# Flowable 双模交付清单

更新：2026-09-21。状态：阶段一及阶段二 2A 验收通过，正在推进 2B/2C 可靠协作；完整任务尚未完成。

开发进度粗估：整体约 **31%**，按四个阶段等权估算，阶段一 100%、阶段二约 25%、阶段三/四 0%。百分比表示需求完成度估计，不是测试通过率或生产可用性；待评审及待运行验收的代码不计为完成。阶段二 2A 请求/任务幂等已通过最终双模 CI、HTTP 与浏览器恢复验证；Outbox、Inbox 与生命周期事件契约已审查集成，双 Profile 共 110 项实际 MySQL 原语测试通过；可靠领域处理器、共享提交恢复界面和 Rabbit 投递仍在开发，尚未切换运行链路。

完整需求见 [REQUEST.md](REQUEST.md)，架构见 [DESIGN.md](DESIGN.md)。每次续接先核对本文件、Git 工作区及实际测试结果；不能将已编译等同于运行验收。用户明确禁止自动提交、推送和外部部署，禁止改动 `.docs/.chiwen.state.json`。

续接环境核查（2026-09-21）：主仓库的已集成源码与清单保留；先前 `/tmp` 待审副本及运行会话已不存在，不能继续引用其路径作为可复查交付物。Docker 已重新启动，后续候选源码、补丁和日志改存持久隔离目录。Workflow/UPMS/UI 候选和 Rabbit 发送/接收批次正在重建并重新验证；这些批次未计入完成百分比，四阶段目标不变。

## 已核查的基线

| 范围 | 实际证据 | 初始状态 |
| --- | --- | --- |
| 引擎版本 | `bixi-common/bixi-common-bom/pom.xml` 固定 Flowable 7.1.0 | 有依赖，保留版本 |
| single 聚合 | `bixi-single/pom.xml` 仅 Auth/UPMS/Generator/Quartz | 未聚合 Workflow |
| 功能开关 | `WorkflowAutoConfiguration` 仅条件化配置器；Flowable starter 独立自动配置 | 不能证明关闭引擎，需真实上下文回归 |
| 引擎参数 | Workflow application.yml 将参数置于 `spring.flowable` | 命名空间错误；Bixi 默认 schema-update=true |
| 同步契约 | workflow-api 只有 RemoteWorkflowService，没有 api.service 契约 | 待补本地/远程适配 |
| 发起 | `ProcessInstanceServiceImpl.start` 先启动再插扩展记录，无请求标识/唯一约束 | 不具备请求幂等，同步结束时序有缺口 |
| 结束通知 | `ProcessEndListener` 仅更新 wf_process_instance；基础监听器重新抛异常 | 无持久化跨模块通知 |
| 事务 | Service 有 @Transactional；terminate/suspend/activate 捕获异常返回 false | 尚未验证引擎与业务表同事务；存在吞异常风险 |
| 办理资格 | `WfTaskServiceImpl.complete/reject/transfer` 查询 taskId 后直接修改 | 未验证当前用户是否有办理资格 |
| 拒绝 | reject 可直接 moveActivityIdTo 任意指定节点 | 不能视为通用退回；第一阶段限定拒绝结束 |
| 消息基础 | common-mq 只有 Rabbit JSON 转换器 | 无 Outbox、领取/恢复或消费者持久化去重 |
| 测试 | WorkflowServiceTest mock 被测 Service 本身 | 不作为业务正确性证据 |
| 默认验收 | acceptance.mjs 覆盖登录、菜单、demo/task、权限、日志 | 未覆盖 Workflow 或四组开关 |
| 基础设施 | 本机 Java 17、Maven、Docker daemon 可用 | 运行依赖仍需实测 |

租户核查：BaseEntity/SQL 含 tenantId，但当前 BixiUser 身份对象没有租户字段，common-mybatis 没有配置租户拦截器；尚无完整隔离链，不宣称多租户能力，不从客户端变量信任租户。

补充发现：现有 BPMN 把抽象 BaseTaskListener / BaseExecutionListener 写成可实例化监听器；现有前端使用 workflow_* 权限而 Controller 使用 wf_*；主初始化菜单没有工作流条目，菜单位于独立的历史 SQL。wf_process_instance / wf_approval_record 表与 BaseEntity 的 data_status/status 继承字段亦需在真实业务测试中核实。1C–1F 必须处理，不能将现有页面/示例视为可用闭环。

## 分阶段工作

### 阶段一：装配与真实审批

- [x] 读取规范、发布基线、关键实现和验收入口，保存完整需求与设计。
- [x] 1A：条件装配引擎、执行器及业务组件；single 聚合唯一 workflow-biz；真实引擎启停测试。见 [验收记录](EVIDENCE-1A.md)。
- [x] 1B：传输无关契约、cloud Feign / single 本地适配；参数校验、权限、操作日志和启动配置隔离。见 [1B 证据](EVIDENCE-1B.md)。
- [x] 1C：独立请假示例草稿、提交、列表、详情与服务端状态转换；内置 BPMN 重复部署。定向验证见 [1C 记录](EVIDENCE-1C.md)，完整运行验收仍在 1F。
- [x] 1D：待办/已办/通过/拒绝结束/历史/业务回写，服务端办理资格、查询范围、权限、审计；已通过真实引擎及领域测试。
- [x] 1E：共享前端页面、表/索引/菜单、权限与缓存开关；前端构建和16项实际脚本交互回归通过，HTTP验收覆盖同一契约。
- [x] 1F：统一黑盒、四组启停、真实事务/引擎集成与完整后端门禁通过；见 [阶段一报告](EVIDENCE-STAGE1.md)。桌面/手机浏览器补充检查通过。

### 阶段二：幂等与可靠协作

- [ ] 稳定请求标识、内容摘要/冲突、请求与业务轮次区分、唯一约束、稳定结果。
- [x] 任务命令幂等、并发审批、真实事务回滚、同步完成时序（2A；可靠事件事务由 2B/2C 继续验证）。
- [ ] Outbox 事件契约、业务提交可靠命令、事务内写入、重试退避/租约领取/过期恢复。
- [ ] cloud Rabbit 确认/不可路由/消费确认；single 本地持久化投递；共用消费者去重与业务更新事务。
- [ ] 重复/乱序/过期/轮次/版本处理；审批后自动业务任务、关联结果、超时/终止/迟到结果/幂等补偿。
- [ ] 可信操作者上下文传递、失败查询/审计/人工重试/对账。
- [ ] 双模响应丢失、进程重启、发送/消费提交窗口故障注入及阶段报告。

### 阶段三：集群与恢复

- [ ] 双 Workflow 副本、统一数据库、线程/连接池/任务锁/领取/过期恢复/优雅停机。
- [ ] BPMN 异步边界、节点执行代码一致、锁过期下副作用去重；single 定时/异步恢复。
- [ ] 受控引擎和扩展表迁移、部署去重、新旧定义并存及显式迁移。
- [ ] 积压/最老等待/重试/死信/失败诊断、关联查询和管理页。
- [ ] 可重复故障脚本、实测恢复时间、性能条件与结果；明确未验证数据库高可用。

### 阶段四：审批扩展

- [ ] 候选人/候选组与组织角色。
- [ ] 转办/委派及委派解决闭环。
- [ ] 退回修改/重新提交/撤回，明确并行和多实例语义。
- [ ] 会签/或签/完成条件。
- [ ] 超时提醒/催办/通知幂等。
- [ ] 表单版本绑定/服务端校验/节点字段权限。
- [ ] 流程轨迹/异常任务/事件投递管理页。
- [ ] 复用已有前端基础完善 BPMN 设计器。

## 验收矩阵

| 组合 | 核心验收 | 审批闭环/权限/日志 | 可靠协作/重启 | 集群/版本 | 当前证据 |
| --- | --- | --- | --- | --- | --- |
| A single enabled | 必须 | 必须 | 无 Workflow 新增 MQ/Nacos/Gateway 依赖 | 定时与重启恢复 | 无 RabbitMQ 实际审批、权限、历史与回写通过；完整后端147项、MySQL58项通过；可靠恢复未实现 |
| B single disabled | 核心运行通过 | 无引擎/执行器组件测试通过，新库禁用不建 ACT，已有数据保留 | 持久化事件尚未实现 | 重新启用恢复待实现 | 本轮 make verify-single exit 0，菜单隐藏/接口404、已有数据保留；可靠事件恢复未实现 |
| C cloud enabled | 必须 | 正常网关及服务链 | Rabbit 真传输及故障注入 | 至少两个副本故障/新旧定义 | 完整后端149项、MySQL58项通过；实际网关/Feign审批与回写通过；可靠性/集群未实现 |
| D cloud disabled | 核心运行通过 | 当前默认编排无 Workflow 服务/路由，禁用组件及 Feign 测试通过 | 持久化事件尚未实现 | 核心回归通过 | 本轮 make verify-cloud exit 0，菜单隐藏/接口404、独立Workflow停止、数据保留；可靠事件未实现 |

共同门禁：`make architecture-check`、`make runtime-config-check`、`make backend-cloud-ci`、`make backend-single-ci`、`make frontend-ci`、`make verify-cloud`、`make verify-single`、`codegraph sync .`、`git diff --check`。新增用例必须复用同一业务断言。窄范围测试只证明其覆盖部分。

## 续接位置与证据

当前 1A–1D 的装配、审批和独立请假业务均已实现并完成定向测试，记录见 [1B](EVIDENCE-1B.md)、[1C](EVIDENCE-1C.md) 和 [请假说明](LEAVE_STAGE1.md)。共享前端、菜单开关、cloud Workflow 服务与优先网关路由已实现，前端及静态门禁通过。新增业务轮次存入扩展表，关闭引擎历史仍可回写；回调有独立事务和审计。

阶段一四组实际运行现已通过，完整记录见 [EVIDENCE-STAGE1.md](EVIDENCE-STAGE1.md)。临时本地项目/环境保持用于后续阶段；共享前端桌面/手机浏览器检查已通过。最新后端 cloud149项/single147项、前端构建、16项UI回归通过。新计划 [PLAN-STAGE2.md](PLAN-STAGE2.md) 已解决公共业务关联被伪造抢占的过渡风险：2A只保证请求幂等，2C切换可信持久化提交后才添加业务轮次唯一约束。

阶段二 **2A 已完成**，见 [2A 验收记录](EVIDENCE-2A.md)。最终固定源码的后端 cloud 304 项、single 302 项通过；实际双模 HTTP 和浏览器服务器成功后丢响应、刷新原样重试/查询、失去认证后重新登录恢复均通过。关闭模式核心验收通过，103 条工作流状态指纹前后一致。共享 UI 44 项及 ESLint/构建通过。

下一步是阶段二 2B/2C 可靠提交与回写切换。Outbox/Inbox 基础已集成并在主工作区通过两 Profile 各 55 项真实 MySQL 测试，事件契约通过独立审查及集成验证，见 [2B 证据](EVIDENCE-2B.md)。Rabbit sender 在隔离副本通过 18 项实际传输及协议测试，待独立复审；两侧领域处理器及共享前端正在准备，均未激活可靠运行链。后续自动任务/补偿、恢复管理、阶段三/四继续保留，不能以 2A 完成结束完整任务。普通选择已获授权，无需重复请求许可；不提交/推送、不编辑用户状态文件。
