# Flowable 双模交付清单

更新：2026-09-23。状态：阶段一、阶段二 2A/2B/2C 核心业务闭环及 Rabbit 传输验证通过；2E 第一切片已完成自动任务事件契约、请假登记/补偿原语、v2 Flowable 路径、双方 handler 定向验证、真实 MySQL/Rabbit 跨 owner 自动任务链路、提交窗口故障注入及旧轮次/未知 schema/同 eventId 不同 payload 矩阵；Workflow/UPMS 两个 owner 的恢复查询、失败重试、隔离消息 replay、脱敏对账 API 和共享管理页已落地，对账报告已补齐 command/business-task/quarantine 关联标识；阶段二的 Inbox 提交前进程重启、登记/补偿竞争和 Rabbit ACK 前 SIGKILL/redelivery 已在临时 Docker 环境通过，完整应用容器重启与真实运行态恢复报告仍待补；阶段三已完成 Flowable lock-owner/租约配置绑定、运行诊断 API/UI、双副本 Compose、显式异步边界、v2/v3 定义并存、受控迁移，以及真实双副本异步 Job/Timer Job 的 SIGKILL、锁过期接管、节点重启、最终业务收敛和优雅停机后存活副本继续服务；真实 MySQL 存量 v2 实例维护窗口也已通过，阶段三剩余证据项为性能/容量报告和执行中任务的优雅停机 drain 量化；阶段四 4A 候选身份切片已完成，4B 及后续审批扩展仍未开始。

开发进度粗估：按本清单阶段权重，路线图整体约 **77%**，阶段一 100%、阶段二约 99%（真实 MySQL/Rabbit 自动任务链路、broker/consumer 重启、发送成功后 mark-delivered 故障窗口、真实 timer 超时后的迟到结果/补偿收敛、登记/补偿竞争、ACK 前进程崩溃，以及旧轮次/未知 schema/同 eventId 不同 payload 矩阵已通过；完整应用容器重启和完整阶段报告仍未完成，见 [EVIDENCE-2G.md](EVIDENCE-2G.md)）、阶段三约 **95%**（已完成 lock-owner/租约配置绑定、Flowable/Outbox/Inbox 运行诊断 API/UI、双副本 Compose、显式异步边界、v2/v3 并存、受控迁移、真实双副本异步 Job/Timer Job 的 SIGKILL、8 秒锁过期接管、节点恢复、最终业务收敛、优雅停机后存活副本继续处理新流程，以及真实 MySQL 存量 v2 实例维护窗口；剩余证据项为性能/容量报告和执行中任务的优雅停机 drain 量化，见 [EVIDENCE-3B.md](EVIDENCE-3B.md)）、阶段四约 **12%**（4A 候选用户/候选角色、服务端 `claimable`、部署前校验、双模适配和共享任务页已完成，4B 及后续七类审批扩展仍未开始）。百分比表示需求完成度估计，不是测试通过率或生产可用性。

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
- [x] 跨表事件/命令对账接口、隔离消息 replay、脱敏恢复结果，以及 command/business-task/quarantine 关联字段和共享 UI；真实 MySQL/Rabbit 自动任务恢复报告仍待运行并保存证据。
- [ ] 双模响应丢失、独立应用进程重启、真实超时竞态、剩余发送/消费提交窗口故障注入及阶段报告；当前已有 Rabbit broker/consumer 重启、Outbox mark-delivered 失败后租约重放、Inbox 提交前 JVM 重启及 ACK 前 `SIGKILL`/redelivery 证据，仍缺完整应用容器重启和恢复时延，见 [2G 证据](EVIDENCE-2G.md)。

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
- [ ] 表单版本绑定/服务端校验/节点字段权限。
- [ ] 流程轨迹/异常任务/事件投递管理页。
- [ ] 复用已有前端基础完善 BPMN 设计器。

## 验收矩阵

| 组合 | 核心验收 | 审批闭环/权限/日志 | 可靠协作/重启 | 集群/版本 | 当前证据 |
| --- | --- | --- | --- | --- | --- |
| A single enabled | 必须 | 必须 | 无 Workflow 新增 MQ/Nacos/Gateway 依赖 | 定时与重启恢复 | 阶段一闭环、可靠 single 提交/回写及本地进程重启证据通过；完整应用容器重启报告仍待补 |
| B single disabled | 核心运行通过 | 无引擎/执行器组件测试通过，新库禁用不建 ACT，已有数据保留 | 关闭期间不消费 | 重新启用恢复 | 禁用模式核心和无引擎行为通过；关闭期间积压、不消费及重新启用恢复的完整报告仍待补 |
| C cloud enabled | 必须 | 正常网关及服务链 | Rabbit 真传输、真实 MySQL/Rabbit 请假自动任务提交/回写通过 | 至少两个副本故障/新旧定义 | 专用跨 owner 测试通过；Flowable 表一次性初始化后恢复 schema-update=false；双副本故障接管、新旧定义及存量 v2 实例维护窗口通过，完整应用容器重启仍待补 |
| D cloud disabled | 核心运行通过 | 当前默认编排无 Workflow 服务/路由，禁用组件及 Feign 测试通过 | 关闭期间不消费 | 核心回归与重新启用恢复 | 禁用模式核心和无引擎行为通过；关闭期间积压、不消费及重新启用恢复的完整报告仍待补 |

共同门禁：`make architecture-check`、`make runtime-config-check`、`make backend-cloud-ci`、`make backend-single-ci`、`make frontend-ci`、`make verify-cloud`、`make verify-single`、`codegraph sync .`、`git diff --check`。新增用例必须复用同一业务断言。窄范围测试只证明其覆盖部分。

执行策略：个人电脑只运行聚焦 Java/Node 测试、架构/配置检查和静态检查；完整 cloud/single CI、重复 Docker 故障注入、压力/容量、长时间浸泡、数据库高可用和跨区域验证转移到 CI 或专用测试机。

## 续接位置与证据

当前 1A–1D 的装配、审批和独立请假业务均已实现并完成定向测试，记录见 [1B](EVIDENCE-1B.md)、[1C](EVIDENCE-1C.md) 和 [请假说明](LEAVE_STAGE1.md)。共享前端、菜单开关、cloud Workflow 服务与优先网关路由已实现，前端及静态门禁通过。新增业务轮次存入扩展表，关闭引擎历史仍可回写；回调有独立事务和审计。

阶段一四组实际运行现已通过，完整记录见 [EVIDENCE-STAGE1.md](EVIDENCE-STAGE1.md)。临时本地项目/环境保持用于后续阶段；共享前端桌面/手机浏览器检查已通过。最新后端 cloud149项/single147项、前端构建、16项UI回归通过。新计划 [PLAN-STAGE2.md](PLAN-STAGE2.md) 已解决公共业务关联被伪造抢占的过渡风险：2A只保证请求幂等，2C切换可信持久化提交后才添加业务轮次唯一约束。

阶段二 **2A 已完成**，见 [2A 验收记录](EVIDENCE-2A.md)。最终固定源码的后端 cloud 304 项、single 302 项通过；实际双模 HTTP 和浏览器服务器成功后丢响应、刷新原样重试/查询、失去认证后重新登录恢复均通过。关闭模式核心验收通过，103 条工作流状态指纹前后一致。共享 UI 44 项及 ESLint/构建通过。

当前阶段二 2C 的可靠提交与回写已在 single/cloud 真实运行态收口。真实 MySQL 工作流 192/192、可靠消息 cloud/single 各 55/55 通过；`make reliable-rabbit-test` 的 Rabbit owner/listener 6/6 及跨 JVM broker 重启 seed/consume 2/2 通过；统一 acceptance 已验证 `APPROVED/REJECTED/CANCELED`、幂等/冲突、审批人和数据权限。新增 2F 定向验证：恢复元数据/Workflow/UPMS 三个聚焦测试类共 15/15，UPMS 审计 H2 实际落库 1/1；共享恢复页已支持 Workflow/UPMS owner 切换，并展示 command/business-task/quarantine 六个脱敏关联字段；Workflow/UPMS 对账、隔离区 replay 和独立审计接口已落地。真实 MySQL/Rabbit 自动任务测试 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest` 已扩展为 5/5，确认两 owner 的 Outbox/Inbox 状态、booking 幂等、真实 timer 超时后的迟到登记结果忽略与补偿收敛、登记/补偿竞争，并覆盖旧轮次 `IGNORED`、未知 schema 隔离、同 eventId 不同 payload 冲突隔离；`WorkflowLeaveBusinessTaskIntegrationTest` 4/4 验证真实 Flowable timer、补偿等待、迟到结果和 v2/v3 并存。`scripts/test-workflow-process-restart.sh` 的 Inbox 提交前杀 JVM 恢复通过，Rabbit ACK 前杀 consumer JVM 后 redelivery 去重也已通过。阶段三双副本故障接管和真实 MySQL 存量 v2 实例维护窗口已经通过；阶段二尚未完成完整应用容器重启、结果丢失全矩阵、精确恢复时延和真实运行态自动任务恢复报告；4A 候选身份切片已完成，4B 及后续审批扩展未开始。不能以当前百分比宣称完整路线图完成。不提交/推送、不编辑用户状态文件。

本轮收口复验：可靠 cloud 使用 `BIXI_HTTP_PORT=28380 WORKFLOW_ENABLED=true BIXI_RELIABLE_ENABLED=true make verify-cloud` 通过；可靠 single 使用 `BIXI_HTTP_PORT=18380 WORKFLOW_ENABLED=true BIXI_RELIABLE_ENABLED=true make verify-single` 通过。single 验收环境额外使用 `SINGLE_JAVA_OPTS='-Xms128m -Xmx512m'`，此前默认堆上限在多套并行环境下触发宿主 OOM（退出码 137），降低测试环境堆上限后容器保持健康；这不是业务代码变更，也不代表已完成进程重启故障注入。
