# 阶段二 2E：自动任务与补偿第一切片

更新时间：2026-09-22。此记录只覆盖当前切片，不代表 2E 或阶段二整体完成。

## 已交付

- `bixi-workflow-api` 新增四类固定版本事件：`WORKFLOW_BUSINESS_TASK_REQUESTED`、`WORKFLOW_BUSINESS_TASK_RESULT`、`WORKFLOW_COMPENSATION_REQUESTED`、`WORKFLOW_COMPENSATION_RESULT`。
- `WorkflowEvent` 对 source/target owner、流程实例、聚合序列和 payload 类型执行严格关联校验；codec 对 payload 字段集合、UUID、摘要、时间和 nullable 结果字段执行严格编解码。
- UPMS 新增 `LeaveBookingService`，以 `operationId` 和 `requestHash` 保证请求重放稳定，以 `compensationId` 写入补偿墓碑；状态只允许 `BOOKED` 和 `CANCELED`。
- 新库 SQL 与增量迁移均加入 `demo_leave_booking`，唯一约束覆盖租户、请假轮次和补偿标识。
- Workflow 新增 `demo_leave_approval_v2.bpmn20.xml`：审批后 service task 发布登记请求，receive task 等待结果，失败或超时进入补偿请求和 receive task，最终通过 terminate end 结束。
- Workflow 已接入登记/补偿请求 delegate、版本化 Outbox 发布器和结果 handler；UPMS 已接入自动任务 handler，将登记/补偿结果写回对应 Outbox。

## 实际验证

- `WorkflowEventCodecTest`：9/9 通过。
- `LeaveBookingServiceTest`：5/5 通过，覆盖首次登记、同摘要重放、摘要冲突、补偿先到和已登记后补偿。
- `LeaveBookingMapperIntegrationTest`：3/3 通过，真实 H2/MyBatis 验证 `FOR UPDATE` 查询、`(leave_id, round)` 唯一约束、补偿 tombstone 持久化和双线程并发最多一条 `BOOKED`。
- `LeaveBusinessTaskEventHandlerTest`：2/2；`LeaveWorkflowEventHandlerTest`：3/3；`LeaveAdapterConfigurationTest`：3/3。
- `WorkflowBusinessTaskEventPublisherTest`：1/1；`WorkflowBusinessTaskResultHandlerTest`：2/2。
- `WorkflowApprovalIntegrationTest`：47/47；`WorkflowStartIdempotencyTest`：19/19；`WorkflowLeaveBusinessTaskIntegrationTest`：4/4。
  新增真实 Flowable timer 切片：执行 persistent timer job 后进入补偿等待；补偿完成前后的迟到登记结果均为
  `IGNORED`，流程只终止一次且不重开 receive task。Flowable `FULL` history 下以终态
  `compensationResultOperationId` 识别已完成补偿，避免流程实例删除后把迟到结果误判为可重试。
- `WorkflowUpmsAutomaticTaskIntegrationTest`：cloud 1/1、single 1/1。使用真实 Flowable、MyBatis mapper、
  `LeaveBookingService`、`LeaveBusinessTaskEventHandler` 和 `LeaveWorkflowEventHandler`，完整执行
  UPMS durable start → Workflow 人工任务完成 → receiveTask 等待 → UPMS booking 落库 → Workflow 结果 trigger
  → terminal event → UPMS `APPROVED` 回写；断言 booking 为 `BOOKED`、流程扩展状态为 `completed` 且运行实例已消失。
- Workflow 聚焦 Maven reactor 构建：71/71，`BUILD SUCCESS`。

### 新增真实 MySQL/Rabbit 跨 owner 验证

- 2026-09-22 运行 `bash scripts/test-reliable-rabbit.sh`，脚本创建一次性 MySQL `8.0.45`
  （`REPEATABLE-READ`）和 RabbitMQ `4.0.5`，测试结束后自动清理容器。
- Rabbit 通用 owner endpoint、Inbox listener 和 broker 重启 seed/consume 验证通过；其中 broker
  重启前后使用不同端口，重启后的新 JVM 消费到了重启前持久化的消息。
- `WorkflowUpmsAutomaticTaskRabbitIntegrationTest`：5/5，通过真实 Flowable、MySQL
  `JdbcOutboxStore`/`JdbcInboxStore`/`JdbcQuarantineStore`、两个 `RabbitOwnerEndpoint` 和两个
  `OutboxDispatcher` 执行完整链路：UPMS start request → Workflow 人工审批 → automatic task request
  → UPMS booking → result → Workflow receive/terminal completion → UPMS `APPROVED`。
- 同一真实测试还覆盖消息矩阵：将请假记录推进到更高轮次后，旧轮次自动任务被 Inbox 记录为 `IGNORED` 且不产生 booking；未知 schema 版本在 handler registry 前被隔离为 `PERMANENT` 且不写 Inbox；相同 `eventId` 携带不同 payload 时被隔离为 `CONFLICT`，原已处理 Inbox 保持 `PROCESSED`。
- 同一测试断言双方 Outbox 为 `DELIVERED`、Inbox 为 `PROCESSED`、`demo_leave_booking` 为 `BOOKED`、
  `demo_leave_request` 为 `APPROVED`，并重复投递同一 automatic-task request，确认 booking 仍只有一条。
- 同一测试还注入了发送成功后 `mark-delivered` 更新失败：暂停 UPMS consumer，Rabbit 队列保留首条消息，
  Workflow outbox 租约过期后重放同一 event；恢复 consumer 后 Inbox 去重，booking 仍只有一条。
- 同一测试的超时切片暂停 UPMS consumer，执行真实 Flowable persistent timer job 后进入
  `waitCompensationResult`；恢复 consumer 后迟到的登记结果被 Workflow 标记为 `IGNORED`，补偿结果使流程
  进入 `terminated`，`demo_leave_request` 与 `demo_leave_booking` 均为 `CANCELED`，且 booking 仍只有一条。
- 同一测试的竞争切片并发执行登记和补偿请求，两个事务使用相同业务锁顺序；最终 booking 只有一行且为
  `CANCELED`，流程终止，登记与补偿结果按实际竞争顺序经 Rabbit 返回 Workflow。
- `scripts/test-reliable-rabbit.sh` 实际结果：Rabbit owner/listener `6/6`，broker 重启 seed/consume `1/1 + 1/1`，
  Workflow 自动任务 `5/5`，另含 ACK 前 `SIGKILL` 的 seed/resume `1/1 + 1/1`；最终 Maven reactor
  `BUILD SUCCESS`。ACK 故障窗口详情见 [EVIDENCE-2G.md](EVIDENCE-2G.md)。

## 明确未完成

- H2 版本的 Flowable v2 与 handler 测试仍保留，用于快速验证 BPMN/业务状态；MySQL/Rabbit 端到端证据由上面的
  专用测试补充，二者均不替代应用进程级黑盒验收。
- H2/Flowable 已覆盖真实 timer 超时、补偿等待和迟到登记结果幂等；真实 MySQL/Rabbit 已覆盖旧轮次、未知 schema、
  同 eventId 不同 payload 及登记/补偿并发竞争。发送成功后 `mark-delivered` 失败、Rabbit broker 重启、consumer
  重启和 ACK 前 JVM 崩溃已有证据；完整应用容器重启、结果丢失全矩阵和恢复时延仍未覆盖。
- 2F 的恢复查询、失败 CAS 重试、隔离区 replay、UPMS owner 对账和统一 API/UI 已有定向证据；尚未在真实
  MySQL 运行态生成完整自动任务恢复报告，也未进入第三阶段集群能力。
