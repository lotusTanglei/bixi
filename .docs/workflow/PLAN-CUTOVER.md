# 2B/2C 可靠提交切换协调

状态：2A 已验收；本文件记录接下来一次性切换的接口和编辑边界，不代表可靠运行闭环完成。沿用 `PLAN-STAGE2.md` 和 `PLAN-INBOX.md` 的事务及授权要求。

## 已稳定的基础

- `common-mq/reliable` 的 Outbox 已集成；Inbox 使用固定 target 和 `(source,type,schemaVersion)` 注册表。handler 成功和后续 Outbox 在 Inbox 处理事务内提交，失败状态独立持久化。
- `workflow-api/event` 已集成四类不可变生命周期事件和严格 codec。所有 payload 的 `requestHash` 指向原 UPMS 提交，actor 保留 UPMS 接受时的申请人快照。Completed 不要求独立终结命令 hash，以支持同步立即结束及未来自动完成。
- eventId、commandId、correlationId 使用标准小写 UUID。Started sequence=1，Completed sequence>1；StartRequested sequence=0。外层 DurableMessage 与内部事件所有路由、类型、版本和 eventId 必须完全一致。
- 域 codec 不证明生产者身份。本地配置和 Rabbit 服务账户负责来源认证；接收侧仍检查本地持久业务关联。

## 本批编辑边界

Workflow 侧负责可信启动 handler、REQUIRED 命令边界、唯一的启动领域路径、事务事件 recorder、原结束/reject/terminate 接入及真实 Flowable 测试。UPMS 侧负责持久提交命令、Started/Completed/Rejected handler、状态机和申请人查询。公共原语、共享初始化 SQL、运行装配和前端由集成阶段统一接入；不让两个实现批次覆盖同一文件。

两侧先在隔离副本准备，通过独立审查后一次切换。运行环境在此之前保持已验收的 2A 镜像；不得在可靠接收器就绪前移除旧回写，也不得在切换后长期保留双投递或同步兜底。

## Workflow 存储与事务

候选新增扩展字段为 `business_owner`、`event_sequence`、`start_context_json`；已有 `start_request_id` 保存可信 commandId。start_context 保存原始受信事件，供没有请求线程身份的完成监听器重建稳定关联、申请人及原提交 hash。实现若采用等效更小存储，须在证据中记录。

公开 START 只允许服务端 `workflow.public-start-models` 白名单中的未绑定模型，默认空；保留请假模型和业务绑定字段/变量不能从公共入口进入。内部 handler 只接受已认证 UPMS 的固定请假指令，在当前 Inbox 事务执行命令、Flowable、扩展及 Outbox，禁止调用公开安全 facade 或同步 REQUIRES_NEW 代理。

生命周期 eventId 从已持久实例/事件种类确定派生，时间和内容来自持久记录；不可在重试时改变 UUID 或 occurredAt。同步立即结束先写扩展及 Started，再写 Completed；普通完成、拒绝和终止同样在引擎事务内写终态事件。unbound 公共流程不向 UPMS 发布业务回写。

历史 owner/context 保持空，不能根据 Workflow 自报 businessId 猜归属。仅从 UPMS 已确认的 processInstanceId 和完整身份核对后建立可信绑定；有歧义的 SUBMITTING/孤立实例留待离线对账。业务轮次唯一约束只在公共伪造入口封闭和历史核对后启用。

## UPMS 对外契约

`POST /demo/leave/{id}/submit` 接收必填 `requestId`，服务器锁定并验证本人草稿、审批人和内容后，在同一事务保存 SUBMITTING、业务命令及 StartRequested Outbox。响应表达持久化接受，不表达流程已经启动。

候选响应 `LeaveSubmissionVO(commandId,requestId,leaveId,round,status)`，status 首次为 ACCEPTED；按原 requestId 重放返回相同接受结果。另提供本人命令查询，显示 ACCEPTED/STARTED/REJECTED、processInstanceId 和安全错误码。不同资源复用 requestId 明确冲突；不同 requestId 争抢同申请轮次不产生第二条命令或第二轮。

Started/Completed 接收必须核对本地命令、原 requestHash、申请人、完整业务身份/轮次、实例和序号。Completed 先到可绑定并直接进入终态，迟到 Started 为合法 IGNORED；旧轮次忽略，未来轮次或矛盾终态留下失败证据。不能把当前未绑定当作已消费成功。

## 前端与运行切换

提交前保存按账号/申请隔离的原 UUID，响应不确定时提供原请求重试和查询；退出/登录后仍保留本浏览器 session 内的原意图。已接受后明确显示处理中，有界轮询申请/命令；超时停止自动轮询并保留手动刷新。实际失败状态与审批拒绝区分。

single 构造相同两个 owner 的本地 transport/Inbox handler，源投递在原事务外；cloud 使用专用 Rabbit vhost、限权账户、publisher confirm/return 和消费提交后的确认。功能开关同时控制所有 worker/listener，禁用时不得消费或确认积压。最终切换前验证实际不同 DataSource/schema 的 owner 事务边界和无 SecurityContext 的回写。
