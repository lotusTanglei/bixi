# 通知收件人投递闭环证据

更新时间：2026-09-26。该记录覆盖 `sys_user_notice` 收件人级投递状态、管理重试和 provider-neutral HTTP 渠道适配器，不代表第三方厂商送达回执、SBA 监控或完整运行态验收已经完成。

## 已交付

- 收件人状态为 `PENDING`、`IN_FLIGHT`、`DELIVERED` 或 `FAILED`，并持久化尝试次数、最近一次尝试时间、送达时间和受限长度的失败原因。
- 领取使用条件更新；同一收件人不会被并发本地投递或 Rabbit 重复消费重复刷新。失败会落库后再抛出原异常，调用方可以看到失败而不会把失败伪装成成功。
- `IN_FLIGHT` 使用固定 5 分钟 lease。过期行可以在下一次通知重放或人工重试时重新领取；`delivery_attempts` 是完成/失败 CAS fence，旧进程不能覆盖被重新领取的 attempt。
- 管理记录接口支持 `deliveryStatus` 筛选并返回投递字段。通知管理页显示状态、attempts、失败原因和时间，已发布通知可以用 `sys_notice_send` 权限调用失败投递重试。
- single 选择本地投递，cloud 选择 Rabbit 投递；两种模式共享同一通知业务、Mapper、SQL 和前端契约。
- cloud Rabbit 消息携带显式 `tenantId`；消费者在处理期间安装该租户上下文并在成功或异常后恢复原上下文，缺少或非法租户的消息会被拒绝，避免在错误租户下读取通知。
- `IN_APP` 使用 SSE；`EMAIL`、`SMS`、`WECHAT` 和 `WEBHOOK` 均通过可配置的 HTTP JSON 适配器发送。适配器不绑定厂商 SDK，供应商返回非 2xx 或请求异常会进入同一 `FAILED`/CAS 重试状态机；短信使用手机号，微信使用用户 openid/小程序 openid。

## 实际验证

| 检查 | 结果 |
| --- | --- |
| `NoticeLocalDeliveryIntegrationTest` | 5/5；覆盖成功、失败持久化、已送达幂等、新鲜 `IN_FLIGHT` 不抢占、过期 `IN_FLIGHT` 恢复及旧 attempt fence |
| `NoticePublicationIntegrationTest` | 18/18；覆盖 cloud 发布、重复消息、失败重发、管理筛选和 retry endpoint |
| `PublishedNoticeNotifierTest` | 2/2；覆盖 after-commit 租户上下文和 attempt claim |
| `NoticeChannelDispatcherTest` | 12/12；覆盖 Email/Webhook/SMS/WeChat 本地 HTTP 202/204、503 失败、租户/地址校验和禁用语义 |
| `UpmsManagementPermissionTest` | 85/85；覆盖通知发送/重试权限和审计契约 |
| UPMS focused Maven reactor | 109 tests, 0 failures, 0 errors |
| `node --test scripts/test-notice-ui.mjs` | 1/1；覆盖 retry API、管理按钮/权限和记录字段契约 |
| `20260926_notice_delivery.sql` disposable MySQL check | 旧表升级成功，脚本重复执行成功，字段默认值和索引核对通过 |
| `make architecture-check`、`git diff --check`、`codegraph sync .` | 通过 |

## 明确边界

- lease 是固定窗口，不提供自动 `next_attempt_at`、指数退避、后台扫描器或永久失败上限；消息重放和人工操作负责再次触发。
- 旧进程在 lease 过期后可能已经把 SSE 写到客户端，因此恢复语义是至少一次；attempt fence 只保证旧进程不能错误覆盖新 attempt 的持久状态。
- HTTP 适配器和状态机使用本地 JDK HTTP 服务验证；真实短信/邮件/微信厂商的送达、回执、限频和渠道 fan-out 仍未验证，也没有伪造为已通过。
- 失败状态写入依赖通知生产路径在 SSE 调用时没有外层业务事务；Workflow Inbox 路径通过 `afterCommit` 回调执行。若未来从任意外层事务直接调用 notifier，应保持该边界或把失败 CAS 放入独立事务。
- cloud 通知消息的 `tenantId` 是必填契约；升级期间队列中残留的旧格式消息不会猜测租户，需由生产端重新发布。
- 本记录没有替代 `make verify-cloud`、`make verify-single` 的完整应用容器验收；Docker 运行态故障注入和恢复时延仍按现有阶段证据记录。
