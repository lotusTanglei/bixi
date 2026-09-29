# 阶段二 2F：双 owner 恢复与审计入口

更新时间：2026-09-24。此记录只覆盖恢复管理切片，不代表阶段二整体完成。

## 已交付

- UPMS owner 新增 `/upms/recovery` 查询接口：Outbox、Inbox、隔离消息均只暴露元数据/受控正文，与 Workflow owner 的状态枚举和分页限制一致。
- UPMS owner 新增 Outbox/Inbox 失败重试接口；重试调用底层状态 CAS，实际未变更时返回 `changed=false`，不会夺取其他 worker 的租约。
- UPMS 与 Workflow 的失败重试将状态 CAS 和 `wf_recovery_audit` 写入放在同一本地数据库事务，记录操作者、owner、事件 ID、原因和是否实际变更；审计失败会回滚重试。无环境事务的拒绝与重放审计仍以单条 JDBC 写入提交；审计时间使用 `CURRENT_TIMESTAMP(6)`，兼容 MySQL 8 与 H2。
- Workflow/UPMS 增加 `/reconcile` 脱敏对账接口，关联业务表与最新 durable 状态并区分 `MATCHED`、`PENDING_DELIVERY`、`FAILED_DELIVERY`、`BUSINESS_MISMATCH`。
- 对账报告进一步关联命令、自动任务和隔离证据，输出 `requestId`/`commandStatus`、`operationId`/`businessTaskStatus`、`compensationId` 和 `quarantineEvidenceId`；不支持版本只宽松提取这些脱敏标识，不暴露事件 payload。
- 未解决的隔离消息会进入对账报告；同一事件已由 Inbox 标记为 `PROCESSED` 或 `IGNORED` 时跳过历史隔离记录，避免把已收敛事件继续报告为故障。
- Workflow/UPMS 增加隔离消息 replay 接口；坏 wire 数据会独立记录失败审计，合法消息复用 owner InboxExecutor 去重。
- Workflow/UPMS 复用 `workflow_recovery_view`、`workflow_recovery_edit` 权限；共享恢复页面增加 owner 切换、业务对账页和隔离消息重放，并展示上述关联标识，single/cloud 使用相同请求契约。
- 本轮二阶段一致性复核补齐了可信流程发起、稳定 `requestId`、业务轮次唯一约束、隔离数据 owner 边界、永久失败 Inbox 显式 replay、非默认租户传播、恢复 CAS 与审计原子性，以及 `wf_business_task` 自动任务持久状态机；公共流程发起入口不能再绕过请假业务命令入口。

## 实际验证

- `WorkflowRecoveryMetadataTest`：2/2，覆盖不支持版本的脱敏关联标识提取和损坏正文的空结果。
- `UpmsRecoveryServiceTest`：7/7，覆盖 owner 映射、状态归一化、事件 ID 校验、失败重试 CAS、不泄露 payload、命令/业务任务字段和隔离证据对账。
- `WorkflowRecoveryServiceTest`：6/6，覆盖失败重试、合法隔离消息 replay、命令/自动任务字段、隔离证据过滤、对账分类和审计。
- 前端恢复对账契约测试：1/1，确认六个新增脱敏关联字段同时保留在 TypeScript 契约和对账表格中。
- `UpmsRecoveryControllerPermissionTest`：2/2，覆盖读/写权限隔离。
- `UpmsRecoveryAuditStoreTest`：1/1，H2 实际验证无环境事务时写入成功，并在恢复事务回滚时一同回滚。
- `RabbitInboxListenerTest`：2/2，覆盖隔离落库失败时保持未确认，以及隔离记录保留完整 wire body 供后续 replay。
- 本次 UPMS/Workflow 定向 Maven reactor：15/15（metadata 2、Workflow recovery 6、UPMS recovery 7），`BUILD SUCCESS`；此前恢复管理切片的权限/审计测试和前端 Vite production build 也已通过。
- 2026-09-24 使用 Java 17 复验 Flowable 聚焦用例 37/37：`ProcessInstanceControllerTest` 1/1、`WorkflowRecoveryServiceTest` 7/7、`WorkflowStartIdempotencyTest` 25/25、`WorkflowLeaveBusinessTaskIntegrationTest` 4/4；可信启动先重放相同命令，再对新命令检查业务轮次冲突。
- `python3 scripts/test-workflow-command-migration.py` 在真实 MySQL 上通过 15/15，覆盖业务轮次唯一索引的租户隔离、重复执行、存量重复数据拒绝、同名不兼容索引拒绝及数据库层重复写入拦截。
- `bash scripts/test-reliable-mysql.sh` 在 MySQL 8.0.45、`REPEATABLE-READ` 下完成 cloud 63/63 和 single 63/63；两次 `InboxExecutorTest` 均为 20/20，未因数据库不可用跳过。
- 真实 Rabbit 自动任务链路复用了本切片的两个 owner Inbox/Outbox 和隔离表：`bash scripts/test-reliable-rabbit.sh`
  在 MySQL `8.0.45` + RabbitMQ `4.0.5` 上通过 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest` 1/1，
  证明自动任务请求/结果在两 owner 边界持久化为 `DELIVERED`/`PROCESSED`，并验证重复 request 不产生第二条 booking。
  该测试是业务链路证据，不等同于完整恢复报告或进程故障注入。

## 修复记录

- `RabbitInboxListener.safeBody` 改为保留完整 UTF-8 wire body，不再截断到 4096 字符；隔离区 replay 继续由 `DurableMessageWireCodec` 做报文校验，避免大报文无法重放。

## 明确未完成

- command/business-task/quarantine 恢复报告的代码、共享 UI 和聚焦测试已完成；尚未在真实 MySQL 运行态执行该报告并保存实际输出。
- 已用真实 MySQL/Rabbit 执行 Workflow ↔ UPMS 自动任务业务端到端验收；仍未完成应用进程重启、发送/消费提交窗口
  故障注入，以及基于真实运行态的完整自动任务恢复报告。
- 完整容器重启、压力/容量、长时间浸泡、数据库高可用和跨区域验证只在 CI 或专用测试机执行，不在个人电脑重复运行。
