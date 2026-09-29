# 4F 表单、BPMN 与双模复核证据

日期：2026-09-25

## 结论

当前 Flowable 实现符合路线图第二阶段 B 的主要架构和运行约束：cloud/single 共用 `bixi-workflow-biz` 业务实现；cloud 使用独立 Workflow 服务与 Rabbit 可靠传输，single 在同一进程使用本地可靠传输；稳定命令、Outbox/Inbox、自动任务、补偿、表单版本冻结、字段权限和 BPMN 上传均进入真实运行链路。

本轮没有把 Flowable 当作不可修改基线。审查发现标准 single 的通知仍经 RabbitMQ，导致 `single + enabled` 实际存在 Rabbit 依赖；现已改为部署模式选择的 `NoticeDelivery`，single 本地刷新 SSE，cloud 保持 Rabbit 广播，并从 single 的自动配置、Compose 依赖、镜像准备、端口检查和启动服务中移除 Rabbit。

进一步复核表单版本生命周期时发现，表单删除原先直接继承 MyBatis-Plus 逻辑删除：即使流程定义或已有实例仍引用该表单，也会令冻结版本后续无法渲染。现已在单个和批量删除事务中锁定表单、检查定义及实例引用并整体拒绝；BPMN 部署绑定表单时取得同一行锁，关闭“部署已读表单、删除先提交、定义后落库”的并发窗口。

这份证据只关闭 Flowable 表单/BPMN 切片和上述双模集成偏差。路线图第二阶段 A（生成器）与 C（通知完整状态机、Quartz、监控交付、AI/RAG）仍需分别审计和验收，不能据此宣布整个第二阶段完成。

后续复核补齐了任务创建通知链路：Flowable 在创建任务的同一事务写 Workflow Outbox，single 经本地可靠传输、cloud 经 Rabbit 投递到 UPMS Inbox，再由 UPMS 校验租户和服务端收件人后持久化 `sys_notice/sys_user_notice`；SSE 只在目标事务提交后刷新。全局 Flowable 监听器保证上传的 BPMN 即使没有内嵌监听器也会发送，瞬态标记避免全局与内嵌监听器重复记录。协议 schema v2 同时携带明确审批人、候选用户和候选角色，UPMS 将角色成员解析为具体用户、过滤停用/锁定/跨租户成员并去重；schema v1 仍可解码和消费，保证已持久化消息可继续处理。BPMN 最多声明 128 个候选身份，候选角色最多解析 1000 个唯一成员，过宽候选池永久拒绝。短信、邮件、微信和 Webhook 不在这条证据范围内。

本次第二阶段一致性审查又发现 BPMN 上传安全边界有遗漏：用户任务分配字段和连线条件原先可接受 `${dangerousBean.execute()}` 一类可执行 EL。初次修复后继续按攻击面复核，又证明同一方法调用放入多实例 `loopCardinality` 时仍可绕过字段枚举。最终实现对上传 XML 的全部属性和文本节点执行同一白名单，不解析 DTD 或外部实体，并保留模型级脚本、实现类和监听器拒绝作为纵深校验。上传流程只允许简单变量，或简单变量与布尔值、空值、数字、字符串字面量的单次比较；方法调用、属性/索引访问和 `#{...}` 均在部署前拒绝。回归用例保留 `${approverId}`、`${businessTaskSuccess == true}`、`${days <= 3}` 等现有安全表达式。

2026-09-25 的交付一致性复核没有把 Flowable 当作冻结基线。复核并修正了四类用户可见或安全问题：流程定义管理列表继续使用 `workflow_definition_view`，可发起列表改用独立的 `workflow_process_add` 契约且只返回当前租户最新活动定义；发起和审批对话框改为读取冻结表单版本、应用服务端隐藏/只读/可编辑策略并只提交可编辑字段；转办/委派审计名称改为使用 UPMS 返回的可信姓名或账号，不再信任客户端 `transferUserName`；发起请求新增可选 `processDefinitionId`，共享前端固定提交已经渲染表单的定义版本，避免打开对话框后发布新版本导致表单与实际实例错位。旧调用方不传定义 ID 时仍按流程 key 解析最新活动版本，历史无此字段的请求摘要保持不变。

## 契约复核

| 要求 | 当前实现与证据 |
| --- | --- |
| 单一业务实现 | Controller、Service、Mapper、Entity 和前端均共享；single 聚合 `workflow-biz`，cloud 通过独立应用组合 |
| 可信发起 | 公开发起默认白名单为空，禁止请假流程、业务关联和保留身份变量；请假由持久命令和可信事件入口发起 |
| 幂等与可靠协作 | 客户端稳定 `requestId`、内容摘要冲突、命令结果重放、Outbox/Inbox、租约重试、隔离和恢复入口均有持久状态 |
| 表单版本 | 定义绑定已发布表单版本，实例冻结 `formId/formVersionId`；发布 v2 不改变运行中的 v1 实例；被定义或实例引用的表单不可删除，部署与删除使用同一行锁串行化 |
| 服务端校验 | 发布和提交复用 schema 解析器；任务、流程定义和可信角色共同确定隐藏/只读/可编辑字段；发起和任务表单读取入口分别受 `workflow_process_add`、`workflow_task_view` 保护，任务表单还校验办理人/候选人可见性 |
| 发起定义一致性 | 可发起页只查最新活动定义；表单读取和提交均携带同一 `processDefinitionId`，后端校验定义 ID、流程 key、活动状态及租户；旧客户端仍兼容按 key 发起 |
| BPMN 上传 | multipart 最大 1 MiB；部署前校验 XML、可执行流程数量、危险脚本/类实现、表达式白名单、候选身份、表单绑定及任务通知契约可接受的流程 ID；版本由 Flowable 产生 |
| 自动业务任务 | Workflow 产生带 `operationId` 的请求；UPMS `demo_leave_booking` 持久化 BOOKED/CANCELED 状态和补偿墓碑；结果事件回传原租户上下文 |
| 待办通知 | 明确审批人、候选用户和候选角色任务在创建事务写 schema v2 Workflow Outbox；UPMS 校验角色/用户的租户和可用状态，将候选角色解析为去重的具体用户，并在 Inbox 事务写 `sys_notice/sys_user_notice`；schema v1 兼容消费，重放按事件去重，SSE 在提交后携带捕获的租户上下文刷新 |
| 管理与脱敏 | Outbox/Inbox/隔离/对账按 owner 查询；管理 DTO 不返回隔离正文；失败 Inbox 可显式 CAS 重开并重放 |
| single 依赖边界 | 标准编排仅需 MySQL、Redis、single、前端；通知和 Workflow 均不需要 RabbitMQ |
| cloud 信任边界 | 专用 `/bixi-workflow` vhost；UPMS/Workflow 使用不同账号和相反方向的 configure/write/read 正则权限 |

## 测试与门禁

以下命令在 4F 切片首次验收时实际执行并通过：

```text
通知部署模式、单体本地投递和发布安全聚焦测试：20 tests, 0 failures, 0 errors
Flowable 启动幂等、恢复、自动任务、表单生命周期与 BPMN 部署聚焦测试：45 tests, 0 failures, 0 errors
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
make verify-single
make verify-cloud
```

backend cloud 的 28 个 reactor 模块、single 的 22 个 reactor 模块全部成功。前端 ESLint 和 production build 通过；Vite 仅报告上游 `vform3-builds` 使用 `eval` 的既有警告。

表单删除保护和部署并发锁是上述完整门禁之后的窄范围修复；修复后的最终源码已重新通过 45 个 Flowable 聚焦测试、`make architecture-check`、`make runtime-config-check`、`git diff --check` 和 `codegraph sync .`，未重新执行完整 backend/frontend CI 与 cloud/single 容器验收。

待办通知和流程 ID 契约复核另取得以下增量证据：通知 Codec/Publisher/Listener/UPMS Handler/提交后 SSE 覆盖 schema v1/v2、明确审批人、候选用户/角色、租户/状态校验、去重及查询上限；无内嵌监听器的真实 Flowable 创建任务能够捕获候选身份；上传流程 ID 校验拒绝不能进入消息契约的定义。真实 MySQL 8.0.45 的 cloud/single Profile 各 2/2，通过 `Outbox -> LocalDurableTransport -> Inbox -> sys_notice/sys_user_notice -> afterCommit SSE`，覆盖明确审批人、真实 `sys_user_role` 候选角色解析和源端重放去重；这些 MySQL 用例没有跳过。

本次表达式白名单修复后，Flowable 的 cloud/single 依赖反应堆分别通过，`bixi-workflow-biz` 均为 201 tests、0 failures、0 errors、7 skipped；完整 `make backend-cloud-ci` 的 28 个模块和 `make backend-single-ci` 的 22 个模块也全部成功。`make frontend-ci` 在同一工作树的前一轮复核中通过，生成器聚焦套件 23/23 通过。上述结果证明当前代码通过编译和自动化门禁，不代表路线图第二阶段 A/B/C 的全部功能已经完成。

多实例表达式绕过的后续 TDD 证据：新增用例在修复前稳定失败（10 项中仅该用例未抛异常），全 XML 扫描补强后 `WorkflowDefinitionCandidateValidatorTest` 10/10；随后 `make workflow-test` 的 cloud/single 两条 Maven 命令均成功，`make architecture-check`、`make runtime-config-check` 和 `git diff --check` 均通过。此轮未执行完整 backend/frontend CI 或容器运行验收。

2026-09-25 一致性复核的 fresh 证据：API 定义版本字段与真实启动行为均先取得 RED，再修复为 GREEN；Flowable/UPMS 聚焦 Maven 反应堆 18 个模块构建成功，10 个指定测试类合计 98 tests、0 failures、0 errors、0 skipped；`node --test scripts/test-workflow-ui.mjs` 为 50/50；`make workflow-test` 的 cloud/single 两条 Maven 命令在最终源码上均成功，末次 `bixi-workflow-biz` 汇总为 176 tests、0 failures、0 errors、7 skipped；`make frontend-ci`、`make architecture-check`、`make runtime-config-check` 均通过。前端构建仍只有上游 `vform3-builds` 的既有 `eval` 警告。容器运行复验状态仍以下文记录为准，不能用这些聚焦测试和静态门禁替代。

表达式修复后的 `make verify-cloud` 与 `make verify-single` 已发起，但本地 Docker 数据盘耗尽：single 容器状态明确记录 `no space left on device`，cloud 的多个 Temurin 17/aarch64 JVM 同时以原生 `SIGBUS` 退出，Gateway 代理随后返回 502。2026-09-25 收口时再次执行 `docker info`，OrbStack daemon socket 已不存在，当前无法重跑。因此本次未取得新的双模运行验收结果；下文记录仍是修复前同一业务链路的最近一次成功证据，恢复 Docker 环境后需要补跑。

## 真实 single

在启用 Workflow 和本地可靠投递、停止 RabbitMQ 的独立 Compose 项目中，仅运行 `mysql`、`redis`、`single`、`frontend-single`。`/admin/actuator/health` 为 `UP`，统一验收通过：

- demo CRUD、权限与审计；
- `APPROVED`、`REJECTED`、`CANCELED`；
- 稳定重试与内容冲突；
- 办理人和数据权限、审批历史；
- 表单 v1/v2、旧实例冻结 v1、新实例使用 v2。

## 真实 cloud

Gateway、Auth、UPMS、Workflow、Nacos、RabbitMQ 和 cloud 前端健康，统一验收通过与 single 相同的业务断言。Rabbit 运行态额外确认：

```text
bixi.workflow.inbox  quorum durable  messages_ready=0 messages_unacknowledged=0 consumers=1
bixi.upms.inbox      quorum durable  messages_ready=0 messages_unacknowledged=0 consumers=1
```

两个消费者均为 active、手动 ACK。连接分别使用 `bixi-upms` 与 `bixi-workflow` 可靠账号；普通通知使用非管理员 `bixi-app`，管理员凭据未注入业务应用。

## 保留边界

- FAILED Inbox 的成功重放会把业务处理、Inbox 终态和恢复审计放在同一数据库事务；重新开放 FAILED 行本身是前置 CAS。若处理或审计失败，业务结果不提交，消息保留为可恢复状态并记录失败审计。
- 任务创建通知覆盖当时的明确 `assignee`、候选用户和候选角色成员快照；角色成员变更不会回收已持久化通知。认领后的撤销/补发、催办、升级和超时提醒仍需单独定版状态机、去重及撤销语义。
- SSE 是持久通知提交后的在线刷新信号，不替代 `sys_notice/sys_user_notice`、Outbox/Inbox 投递状态或恢复审计。
- 完整应用容器重启恢复时延、结果丢失全矩阵、性能容量和数据库高可用仍按既有 2G/3B 清单保留。
- 本轮验证的是 enabled 双模及已有 disabled 回归，不替代路线图 A/C 的功能与四组综合验收。
