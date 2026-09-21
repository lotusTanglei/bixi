# 阶段三：Flowable 多实例与故障恢复实施准备

状态：准备记录，尚未实施或验收。必须在阶段二可靠提交、Outbox/inbox、自动任务和补偿全部完成后执行。当前实施仍在阶段二 2A，不能把本文件当作阶段三完成证据。

## 已核查的配置边界

- 继续使用实际依赖 Flowable 7.1.0。核查来源为该版本 `flowable-spring-boot-autoconfigure` JAR 内的 `META-INF/spring-configuration-metadata.json`，没有推测其他版本的参数名。
- 实际执行器参数前缀为 `flowable.process.async.executor`，其中 `async-job-lock-time`、`timer-lock-time`、`reset-expired-jobs-interval`、`default-async-job-acquire-wait-time`、`default-timer-job-acquire-wait-time` 接受 `Duration`；`lock-owner` 接受字符串。优先使用这些字段，避免已弃用的 `*-in-millis` 形式。
- 实际参数还包括 `max-async-jobs-due-per-acquisition`、`max-timer-jobs-per-acquisition`、`reset-expired-jobs-page-size`、`reset-expired-job-enabled`、`unlock-owned-jobs`。最终必须以启动后读取的引擎配置和实际故障恢复证明绑定生效。
- Bixi 当前 `WorkflowProperties` 控制 `workflow.enabled`、`workflow.async-executor-activate`、`workflow.database-schema-update` 和历史级别。公共配置器会写入引擎配置；启用开关仍由 Bixi 这一层控制，不能仅设置另一个 Flowable 开关而忽略覆盖顺序。
- 阶段一已为 Flowable 适配现有 DynamicTp 原生 `taskExecutor`，没有创建第二个线程池。阶段三须验证共享线程池下的领取量和执行耗时；不得为调参重新引入同名 Bean、重复引擎或线程池生命周期冲突。

## 逐项实施与验证

1. 提供独立 cloud 多副本 Compose 覆盖配置：两个 Workflow 副本使用同一镜像、数据库、Delegate/Listener 和事件契约，分别向 Nacos 注册；通过网关访问不指定节点。避免固定容器名和重复宿主端口。single 保留同一业务模块与独立配置。
2. 每个运行实例分配可区分的 `lock-owner`，日志与诊断关联该标识。明确连接池、共享任务池、引擎领取批量、锁时长、过期扫描和停机宽限；验证配置实际绑定，记录生产候选值与短时故障演练值。节点时钟须同步，不能将 Flowable 的锁期限描述为无时钟依赖。
3. 在验收 BPMN 中显式加入异步边界与定时节点。短事务 Delegate 只写本地持久状态和 Outbox，外部副作用沿阶段二的 operationId 与补偿协议执行。打开执行器本身不证明节点异步，也不保证副作用只执行一次。
4. 核对 Flowable 7.1.0 实际数据库创建/升级资源，提供受控初始化入口和明确维护窗口。多个运行副本必须保持 schema-update=false；需要变更时先停止领取和新写流量，执行一次迁移并核查版本，再恢复副本。复用阶段二扩展表和可靠性表迁移，不重放全量业务初始化。
5. 发布保留相同 process key 的新版本：确认定义去重策略、旧 definitionId 实例沿原模型完成、新申请进入新版本。保留旧 Delegate、监听器、表单版本和事件 schema；对旧实例做迁移必须是单独显式操作。
6. 复用阶段二恢复查询展示引擎可执行/定时/重试/死信任务、Outbox/inbox 积压、最老等待时间和最近失败；关联 requestId、eventId、operationId、processInstanceId 和业务单/轮次。恢复写操作继续使用权限、原因、稳定请求 ID 和持久审计。
7. 增加可重复故障脚本，所有杀进程动作只针对本脚本创建的本地测试项目。先通过实际持久化标志确认进入目标窗口，再发 SIGKILL；不要用固定 sleep 推断已进入窗口。
8. 运行四组启停及共用业务断言。分别记录单节点停止后的请求恢复、异步锁过期恢复、定时任务推进和 Outbox 重领时间；single 必须在 Rabbit 停止且既有无关监听/健康检查禁用时完成重启恢复。
9. 记录本机硬件、Docker 资源限制、MySQL/Rabbit 版本、模型、并发、样本量、成功率、延迟分位数与积压。性能数据只作为该环境结果；这里的两个 Workflow 副本仍共享单数据库，不表示数据库高可用或跨地域容灾已验证。

## 故障断言矩阵

| 触发点 | 必须观察到的结果 |
| --- | --- |
| cloud 停一个 Workflow 副本 | 其余副本继续处理正常网关请求；Nacos/Gateway 恢复延迟有实测记录 |
| 异步节点已领取，事务提交前杀节点 | 锁到期后由存活节点恢复；无部分业务提交 |
| 业务副作用已提交，结果/确认前杀节点 | 相同 operationId/eventId 重试，不重复登记；流程最终收到稳定结果 |
| 定时任务到期前后杀节点 | 存活节点在锁/扫描窗口后推进，只有一次有效状态转换 |
| 多实例同时领取 Outbox/inbox | 非重叠有效租约；旧 token 不能覆盖新持有者结果；重复传输无重复业务副作用 |
| 旧、新定义版本同时存在 | 旧实例保留旧定义，新实例使用新定义，两者都能完成 |
| single 无 Rabbit 重启 | 从本地持久化命令、事件和定时任务恢复，最终状态与 cloud 相同 |
| workflow.enabled=false 后再启用 | 关闭期间不拉取或确认积压，核心正常，数据保留；重新启用后恢复处理 |

实施时以独立规格与质量审查、实际 MySQL/Rabbit/进程故障证据和当前 `PROGRESS.md` 更新为完成依据。阶段四仍需单独完成候选组、退回/重提/撤回、会签/或签、提醒、表单权限和设计器。
