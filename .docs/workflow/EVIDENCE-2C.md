# 阶段二 2C：可靠协作切换（进行中）

更新时间：2026-09-21。当前只记录已实现和已验证的范围，不代表双模可靠业务闭环已完成。

## 已实现

- UPMS 请假提交在可靠开关打开时先提交 `SUBMITTING`，并在同一事务写入 `WORKFLOW_START_REQUESTED` Outbox、命令 ID 和请求摘要。
- Workflow Inbox 在同一事务内启动 Flowable、写入扩展表，并写入 `WORKFLOW_STARTED`/`WORKFLOW_START_REJECTED` 后继 Outbox。
- Workflow 完成事件写入 durable Outbox；UPMS Inbox 处理 started/rejected/completed，校验信封、来源、业务身份、操作者、轮次、请求摘要和合法状态。
- single 使用 `LocalDurableTransport`；cloud 仅在 `bixi.reliable.rabbit.enabled=true` 时创建 Rabbit owner endpoint；两侧通过限定 Bean 名称区分 owner 存储和投递属性。
- `bixi.reliable.enabled=false` 时不创建可靠 worker/存储，Workflow completion listener 回退到阶段一兼容通知器。
- Compose、Nacos 和 single 配置已暴露 `BIXI_RELIABLE_ENABLED`、`BIXI_RELIABLE_RABBIT_ENABLED` 及 Rabbit 连接参数；可靠配置默认关闭。

## 已验证

- `bixi-workflow-biz`、`bixi-upms-biz` 正常 annotation processing 编译通过；请假事件处理器单测通过。
- 真实 MySQL 工作流双模式集成：cloud/single 合计 `192/192` 通过；可靠消息原语 cloud/single 各 `55/55` 通过。
- 自动任务跨 owner 的真实 MySQL/Rabbit 业务闭环已由 2E 专用证据补充：Workflow → UPMS booking →
  UPMS 结果 → Workflow terminal → UPMS `APPROVED`，双方 Outbox/Inbox 分别收敛为 `DELIVERED`/`PROCESSED`，
  重复请求不产生第二条 booking；详见 [2E 证据](EVIDENCE-2E.md)。
- `make reliable-rabbit-test`：Rabbit confirm/正常路由、重复投递去重、mandatory return、损坏 wire quarantine、消费者停止后队列恢复共 `5/5` 通过。
- 同一脚本跨 JVM 执行 broker 重启恢复：seed `1/1`、broker `docker restart` 后新 JVM consume `1/1` 通过；重启期间宿主端口实际从 `32826` 重新分配为 `32827`，消费者使用新端口并将 Inbox 置为 `PROCESSED`。
- 隔离区数据库不可用时保持未 ack 的回归单测通过。
- `make architecture-check`、`make runtime-config-check` 和 `git diff --check` 通过。

## 未完成/阻塞

- 请假提交 -> Outbox -> Workflow Inbox -> Started/Completed -> UPMS 状态回写的真实 MySQL/Rabbit 自动任务链路
  已在 2E 专用测试中通过；本文件保留 2C 的可靠协作基础证据。
- Rabbit transport 协议与 broker 重启已验证；尚未完成应用进程重启、发送/消费提交窗口故障注入及双模业务恢复验收。
- 2E 已补充真实自动任务的 consumer 重启及发送成功后 `mark-delivered` 失败/租约重放窗口；本文件不将该单例
  故障注入扩大解释为完整应用进程重启或全量恢复报告。
- 尚未完成人工重试/隔离区查询/对账入口和统一恢复管理页。
- 当前工作树中既有 `DataScopeInterceptor` 对 MyBatis 代理读取 `mappedStatement` 的错误会使部分 Workflow 集成测试失败；该问题与本轮可靠链路改动无关，未在本轮扩大修复范围。
