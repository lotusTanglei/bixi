# 阶段二 Inbox 与业务切换细化

状态：2A 已完成；Outbox 原语已审查集成，Inbox 已通过规格/质量审查并集成，主工作区真实 MySQL 两 Profile 各 55 项通过。当前运行链仍是阶段一同步回写，须在可靠接收器和提交链一起就绪后切换。它细化 `PLAN-STAGE2.md`，不另建事件平台。

## 依赖与分工

- `common-mq/reliable` 保持无业务依赖。Outbox 的 `DurableMessage`、严格规范 JSON/摘要、不可变消息、数据库 UTC 和租约 fencing 作为唯一基础。
- 下一批只新增 `DurableMessageHandler`、`JdbcInboxStore`、`InboxExecutor`、`LocalDurableTransport` 及必要状态/结果类型。领域事件契约仍位于 workflow-api，处理器分别归 Workflow 与 UPMS。
- 固定表名 `reliable_inbox`，主键 `(target_owner,event_id)`；接收前核对消息摘要、目标所有者与类型/来源注册表。可信源凭据由 Rabbit/本地适配边界验证，摘要本身不证明来源。
- API、SQL、common-mq、Workflow、UPMS 每处同时只有一个编辑所有者。原语通过独立规格/质量审查后再接业务；不在接收器就绪前删除运行回调。

## 接收与处理事务

可将接收登记和业务执行拆为两步，但只有与业务事务一起提交的 PROCESSED/IGNORED 才代表处理成功。RECEIVED/IN_FLIGHT 绝不作为重复成功返回。

1. 在短事务中接管不可变消息，保存 RECEIVED。相同目标/eventId 必须匹配 source/type/schema/hash；重复消息不能覆盖 payload、重置次数或把 FAILED 变回待处理。使用锁定的当前读核对，避免 REPEATABLE-READ 旧快照。
2. 当前请求可触发一次定向领取；后台恢复扫描使用相同 owner、到期时间、随机 token、有限次数与 `SKIP LOCKED`。每个短领取事务提交 IN_FLIGHT 后释放连接。
3. 处理事务按 `(target,eventId,token)` 锁定当前行，确认租约仍有效及未处理，然后调用已注册领域处理器。handler 的业务更新、Flowable、命令成功结果、后续 Outbox 与 Inbox PROCESSED/IGNORED 共享本地事务和物理 DataSource。
4. 处理期间 Inbox 行锁保留至提交；其它 worker 不能在原事务进行中偷走该行。领取已过期但未开始处理的旧 worker 必须在进入 handler 前拒绝；过期 worker 不允许先发生副作用再检查 token。
5. handler 异常使业务与处理结果整体回滚。随后独立短事务按 token CAS 保存失败原因、attempts、next_attempt 或 FAILED，不更新已换 token 的记录。持久化失败记录也失败时，接收方不得确认已接管责任。
6. single 的本地 adapter 调用相同 executor；handler 成功提交才允许源 Outbox 标记已交付。失败保留源重试，同时目标可能已持久化接管重试；两路重试都受同一 Inbox 租约与业务幂等限制。
7. Rabbit 的 broker 消费确认发生在处理成功或目标持久化接管重试之后；恢复 worker 必须已装配。未知 schema/source、协议冲突和不能解析的消息须有持久失败证据，不无限热重投，也不能伪装成业务成功。

这一拆分在宕机窗口留下的是可恢复 RECEIVED/IN_FLIGHT，绝不提前留下成功去重记录。实现若选择正常路径单事务插入+执行，也必须为独立失败持久化和后台恢复保留相同成功判定与 fencing；最终只采用一套实现，不维护两套消费者规则。

## 必须先写的真实数据库用例

| 场景 | 断言 |
| --- | --- |
| 正常处理与重复 | Inbox 成功、业务一条、后续 Outbox 一条；并发重放返回原结果，不再调用有效副作用 |
| handler 在业务/引擎变更后失败 | 所有变更回滚，Inbox 不成功，失败重试信息单独持久化；使用原消息恢复 |
| 接管提交后、执行前进程死 | 到期扫描领取原消息，原 payload/eventId 不变 |
| 领取提交后进程死 | 租约到期重领；旧 token 的完成/失败/处理入口无权改新状态 |
| 执行事务未提交时重领 | 其它 worker 跳过被锁的 Inbox；原提交后只看到已成功 |
| 执行提交后、传输确认前进程死 | 原成功 Inbox 保留，重复消费无重复业务副作用 |
| 同 eventId 改源/目标/类型/版本/内容 | 接收完整性及协议冲突被拒绝，原记录保留，不确认另一个消息成功 |
| 两 owner 共用单库 | 相同 eventId 不串数据；扫描、失败更新、重试和查询都限定 targetOwner |
| 旧 RR 快照和并发接管 | 比较用当前读，不产生错误未找到或覆盖已成功状态 |
| FAILED/忙碌/未来重试 | 均不按“已存在记录”返回已处理；可查询明确状态，有界退避且尝试次数持久化 |
| 非 UTC 会话 | 到期和租约仍按数据库 UTC，无本地时区偏移 |

使用真实 MySQL/JDBC 与至少两个独立执行器；集成 Flowable 时再增加 engine/扩展/Inbox/Outbox 实际回滚用例。由模拟处理器计数只能证明原语调用次数，不能代替领域副作用幂等测试。

## 2B/2C 一次性业务切换

1. UPMS 校验申请人、状态、审批人后，事务提交 SUBMITTING、稳定业务命令与 StartRequested Outbox；HTTP 返回已接受。公开 DTO 不接受可信 actor/owner/round 自报值。
2. Workflow 的内部 StartRequested handler 在 Inbox 事务使用 REQUIRED 领域执行路径，不调用公开方法的 SecurityContext 权限 facade，也不套用同步 REQUIRES_NEW 命令代理。
3. Workflow 在引擎事务写 Started/Completed Outbox；事件 ID 在业务命令/实例生命周期内稳定，不能对同 dedupKey 每次生成随机新 ID。同步结束时先插扩展并记录 STARTED，再记录终态。
4. UPMS 的 Started/Completed handler 核对 command、业务键、轮次、实例、状态与序号；完成先到可以建立正确绑定并终态，迟到 Started 标记 IGNORED。冲突/未来轮次持久失败，旧轮次不覆盖当前申请。
5. 同时关闭旧 AFTER_COMMIT callback/Feign/local receiver 投递职责，封闭公开业务关联与保留模型 START，完成历史绑定对账后加业务轮次唯一约束。不得双投递或在可靠链路故障时偷偷回到同步兜底。
6. single 先验证无 Rabbit 完整异步闭环与重启恢复；cloud 再接受限账户和专用 vhost 的真实 Rabbit。后续自动任务、补偿和管理入口继续沿同一契约、原语和所有者隔离实现。

完成后更新主计划、迁移/回滚与模式切换说明、实际测试证据和百分比；当前隔离原语验证不代表可靠业务切换已完成。
