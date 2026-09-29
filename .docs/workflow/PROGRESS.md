# Flowable 双模交付清单

更新：2026-09-28。状态：阶段一、阶段二 2A/2B/2C 核心业务闭环及 Rabbit 传输验证通过；明确审批人、候选用户和候选角色的任务创建通知已完成 schema v1/v2 兼容、Workflow Outbox、single 本地/cloud Rabbit 传输、UPMS Inbox、服务端角色成员解析、站内通知持久化、提交后 SSE 和重放去重；2E 第一切片已完成自动任务事件契约、请假登记/补偿原语、v2 Flowable 路径、双方 handler 定向验证、真实 MySQL/Rabbit 跨 owner 自动任务链路、提交窗口故障注入及旧轮次/未知 schema/同 eventId 不同 payload 矩阵；Workflow/UPMS 两个 owner 的恢复查询、失败重试、隔离消息 replay、脱敏对账 API 和共享管理页已落地，对账报告已补齐 command/business-task/quarantine 关联标识；Inbox 提交前进程重启、登记/补偿竞争、Rabbit ACK 前 SIGKILL/redelivery，以及 single/cloud enabled/disabled 四组隔离运行矩阵已通过，完整结果丢失组合矩阵和生产级恢复目标仍待补；阶段三已完成 Flowable lock-owner/租约配置绑定、运行诊断 API/UI、双副本 Compose、显式异步边界、v2/v3 定义并存、受控迁移，以及真实双副本异步 Job/Timer Job 的 SIGKILL、锁过期接管、节点重启、最终业务收敛和优雅停机后存活副本继续服务；真实 MySQL 存量 v2 实例维护窗口也已通过，阶段三剩余证据项为性能/容量报告和执行中任务的优雅停机 drain 量化；阶段四已完成 4A 候选身份和 4F 表单版本/BPMN 上传切片，4B 及其他审批扩展仍未开始。路线图第二阶段 A/C 仍须独立审计，不能由本文件的 Flowable 证据代替。

历史环境说明（2026-09-26）：`make backend-cloud-ci`（28 个模块）、`make backend-single-ci`（25 个模块）、`make frontend-ci` 和 `make generator-ci` 均实际通过；Generator 93/93、Quartz 34/34、AI 24/24，架构/运行配置检查、`git diff --check` 和 `codegraph sync .` 通过。生成器与 Quartz 迁移脚本已为临时 Docker 调用增加超时和自身容器清理。当时 OrbStack Docker Unix socket 曾因 VM OOM 无响应，因此当日未把运行态结果写成通过；后续 2026-09-27 的隔离运行证据以本文末段和 `runtime-evidence/` 为准。

开发进度粗估：按本清单阶段权重，Flowable 路线图整体约 **80%**，阶段一 100%、阶段二约 99%（真实 MySQL/Rabbit 自动任务链路、broker/consumer 重启、发送成功后 mark-delivered 故障窗口、真实 timer 超时后的迟到结果/补偿收敛、登记/补偿竞争、ACK 前进程崩溃、四组运行矩阵，以及旧轮次/未知 schema/同 eventId 不同 payload 矩阵已通过；完整结果丢失组合矩阵和完整阶段报告仍未完成，见 [EVIDENCE-2G.md](EVIDENCE-2G.md)）、阶段三约 **95%**（已完成 lock-owner/租约配置绑定、Flowable/Outbox/Inbox 运行诊断 API/UI、双副本 Compose、显式异步边界、v2/v3 并存、受控迁移、真实双副本异步 Job/Timer Job 的 SIGKILL、8 秒锁过期接管、节点恢复、最终业务收敛、优雅停机后存活副本继续处理新流程，以及真实 MySQL 存量 v2 实例维护窗口；剩余证据项为性能/容量报告和执行中任务的优雅停机 drain 量化，见 [EVIDENCE-3B.md](EVIDENCE-3B.md)）、阶段四约 **25%**（4A 候选身份和 4F 表单版本/BPMN 上传已完成并通过双模验收，4B 及其余审批扩展尚未完成）。百分比只表示本 Flowable 清单的需求完成度估计，不是整个项目第二阶段 A/B/C 的完成度、测试通过率或生产可用性。

完整需求见 [REQUEST.md](REQUEST.md)，架构见 [DESIGN.md](DESIGN.md)。每次续接先核对本文件、Git 工作区及实际测试结果；不能将已编译等同于运行验收。用户明确禁止自动提交、推送和外部部署，禁止改动 `.docs/.chiwen.state.json`。

续接环境核查（2026-09-22）：主工作树中的已集成源码、清单和保存的证据是当前权威依据；已失效或不存在的 `/tmp` 副本、日志及运行会话不作为可复查证据。四阶段目标不变。

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
| 消息基础 | common-mq 已有 Outbox/Inbox、quarantine、wire codec、Rabbit owner endpoint 和 Local transport | 业务端到端闭环和管理入口仍待验证 |
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

- [x] 稳定请求标识、内容摘要/冲突、请求与业务轮次区分、唯一约束和稳定结果已由 2A 实现并验证；见 [2A 验收记录](EVIDENCE-2A.md)。
- [x] 任务命令幂等、并发审批、真实事务回滚、同步完成时序（2A；可靠事件事务由 2B/2C 继续验证）。
- [x] Outbox 事件契约、业务提交可靠命令、事务内写入、重试退避/租约领取/过期恢复；single/cloud owner 配置和领域处理器已接入，见 [2B 证据](EVIDENCE-2B.md)。
- [x] cloud Rabbit 确认/不可路由/消费确认、quorum 队列、损坏消息隔离、消费者重启和 broker 重启恢复；single 本地 transport 与共用消费者去重原语已通过真实 MySQL/Rabbit 定向测试。真实 MySQL/Rabbit 请假自动任务闭环已通过专用跨 owner 测试。
- [x] 2E 第一切片：四类自动任务/补偿事件固定版本契约、严格 codec round-trip、`demo_leave_booking` 的 BOOKED/CANCELED 幂等状态机、v2 Flowable service/receive/timer/terminate 路径，以及 Workflow/UPMS handler 定向验证；已补真实 H2/MyBatis mapper/并发集成证据和真实 timer 超时/迟到结果幂等证据；见 [2E 证据](EVIDENCE-2E.md)。
- [ ] 重复/乱序/过期/轮次/版本处理的完整真实 MySQL/Rabbit 跨 owner 事件矩阵；当前已补真实旧轮次 `IGNORED`、未知 schema 隔离、同 eventId 不同 payload 冲突隔离及登记/补偿竞争，仍缺结果丢失全矩阵和真实运行态完整恢复报告。
- [x] 可信操作者上下文传递、失败查询/审计/人工重试基础入口（Workflow/UPMS owner 均提供状态查询、FAILED CAS 重试和独立审计；共享页面可切换 owner）。
- [x] 跨表事件/命令对账接口、隔离消息 replay、脱敏恢复结果，以及 command/business-task/quarantine 关联字段和共享 UI；Rabbit 自动任务 fresh 复核报告和 single/cloud 完整应用容器恢复报告均已保存，结果丢失组合矩阵仍待补。
- [x] 明确审批人、候选用户和候选角色的流程待办通知真实写入与可靠投递：schema v2 在任务事务写 Workflow Outbox，single/cloud 分别经本地/Rabbit 传输到 UPMS Inbox；UPMS 校验租户及启用/锁定状态，将角色成员解析为最多 1000 个去重用户，同事务写 `sys_notice/sys_user_notice`，提交后 SSE 刷新，源端重放不会重复通知；schema v1 存量消息仍可消费。真实 MySQL cloud/single Profile 均覆盖候选角色成员链路。
- [x] 通知收件人投递闭环：`sys_user_notice` 持久化状态/attempt/error/时间，5 分钟过期 `IN_FLIGHT` 可 fenced reclaim，管理记录支持筛选和失败重试，single/cloud 共用业务与前端契约；证据见 [通知投递记录](EVIDENCE-NOTICE-DELIVERY.md)。本轮补充了 provider-neutral HTTP Email/Webhook/SMS/WeChat 适配器、收件人地址校验、单渠道状态/失败/重试和真实 JDK HttpServer focused 证据；真实厂商送达/回执、渠道 fan-out、自动退避、SBA 监控和完整容器故障矩阵仍未覆盖。
- [ ] 双模响应丢失、真实超时竞态、剩余发送/消费提交窗口故障注入及阶段报告；当前已有 Rabbit broker/consumer 重启、Outbox mark-delivered 失败后租约重放、Inbox 提交前 JVM 重启、ACK 前 `SIGKILL`/redelivery，以及 single/cloud 完整应用容器重启证据。仍缺结果丢失的完整组合矩阵和生产级恢复目标，见 [2G 证据](EVIDENCE-2G.md)。

### 阶段三：集群与恢复

- [x] 第一切片：两个 Workflow owner 的 lock-owner/租约配置绑定、Flowable/Outbox/Inbox 运行诊断 API/UI 和定向测试；见 [3A 证据](EVIDENCE-3A.md)。
- [x] 双 Workflow 副本 Compose 覆盖配置、统一数据库依赖和显式 `workflow-a/workflow-b` lock-owner；`make workflow-cluster-config` 已通过静态门禁。
- [x] 双 Workflow 副本运行态、线程/连接池/任务锁/领取/过期恢复及优雅停机；真实双副本、异步 Job/Timer Job 领取及过期接管、节点重启、单副本继续发起新流程和停止副本重新加入均已通过。
- [x] BPMN v3 对登记/补偿 service task 设置显式异步边界，保留 v2 Delegate/事件契约；H2/Flowable 4/4 验证旧实例完成、新实例执行持久 job。
- [x] 受控 schema 迁移入口、部署去重、v2/v3 定义并存和显式 v3 部署 API 已落地；空白隔离 MySQL 和带存量 v2 实例的真实维护窗口均已通过。
- [x] 积压/最老等待/重试/死信/失败诊断 API 与管理页第一切片；最新真实 MySQL/Rabbit 双副本故障报告已覆盖本阶段故障场景和最终关联收敛。更广的全事件矩阵及完整恢复报告属于阶段二剩余项。
- [x] 可重复故障脚本已加入，所有杀进程动作限定在脚本创建的临时项目；最新真实双 Workflow 副本异步 Job 在 9406ms、Timer Job 在 9172ms 完成接管，优雅停机、节点恢复、真实 MySQL 存量 v2 实例维护窗口及业务最终收敛通过；最新报告为 `target/workflow-cluster-failover/20260922091956-84802/report.json`，明确不代表数据库高可用。
- [ ] 补充按硬件、并发、延迟、错误率和积压记录的性能/容量证据，并构造执行中业务任务以量化优雅停机 shutdown drain 等待时间。

### 阶段四：审批扩展

- [x] 4A：候选用户/候选角色、可信 UPMS 身份查询、服务端 `claimable`、任务认领授权、BPMN 部署前候选身份校验、租户隔离和共享前端展示；聚焦后端 64/64、Node UI 46/46、ESLint 和前端开发构建通过。未执行完整 cloud/single 运行验收、并发压测、容器故障注入、HA 或浸泡测试。
- [ ] 转办/委派及委派解决闭环。
- [ ] 退回修改/重新提交/撤回，明确并行和多实例语义。
- [ ] 会签/或签/完成条件。
- [ ] 超时提醒/催办/通知幂等。
- [x] 表单版本绑定、实例版本冻结、引用表单删除保护及部署/删除并发串行化、服务端 schema/节点字段权限校验，以及经校验的 BPMN 上传和不可变定义元数据；共享 cloud/single 黑盒验证通过，见 [4F 证据](EVIDENCE-4F.md)。
- [ ] 流程轨迹/异常任务/事件投递管理页。
- [ ] 复用已有前端基础完善 BPMN 设计器。

## 验收矩阵

| 组合 | 核心验收 | 审批闭环/权限/日志 | 可靠协作/重启 | 集群/版本 | 当前证据 |
| --- | --- | --- | --- | --- | --- |
| A single enabled | 必须 | 必须 | 无 RabbitMQ/Nacos/Gateway/独立 Workflow 依赖 | 定时与重启恢复 | `single-enabled-20260927.json` 证明本地可靠链路、审批通知和状态回写；`full-application-restart-20260927t01091790471346z-91250/evidence.json` 证明 4 个服务完整停止后恢复，`recovery_ms=48513`；不覆盖 Rabbit 或压力场景 |
| B single disabled | 核心运行通过 | 无引擎/执行器组件测试通过，新库禁用不建 ACT，已有数据保留 | 关闭期间不消费 | 重新启用恢复 | `single-disabled-reenable-20260927/evidence.json` 证明关闭 54 秒内无消费，重新启用后 Outbox/Inbox 各一次并恢复为 `IN_REVIEW/STARTED`；不覆盖高并发 |
| C cloud enabled | 必须 | 正常网关及服务链 | Rabbit 真传输、真实 MySQL/Rabbit 请假自动任务提交/回写通过 | 至少两个副本故障/新旧定义 | `cloud-enabled-acceptance-20260927.json` 和 `cloud-full-application-restart-20260927.json` 通过；13 个服务完整停止/分阶段恢复，`recovery_ms=166814`，Nacos 注册断言通过；不覆盖第三方回执和数据库 HA |
| D cloud disabled | 核心运行通过 | 当前默认编排无 Workflow 服务/路由，禁用组件及 Feign 测试通过 | 关闭期间不消费 | 核心回归与重新启用恢复 | `cloud-disabled-reenable-20260927/evidence.json` 证明禁用 37 秒内 Outbox 保持 `PENDING`、无 Workflow consumer，重新启用后 36 秒内一次性恢复；需保留维护窗口 schema bootstrap 约束 |

共同门禁：`make architecture-check`、`make runtime-config-check`、`make backend-cloud-ci`、`make backend-single-ci`、`make frontend-ci`、`make verify-cloud`、`make verify-single`、`codegraph sync .`、`git diff --check`。新增用例必须复用同一业务断言。窄范围测试只证明其覆盖部分。

执行策略（个人电脑适配）：先执行静态/聚焦层，再一次只运行一个 Compose 模式；不要并行启动 cloud、single、AI 或多副本项目。当前实测 Docker VM 为约 7.82 GiB/10 CPU，宿主数据盘约 39 GiB 可用且已用 92%，因此默认 `make start-cloud`、`make start-single` 使用前应先清理无关卷和构建缓存，并保留至少 10 GiB 可用空间。

- Tier 1（个人电脑默认）：`make architecture-check`、`make runtime-config-check`、`make workflow-process-restart-static-test`、`make full-application-restart-static-test`、聚焦 Maven/Node 测试和 `bash -n`；不需要完整应用栈。
- Tier 2（个人电脑可选）：单独执行 `make start-single && make verify-single` 或 `make start-cloud && make verify-cloud`，一次只保留一个模式，默认关闭 AI 和 Workflow 集群；重启演练使用预构建镜像和低堆配置，记录为本机观测值而非 SLO。
- Tier 3（CI/专用机器）：四组矩阵重复跑、cloud 与 single 并行、Workflow/SBA 多副本故障注入、压力/容量/浸泡、数据库 HA/跨地域、第三方 AI/通知凭证送达。这些场景不应作为普通个人电脑的默认测试步骤。

完整 cloud/single CI 可以在本机按顺序执行，但应视为资源密集型门禁；若 Docker daemon、磁盘或 JVM 出现 OOM/`no space left on device`，必须记录为环境阻塞并停止追加栈，不得把未执行验收写成通过。

## 续接位置与证据

当前 1A–1D 的装配、审批和独立请假业务均已实现并完成定向测试，记录见 [1B](EVIDENCE-1B.md)、[1C](EVIDENCE-1C.md) 和 [请假说明](LEAVE_STAGE1.md)。共享前端、菜单开关、cloud Workflow 服务与优先网关路由已实现，前端及静态门禁通过。新增业务轮次存入扩展表，关闭引擎历史仍可回写；回调有独立事务和审计。

阶段一四组实际运行现已通过，完整记录见 [EVIDENCE-STAGE1.md](EVIDENCE-STAGE1.md)。临时本地项目/环境保持用于后续阶段；共享前端桌面/手机浏览器检查已通过。最新后端 cloud149项/single147项、前端构建、16项UI回归通过。新计划 [PLAN-STAGE2.md](PLAN-STAGE2.md) 已解决公共业务关联被伪造抢占的过渡风险：2A只保证请求幂等，2C切换可信持久化提交后才添加业务轮次唯一约束。

阶段二 **2A 已完成**，见 [2A 验收记录](EVIDENCE-2A.md)。最终固定源码的后端 cloud 304 项、single 302 项通过；实际双模 HTTP 和浏览器服务器成功后丢响应、刷新原样重试/查询、失去认证后重新登录恢复均通过。关闭模式核心验收通过，103 条工作流状态指纹前后一致。共享 UI 44 项及 ESLint/构建通过。

当前阶段二 2C 的可靠提交与回写已在 single/cloud 真实运行态收口。真实 MySQL 工作流 192/192；2026-09-24 使用 MySQL 8.0.45、`REPEATABLE-READ` 重跑可靠消息测试，cloud/single 各 63/63，两次 `InboxExecutorTest` 均为 20/20 且无跳过；`make reliable-rabbit-test` 的 Rabbit owner/listener 6/6 及跨 JVM broker 重启 seed/consume 2/2 通过；统一 acceptance 已验证 `APPROVED/REJECTED/CANCELED`、幂等/冲突、审批人和数据权限。二阶段一致性复核后的 Flowable 聚焦用例 45/45，覆盖公共入口可信启动边界、稳定 `requestId`、相同命令优先重放、不同命令的业务轮次冲突、owner 隔离、永久失败 replay、非默认租户传播、恢复审计原子性、`wf_business_task` 持久状态机、真实 timer、引用表单删除保护及 BPMN 部署/删除并发串行化；业务轮次唯一索引的真实 MySQL 迁移套件 15/15。新增 2F 定向验证：恢复元数据/Workflow/UPMS 三个聚焦测试类共 15/15，UPMS 审计 H2 实际落库 1/1；共享恢复页已支持 Workflow/UPMS owner 切换，并展示 command/business-task/quarantine 六个脱敏关联字段；Workflow/UPMS 对账、隔离区 replay 和独立审计接口已落地。真实 MySQL/Rabbit 自动任务测试 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest` 已扩展为 5/5，确认两 owner 的 Outbox/Inbox 状态、booking 幂等、真实 timer 超时后的迟到登记结果忽略与补偿收敛、登记/补偿竞争，并覆盖旧轮次 `IGNORED`、未知 schema 隔离、同 eventId 不同 payload 冲突隔离；`WorkflowLeaveBusinessTaskIntegrationTest` 4/4 验证真实 Flowable timer、补偿等待、迟到结果和 v2/v3 并存。任务通知增量验证覆盖固定 schema v1/v2 Codec、Outbox Publisher、全局/内嵌监听器去重、明确审批人与候选身份捕获、UPMS 租户/启用/锁定校验、候选角色成员去重和有界解析、目标事务提交后 SSE，以及真实 MySQL cloud/single Profile 的明确审批人、`sys_user_role` 角色成员和源端重放去重；BPMN 上传同时拒绝不能进入通知事件契约的流程 ID、停用/锁定候选用户及超过 128 个候选身份。`scripts/test-workflow-process-restart.sh` 的 Inbox 提交前杀 JVM 恢复通过，Rabbit ACK 前杀 consumer JVM 后 redelivery 去重也已通过；single/cloud enabled 完整应用重启和 single/cloud disabled 关闭期间不消费、重新启用恢复证据已保存。阶段三双副本故障接管和真实 MySQL 存量 v2 实例维护窗口已经通过；阶段二仍缺结果丢失组合矩阵和生产级恢复目标；4A 候选身份切片已完成，4B 及后续审批扩展未开始。不能以当前百分比宣称完整路线图完成。

2026-09-27 Rabbit 复跑更正：早期报告的 `4/5` 失败来自测试夹具缺少 `wf_process_definition`，`prepareStart` 查询缺表触发 `BadSqlGrammarException`；补齐 `WorkflowTestSchema.create(...)` 后真实脚本退出码 `0`，Workflow/UPMS 自动任务为 `5/5`，common MQ owner/listener 为 `6/6`，broker restart 为 `2/2`，ACK crash seed/hold/resume 为 `1/1`。可复查报告见 `bixi-module/bixi-workflow-biz/target/surefire-reports/` 与 `bixi-common/bixi-common-mq/target/surefire-reports/` 下对应 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest`、`RabbitOwnerEndpointIntegrationTest`、`RabbitBrokerRestartIntegrationTest` 和 `RabbitAckCrashIntegrationTest` 文件；完整应用容器重启、结果丢失全矩阵和恢复时延仍未完成。当前阶段二 A/B/C 工程实现与自动化验证约 **90%**，完整退出条件约 **85%**。

本次安全复核补齐 BPMN 上传表达式白名单：上传 XML 的全部属性和文本节点仅允许简单变量或与基础字面量的单次比较，方法调用、属性/索引访问及 `#{...}` 在部署前拒绝；除用户任务分配字段和连线条件外，后续 TDD 复核还证明多实例 `loopCardinality` 原先可绕过字段枚举，现由不解析 DTD/外部实体的流式 XML 扫描统一覆盖。三类原先可通过的可执行 EL 均有 RED/GREEN 回归证据。

本轮收口复验：可靠 cloud 使用 `BIXI_HTTP_PORT=28380 WORKFLOW_ENABLED=true BIXI_RELIABLE_ENABLED=true make verify-cloud` 通过；可靠 single 使用 `BIXI_HTTP_PORT=28380 WORKFLOW_ENABLED=true BIXI_RELIABLE_ENABLED=true BIXI_RELIABLE_RABBIT_ENABLED=false make verify-single` 通过。两种模式使用同一黑盒断言，均覆盖 `APPROVED/REJECTED/CANCELED`、稳定重试/冲突、办理人与数据权限、历史，以及表单 v1/v2 和旧实例版本冻结。single 运行时未启动 RabbitMQ；cloud 的两个 quorum inbox 各有一个手动 ACK 活跃消费者、无积压，UPMS/Workflow 使用独立账号和方向权限。完整记录见 [4F 证据](EVIDENCE-4F.md)。

当前工作树的一致性复核中，Flowable cloud/single 依赖反应堆均通过（`bixi-workflow-biz` 各 201 tests、0 failures、0 errors、7 skipped），`make frontend-ci` 通过；此前阻塞全仓 cloud CI 的第二阶段 A 生成器默认模板问题已修复，生成器聚焦套件 23/23 通过。表达式白名单修复后，完整 `make backend-cloud-ci` 的 28 个模块和 `make backend-single-ci` 的 22 个模块均全部成功；这仍只证明当前代码通过自动化门禁，不能宣称整个第二阶段的功能均已完成。

2026-09-25 没有将 Flowable 冻结为不可修改基线。第二阶段一致性复核修正了定义管理与可发起权限混用、发起/审批页未接入冻结表单、转办/委派审计信任客户端姓名，以及表单按定义 ID 渲染但提交按 key 重新选择最新版本四类问题。当前共享前端会把已渲染的 `processDefinitionId` 固定到持久重试载荷，后端按定义 ID、流程 key、活动状态和租户共同校验；旧请求未携带定义 ID 时仍兼容原有摘要和 latest 语义。fresh 聚焦反应堆 98/98、Node UI 50/50、`make workflow-test` 的 cloud/single 两条 Maven 命令均成功（末次 `bixi-workflow-biz` 为 176 tests、0 failures、0 errors、7 skipped），`make frontend-ci`、`make architecture-check`、`make runtime-config-check` 通过。完整应用容器重启、结果丢失全矩阵和恢复时延仍未完成，因此不能宣称 Flowable 或第二阶段整体 100% 完成。

全 XML 表达式补强后的 fresh 验证：`WorkflowDefinitionCandidateValidatorTest` 10/10，`make workflow-test` 的 cloud/single 两条 Maven 命令均成功，`make architecture-check`、`make runtime-config-check` 和 `git diff --check` 均通过。容器运行复验状态仍以下一段为准，不能用 H2/构建门禁替代。

表达式修复后的双模运行复验已发起但未完成：本地 Docker 数据盘耗尽，single 容器报 `no space left on device`，cloud 多个 Temurin 17/aarch64 JVM 同时原生 `SIGBUS` 退出，两个入口均返回 502；收口时 `docker info` 又因 OrbStack daemon socket 不存在而失败。该结果属于运行环境阻塞，不能记为业务验收失败或通过；恢复 Docker/Compose 后仍需重跑 `make verify-cloud` 和 `make verify-single`。

2026-09-26 一致性续查修正了两项契约偏差：角色候选人在认领前可读任务/流程详情，纯角色候选人认领后可取消认领，权限判定统一复用 `WorkflowCandidateResolver`；业务实例唯一性改为 `(business_owner, business_table, business_id, business_round)`，可信启动将 `sourceOwner` 同时写入 Flowable 变量和 `wf_process_instance`，迁移仅自动回填可识别的请假历史数据并对未知/残缺关联失败停止。fresh Workflow 反应堆共 211 tests、0 failures、0 errors、7 skipped，候选权限 8/8、启动幂等 28/28、`make workflow-cluster-static-test` 56/56 均通过。由于 Docker/OrbStack daemon 仍不可用，`20260924_workflow_business_occurrence.sql` 的真实 MySQL 执行尚未验证；MyBatis 租户拦截器对 owner 查询是否隐式增加 tenant 条件也仍需与数据库全局 owner 唯一键的语义保持一致。

2026-09-26 C/AI 收口增量：`EVIDENCE-C-BLACKBOX.md` 记录了 Quartz HTTP CRUD/暂停恢复/立即执行/运行中修改拒绝、HTTP 500 与 JAR 非零失败、通知发布/已读/SSE 首次连接与重连、FAILED 投递人工重试，以及 AI disabled 菜单/接口/secret marker 检查。通知渠道 focused 结果为 Dispatcher 12/12（含真实本地 HTTP Server 的 Email/Webhook 202/204 与 503 失败）、PublishedNoticeNotifier 2/2、NoticePublicationIntegration 18/18、NoticeLocalDeliveryIntegration 5/5；SMS/WeChat 未配置时返回可审计失败。AI schema contract 2/2、AI focused 32/32，通过并补齐 `BaseEntity` 字段及 RAG `embedding/chunk_content`/索引的初始化 SQL 与幂等迁移；Single 与 Cloud MySQL 8.4.3 临时旧结构迁移双次执行均通过。截至当日，DashScope/provider、AI enabled HTTP、运行态跨租户召回尚未验证；后续 Cloud deterministic provider HTTP 与 AI 专用租户隔离结果见本文件 2026-09-27 收口段。此前 Cloud 启动曾受 Docker overlay 空间/VM 资源限制；本轮恢复后，`bixi-phase2-cloud` enabled 栈已完成 Flowable 受控迁移并通过真实统一 acceptance，记录见 [EVIDENCE-C-BLACKBOX.md](EVIDENCE-C-BLACKBOX.md)。完整应用容器重启、Cloud Quartz（按架构无独立服务/网关路由，因此 HTTP 黑盒不适用）、真实 DashScope/provider 和剩余故障矩阵仍未验证；AI 运行态跨租户召回已由 2026-09-27 专用黑盒补齐。Cloud `IN_APP`/SSE/retry 专用黑盒已通过，脱敏输出见 [EVIDENCE-CLOUD-NOTICE-SSE.json](EVIDENCE-CLOUD-NOTICE-SSE.json)。当前口径为：第二阶段 A/B/C 的工程实现与自动化验证约 90%，完整退出条件约 85%；不能写成 100%。

同日最终门禁复核：`make backend-cloud-ci`（28 模块）和 `make backend-single-ci`（25 模块）均 `BUILD SUCCESS`，`make frontend-ci`、`make generator-ci`、`make architecture-check`、`make runtime-config-check`、`codegraph sync .` 和 `git diff --check` 均通过。Single 统一 acceptance 使用临时前端端口 28181、后端 29992 fresh 通过，覆盖登录、权限、菜单、示例 CRUD、参数校验、审计，以及 Workflow disabled 菜单隐藏和接口缺失；随后 `bixi-phase2-cloud` enabled acceptance 在端口 28080/29997 上 fresh 通过，包含 Workflow 三种终态、重试/冲突、权限、历史及表单版本冻结。`make verify-cloud` 本身未在本轮重新调用，完整应用容器重启与剩余故障矩阵仍待补。

Cloud enabled 运行记录补充：`target/phase2-runtime/cloud-enabled.env` 对应的 `bixi-phase2-cloud` 数据库受控迁移 ledger 为 `23` 条，`ACT_*` 表为 `30` 张；Workflow、Gateway、Auth、UPMS、Generator、Monitor、Nacos、MySQL、Redis、RabbitMQ 和前端均观察为 healthy（配置初始化容器按预期退出码 0）。统一验收命令为 `BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud-enabled.env node scripts/acceptance.mjs`，退出码 0。Generator 同环境验收也退出码 0，产物目录为 `target/generator-acceptance/cloud-StNHXL`。这些是一次真实 Cloud enabled 运行证据，不替代完整应用容器重启、真实 DashScope/provider 行为、Cloud Quartz 架构边界以外的故障矩阵和剩余故障矩阵。

Cloud 通知黑盒补充（2026-09-26）：`BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud-enabled.env node scripts/cloud-notice-sse-blackbox.mjs` 退出码 `0`。真实管理员登录、`/admin/user-notice/stream` 的 `200 text/event-stream` 与 `open/ok` 首帧、`IN_APP` 发布后的 `DELIVERED`、SSE `noticeId/userNoticeId` 对应、已读后重发、`delivery/retry` 幂等和临时数据清理均通过。运行观察中 UPMS 曾出现 `restart count=4`，随后恢复为 `running/healthy`；其启动约 90 秒而健康检查 start period 约 40 秒，仍需把运行稳定性和完整容器重启恢复列为未闭环风险。Quartz Cloud HTTP 端点按架构不适用，Quartz REST 证据来自 Single。

2026-09-27 收口增量：Cloud enabled 栈以 `bixi-phase2-cloud` 为对象，先运行统一 acceptance 基线（23 秒，退出码 0），再受控重启 Workflow 与 UPMS，观察 Gateway、Workflow、UPMS 均恢复 `healthy` 的时间为 42 秒，重启后统一 acceptance 39 秒、退出码 0；结构化记录见 [EVIDENCE-CLOUD-RESTART-20260927.json](EVIDENCE-CLOUD-RESTART-20260927.json)。该证据覆盖应用副本重启后的真实审批、权限、历史、表单冻结和审计回归，但不把所有依赖同时重启或结果丢失故障注入写成已覆盖。

同日补齐受控迁移入口并完成真实空库实测：`make phase2-migration-list` 可确定性列出 35 个按文件名排序的 Phase 2 增量脚本，`make phase2-schema-migrate` 在独立 MySQL 8.4.3 Compose 项目中首轮全部执行成功，第二轮幂等跳过且 `bixi_schema_migration` ledger 为 35 条；原 `make workflow-schema-migrate` 保留 Workflow-only 语义。对应静态/运行契约测试 `scripts/test-phase2-migration-runner.test.mjs` 已通过。详细记录见 [Phase 2 迁移证据](EVIDENCE-PHASE2-MIGRATION-20260927.md)。

2026-09-27 Cloud AI deterministic provider 收口：在 `bixi-phase2-cloud` enabled 栈上，
管理员登录、AI 菜单/权限、`/ai/config`、文档新增/列表、语义搜索、RAG、同步聊天、
SSE 流式聊天、文档删除及删除后空搜索均已通过真实 HTTP。前端代理
`http://127.0.0.1:28080/api` 的脱敏探测确认 config/document/search/RAG/delete 均为
HTTP 200/code 0，`searchCount=1`、`ragSourceCount=1`；Gateway 直接探测补齐 chat/SSE，
并观察到 `LOCAL-DETERMINISTIC-ANSWER`。通过态摘要为
[Cloud AI HTTP 证据](EVIDENCE-CLOUD-AI-HTTP.json)（本地复核副本为
`target/phase2-runtime/ai-cloud-enabled-http-passed.json`）。同时修复并验证了 AI profile
覆盖基础 Gateway 路由以及全局首段剥离导致的下游路径问题：AI profile 保留 Auth/UPMS/
Generator 路由，并增加 `PrefixPath=/ai`；`sh scripts/ai-cloud-runtime-config.test.sh`
通过。旧的 `ai-cloud-enabled-http.json`（RAG source count 为 0）和
`ai-cloud-ownership-http.json`（fixture/login 失败）仅保留历史，不作为通过证据。真实
DashScope/provider 仍未验证；AI 专用 Cloud 跨租户黑盒已在
`EVIDENCE-CLOUD-AI-TENANT-ISOLATION-20260927.json` 通过，覆盖同名用户按租户登录、文档归属与列表隔离、向量搜索、RAG source、伪造 `X-Tenant-Id`、跨租户删除拒绝以及删除后的不可召回。通用 Cloud 租户矩阵和 Auth→UPMS Feign `X-Tenant-Id` 传播仍各自保留为独立证据，不能替代这些 AI 专用断言。该增量补强了 2C 证据，但不改变当前保守口径：第二阶段
A/B/C 工程实现与自动化验证约 **90%**，完整退出条件约 **85%**。

2026-09-27 最新双模门禁复核：在 Rabbit 权限初始化完成、Workflow 与 UPMS 重新声明
两个 quorum inbox 后，使用显式隔离 env 执行 `make verify-cloud` 退出码 `0`；结果
包含登录、示例 CRUD/审计、Workflow `APPROVED/REJECTED/CANCELED`、稳定重试与冲突、
办理人与数据权限、历史、表单 v1/v2 冻结。同期 `verify-single` 只完成健康探针，复用
旧 single 镜像的登录阶段返回 `401`，因此本轮不能把 single 运行态验收记为通过；临时
single 容器已清理，待使用与当前数据库/密钥一致的 fresh 镜像重跑。该结果不改变保守
口径：第二阶段 A/B/C 工程实现与自动化验证约 **90%**，完整退出条件约 **85%**。

2026-09-27 Quartz 运行态补证：在 fresh Single 容器中，真实 `/admin/sys-job/run-job/{id}` 的两个并发请求
均返回 HTTP 200/code 0；修复了 Quartz JDBC 重复键被包装为 `JobPersistenceException` 时的竞态，并增加了
事务提交可见性短轮询。慢 REST 任务的两次手动触发由 fixture 观测到 `maxActive=1`；HTTP 500 任务记录了
3 次有界重试、同一 `executionId`、`maxAttempts=3` 和异常信息。证据为
`target/phase2-runtime/quartz-runtime-20260927.json`，聚焦 `TaskUtilSchedulingSemanticsTest` 为 7/7。
该补证不覆盖 Quartz 节点接管、第三方通知回执、真实 DashScope/provider 或完整故障矩阵。

此前收口口径（在下方 2026-09-27 故障矩阵、生成器和 provider 复核之前）为第二阶段严格退出约
**92%**（A 生成器 **98%**、B 工作流/可靠消息 **93%**、C 通知/Quartz/AI **87%**），工程实现与自动化
验证约 **94%**；该数字由下方最新证据覆盖。

2026-09-27 SBA 运行态补证：`bixi-phase2-cloud` 中临时扩展两个 `bixi-upms-biz` 副本，
Spring Boot Admin `/instances` 黑盒确认两个不同 `serviceUrl` 均为 `UP`，并核对管理与
health endpoint；证据为 `target/phase2-runtime/sba-multi-instance-20260927.json`，
可复查脚本为 `scripts/sba-multi-instance-acceptance.mjs`，静态契约
`make sba-multi-instance-static-test` 通过。该切片闭合 SBA 多实例注册/状态观测，
不覆盖节点故障接管或完整故障矩阵；总体严格退出百分比待其余运行缺口复验后再更新。

2026-09-27 最新故障矩阵与进程恢复补证（历史窗口）：真实 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest`
现为 5/5，新增 UPMS 业务结果已经落库但 Outbox `markDelivered` 更新失败时的
`IN_FLIGHT`、租约过期、同 `eventId` 重放及最终单次 APPROVED/BOOKED 收敛；
`scripts/test-reliable-rabbit.sh` 的 Rabbit owner/listener、broker restart、ACK 前 JVM
崩溃分别为 6/6、2/2、1/1，`scripts/test-workflow-process-restart.sh` 的 Workflow
owner 提交窗口恢复为 1/1（观测约 2.8--4.4 秒，脚本墙钟值，不是 SLO）。重复、乱序、
旧轮次、未知 schema/冲突载荷和过期 lease 均有真实或聚焦证据；当时的完整应用依赖同时重启、
四组 enabled/disabled 矩阵和组合恢复时延尚未覆盖。后续 2026-09-28 运行记录已补齐其中的
四组隔离矩阵和完整应用重启证据，详见 [2G 证据](EVIDENCE-2G.md)
及 `bixi-module/bixi-workflow-biz/target/surefire-reports/`、
`bixi-common/bixi-common-mq/target/surefire-reports/`。

同日补齐生成器和 AI provider 协议证据：Single 生成器父子表真实运行验收 12/12
通过（主子表创建/详情、替换更新、非法关系键、失败回滚、级联清理、匿名拒绝和三类审计），
记录为 `target/phase2-runtime/EVIDENCE-SINGLE-GENERATOR-PARENT-CHILD-20260927.json`；
`node scripts/test-dashscope-provider.mjs` 通过 health、同步 chat、`X-DashScope-SSE`
分帧和 embedding endpoint，记录为 `target/phase2-runtime/EVIDENCE-C-AI-PROVIDER-20260927.json`。
两项分别补齐本地 Single 生成闭环和 provider 协议形状，但不等同于真实第三方 DashScope
密钥送达或生产厂商回执。

按当前全部证据的保守口径，第二阶段严格退出进度约 **94%**，工程实现与自动化验证约
**96%**。剩余未闭合项为真实 DashScope 密钥链、AI 模型配置的重启/多实例持久化、第三方
通知厂商送达/回执、Quartz/SBA 节点故障接管，以及结果丢失组合矩阵和生产级恢复目标；
这些未完成项不计入上述百分比的已交付部分。

第二阶段分域估算（以可验收项和证据为口径）：

| 分域 | 进度 | 未闭合重点 |
| --- | ---: | --- |
| A 生成器 | **约 98%** | 生成 API 的 Cloud Remote/Single local adapter 范围与最终证据仍需定稿 |
| B 工作流与可靠消息 | **约 95%** | 结果丢失组合矩阵、生产级恢复目标 |
| C 通知、Quartz、AI/RAG | **约 88–92%** | 真实 DashScope 密钥链、第三方回执、Quartz/SBA 节点接管 |

分域数字是审计估算，不能与严格退出 94% 或工程验证 96% 相加，也不能替代未验证项清单。

2026-09-28 个人电脑 Tier 1 增量收口：AI 同步/流式对话共用的会话持久化现在检查实际写入
结果；数据库异常或零行写入会返回 `AiException`，不再把未保存的回答报告为成功。新增
失败路径先红后绿，`ChatServiceImplTest` 为 14/14（含流式完成后的持久化失败和 RAG
检索失败），`MessageServiceImplTest` 为 2/2。Quartz REST 调用新增有限连接/读取
超时、禁止重定向、HTTP(S) 与用户凭据校验，以及私网、环回、链路本地、IPv4-mapped IPv6
和云元数据地址拒绝，并增加响应体大小上限与解析地址固定；`RestTaskInvokTest` 与
`RestTaskUrlPolicyTest` 共 15/15 通过。Java 17
下完整 `bixi-quartz` 聚焦套件为 53 tests、0 failures、0 errors、1 skipped（仅 JDBC
故障转移条件门控）。
上述验证只使用 Java 17、本地 JDK `HttpServer` 和 Maven，没有启动 Docker/Compose，因此
严格退出进度仍按未完成的第三方送达、结果丢失矩阵、Quartz/SBA 节点接管和生产恢复目标
计为约 **94%**，工程实现与自动化验证仍约 **96%**。本轮只验证了本地聚焦 HTTP 路径；真实
Quartz 应用级故障接管仍未验证。

Quartz/SBA 边界复核（2026-09-27）：Quartz 模块 Maven 聚焦套件 39/39、Quartz UI/schema/
migration Node 套件 10/10、迁移脚本 3/3、SBA 多实例静态契约 2/2 均通过，
`make architecture-check runtime-config-check` 和 `codegraph sync .` 也通过。当前 Cloud
Compose 没有独立 Quartz 服务，Gateway 没有 `/job` 路由，Single 只有单调度器；因此现有
`target/phase2-runtime/quartz-single-http-20260927.json` 与 SBA 两副本证据不能证明 Quartz
节点故障接管，节点 kill/recovery 仍明确未覆盖。

2026-09-28 运行证据口径更正：single/cloud enabled/disabled 四组隔离运行矩阵和完整应用依赖
重启均已在独立 Compose project 中完成并保存脱敏证据；本机观测值不等同于生产 SLO，也不
替代结果丢失组合矩阵。个人电脑执行应按资源分层：Tier 1 只跑静态、聚焦 Maven/Node 和脚本
检查；Tier 2 一次只启动 single 或 cloud 一个模式，关闭 AI/多副本并使用低堆配置；Tier 3
的矩阵重复、并行多栈、压力/容量/浸泡、数据库 HA/跨地域及真实第三方回执转 CI 或专用机器。
本机 Docker VM 约 7.82 GiB 内存、宿主数据盘约 39 GiB 可用且已用 92%，因此不能把 Tier 3
作为个人电脑默认步骤。当前阶段二严格退出约 **94%**，工程实现与自动化验证约 **96%**；
仍待真实 DashScope 密钥链、AI 模型配置的重启/多实例持久化、第三方通知厂商送达/回执、
Quartz/SBA 节点故障接管、结果丢失组合矩阵及生产级恢复目标。

2026-09-28 Tier 1 本机复核：在 Java 17、未启动 Docker/Compose 的条件下，
`make architecture-check runtime-config-check quartz-jdbc-failover-static-test
sba-multi-instance-static-test full-application-restart-static-test` 全部通过；其中
Quartz JDBC 静态契约 4/4、SBA 多实例静态契约 2/2、完整重启脚本静态契约 3/3，且
`git diff --check` 通过。`make workflow-test` 的 cloud/single 构建均为 `BUILD SUCCESS`，
`bixi-workflow-biz` 为 192 tests、0 failures、0 errors、7 skipped；7 个跳过项全部来自
需要显式 `OUTBOX_TEST_JDBC_URL` 的 MySQL 可靠通知集成测试。该条件门控是为了避免个人电脑
在没有 MySQL 8/InnoDB 时误报通过；不能用 H2 替代其 `FOR UPDATE`、隔离级别和锁语义证据。
本轮没有追加完整应用栈、并行多栈或压力测试，Tier 2/3 仍按上面的资源分层执行。

同日生成器 Tier 1 复核：`make generator-ci` 在 Java 17 下完成，`bixi-generator` 为
94 tests、0 failures、0 errors、0 skipped，迁移、父子表、生成前端、导入导出、UI、输出及
ownership/support Node 套件均通过；该命令只使用本地 Maven/Node 依赖，没有启动 Docker。

同日个人电脑资源适配复核：未新增或并行启动 Compose 项目，使用现有依赖完成
`ChatServiceImplTest` 14/14、`MessageServiceImplTest` 2/2、完整 Quartz Maven 套件 53 tests（0 failures，1 个 JDBC 条件跳过）、前端
ESLint、生产构建和通知/Quartz Node 套件 11/11；`codegraph sync .` 与 `git diff --check`
通过。`make local-test-preflight BIXI_LOCAL_TEST_MODE=single` 只读通过并提示已有
single/cloud 并行容器；cluster 在 7.8 GiB VM 上被硬性阻止。Docker VM 约 7.82 GiB、宿主可用空间约 38 GiB 且 Docker 卷已接近 48 GiB，因此本轮
没有执行 Tier 2/3 的新栈、并行矩阵、压力、HA 或真实第三方送达测试；这些仍只计为未验证。

同日个人电脑存储压力门禁补强：预检在同一有界 Docker 探针中只读调用
`docker system df --format '{{json .}}'`，报告镜像、容器、卷和构建缓存的总量及可回收量；
可回收量默认达到 20 GiB 时仅输出 `WARN`，不把 Docker Desktop/OrbStack 的逻辑占用当作
统一容量或自动清理。红绿测试现为 10/10，命令不可用时仅告警。当前只读观测为 Docker 卷
47.99 GB（45.1 GB 可回收）、镜像 30.57 GB（21.45 GB 可回收）、构建缓存 11.21 GB，
宿主可用空间约 36 GiB；因此仍不适合在本机追加 Tier 2/3 栈，必须先人工评估 Docker 存储并
保持单模式顺序执行。

同日 AI 模型配置隔离切片：模型配置由进程级字段改为按租户分桶并原子更新，补充跨租户
默认值、部分更新和切换回原租户的回归测试；旧实现先红、修复后绿。Java 17 下完整
`bixi-ai-biz` 反应堆 40/40 通过。该改动只提供进程内租户隔离，重启恢复、cloud 多实例
一致性和持久化仍需后续 schema/repository 设计，未计入已验证的生产能力。

2026-09-28 个人电脑测试适配最终复核：共享只读预检 `scripts/local-test-preflight.sh`
在当前机器上 `static` 退出码为 0，`single`/`cloud` 退出码为 0 但明确警告已有两种模式并行、
Docker 可回收存储约 69.5 GiB；`cluster` 退出码为 1，因 Docker VM 仅 7.8 GiB 而该场景要求
至少 8 GiB。新增门禁的静态测试为 10/10，完整重启 3/3，Workflow 静态套件 57/57，
Quartz JDBC 4/4，SBA 多实例 2/2；`make architecture-check`、`make runtime-config-check`、
`git diff --check` 和 `codegraph sync .` 均退出 0。完整重启脚本默认只跑 `single`，Workflow
双副本脚本在创建报告目录或任何 Docker 资源前强制执行 `cluster` 预检；这些行为均有脚本契约
测试覆盖。

本轮没有启动、停止或清理新的 Compose 项目，也没有把现有 single/cloud 容器当作新验收证据。
因此个人电脑当前只适合 Tier 1 静态/聚焦验证，以及在清理存储后一次只运行一个 Tier 2 模式；
双副本故障、并行矩阵、压力/容量/浸泡、数据库 HA、真实第三方送达和生产恢复目标继续标记为
未验证，不能计入严格退出百分比。

2026-09-28 Phase 2 A/C 增量修复：生成器导入为新表写入固定的内置模板组 ID，历史 `style IS NULL`
记录在预览时回退到同一模板目录，避免干净库导入后预览/生成断链；`GenTableServiceImplTest` 与
`GeneratorServiceImplTest` 在 Java 17 下共 30/30 通过。AI 模型配置移除进程内兼容桶和无参构造，
强制使用按租户 `ai_model_config` 持久化 mapper；迁移补齐 BaseEntity 审计列，校验 `tenant_id`
为 BIGINT、非空、无重复并建立唯一键；schema、持久化重启、租户隔离和控制器密钥脱敏聚焦套件共
20/20 通过。上述增量不把真实 DashScope 密钥、第三方厂商回执或生产多实例故障接管计为已验证。

2026-09-28 第二阶段收口复核：AI 会话列表的删除/重命名改为后端成功后再更新本地状态，
新增前端契约测试；Java 17 `bixi-ai-biz` 聚焦套件 63/63 通过。通知 SSE 新连接按当前
租户和用户重放未读已发布通知，重放只定向新连接并带稳定事件 ID；UPMS 通知测试 54/54、
SSE 测试 6/6、前端通知契约 1/1 通过。工作流业务结果和补偿结果对同一 `result_event_id`
精确重放时允许继续推进，其他事件 ID 的重复结果仍幂等忽略；聚焦测试 9/9 通过。上述
验证均使用 Java 17、本地 H2/HTTP Server 或 Node，不启动 Compose、压力测试、第三方服务
或 HA 演练。`make architecture-check`、`make runtime-config-check`、`codegraph sync .`
和 `git diff --check` 均通过。当前口径仍为：第二阶段工程实现与自动化验证约 **96%**，
严格退出约 **94%**；真实 DashScope 密钥链、AI 配置重启/多实例持久化、第三方送达回执、
Quartz/SBA 节点接管、结果丢失组合矩阵和生产恢复目标继续未验证。

2026-09-28 最新功能收口：A 生成器修复数据源上下文泄漏和输出路径目录穿越，Java 17
`make generator-ci` 通过（Maven 97/97、Node 合同 38/38）；B 工作流迟到业务/补偿结果在
Flowable execution 消失后先写入 durable 状态并保持幂等，聚焦测试 12/12；C AI chat/RAG/stream
同步写入 `ai_conversation` 与 `ai_message`，刷新会话可恢复历史，AI 聚焦 29/29 通过。上述
验证均未启动 Docker/Compose。按功能实现和 Tier 1 证据更新估算：第二阶段 A **100%**、B
**97%**、C **95%**，工程实现与本机自动化验证 **98%**；严格退出 **96%**。未验证项仍为
真实第三方密钥/送达回执、Quartz/SBA 节点接管、生产级恢复目标及少量跨重启/多实例运行证据。

2026-09-28 JDK 17 与通知回归复核：本机有效 Maven 命令显式使用
`/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home`；根 POM 的
`source/target/release` 均为 17。通知租户收件人校验新增后，补齐 H2 聚焦夹具的
`tenant_id=1`，`NoticePublicationIntegrationTest` 18/18、`NoticeLocalDeliveryIntegrationTest`
5/5 在 Java 17 下通过；`make architecture-check` 和 `make runtime-config-check` 在同一
JDK 17 环境下通过。裸 `mvn` 可能受本机 `JAVA_HOME` 指向 JDK 25 影响，该结果不计入项目验证。
同时修正 Makefile：`architecture-check` 现在继承同一 `JAVA_HOME`，并允许显式覆盖
`JAVA_17_HOME`，避免标准门禁静默使用环境默认 JDK。
