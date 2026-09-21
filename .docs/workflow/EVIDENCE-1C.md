# 阶段一请假闭环实现与验证记录

日期：2026-09-21。状态：本批实现及四组基础运行验收完成；完整阶段结论见 [阶段一报告](EVIDENCE-STAGE1.md)。

## 本批实现

UPMS `demo/leave` 维护独立草稿、提交、分页、详情、审批人查询、状态回写与刷新；同步依赖唯一 `WorkflowService` 契约。提交保留三个独立事务边界，无法确认远程结果时保留 SUBMITTING，禁止盲目重启流程。

流程完成、拒绝和申请人取消在引擎事务提交后发送类型化结果；回调以独立事务验证业务键、申请人、轮次及已确认流程绑定。申请轮次持久化到工作流扩展表，不依赖可关闭的引擎历史记录。回调早于绑定时不认领未确认实例，提交协调器绑定后主动读取权威流程状态；重复终态结果幂等，矛盾终态拒绝。

共享前端新增请假表单、列表、详情及审批记录，修正工作流 API 前缀、实际字段与操作权限；待办支持通过、拒绝结束、转办、委派和交回。流程定义页面提供内置请假模型部署入口。菜单缓存按工作流启停状态隔离，关闭时隐藏流程与请假菜单/按钮。cloud 编排增加独立 Workflow 服务及有条件的优先网关路由。

## 已有验证

- 请假后端最终定向测试 cloud/single 各 27 项通过：真实 H2/MyBatis、并发提交与重复回调、REQUIRES_NEW 回写、HTTP 参数校验、权限、客户端字段保护、真实审计事件、安全审批人投影和适配器启停。
- `make workflow-test` 当前同时包括 `Workflow*Test,Leave*Test`，cloud 包括 Gateway，single 包括组合根；两次 BUILD SUCCESS。各含 34 项实际引擎审批测试及单独的无历史记录回写测试。
- `make frontend-ci`、`make architecture-check runtime-config-check` 通过。
- 已记录失败再修复：没有业务结果通知；历史关闭时静默丢通知；任务 DTO 缺少委派状态/办结时间；菜单跨开关缓存泄漏；定义接口使用旧权限且无部署入口；回调缺少审计。
- 结果通知和请假领域均经过规格、质量复审，无剩余发现。回调审计不把申请人冒充审批人：内部匿名调用保留系统操作者，审批人见引擎审批记录。
- 真实 MySQL 8.0.45 / REPEATABLE-READ 双模验证通过 116 项（每模式实际审批 34、无历史回写 1、请假领域 23）；H2 默认夹具另通过 23 项。测试数据库与容器已清理。
- 首次启用 single 暴露真实 DynamicTp 与 Flowable Spring 执行器契约不匹配；完整 Web 上下文先复现失败，再由工作流条件化适配器复用原线程池。9 项装配测试通过，验证引擎实际提交在 `Bixi-Async-` 线程执行、引擎关闭不拥有原池、关闭工作流不新增执行器。随后 Docker single 实际启动成功。
- single enabled 在 RabbitMQ 已停止、无 Nacos/Gateway/独立 Workflow 服务条件下通过统一 HTTP 验收；既有 Rabbit 探针/监听器通过单独覆盖配置关闭。通过/拒绝/取消、办理资格、越权查询、历史及自动回写均通过。随后 single disabled 核心验收、菜单隐藏、接口 404 通过；前后数据库请假状态分布及引擎历史数量相同。
- 最新完整后端门禁 cloud 139、single 137 项通过；原始日志 `/tmp/bixi-workflow-stage1-backend-cloud.log`、`/tmp/bixi-workflow-stage1-backend-single.log`。随后 cloud 错误适配仍有变更，需另补验证证据。
- 菜单迁移先复现自定义 ID 被误授权，再补同一事务中的身份预检和冲突报错。真实 MySQL 8.4.3 上 12 项检查通过，并经独立复核；夹具包含规范唯一约束、CHECK 和角色/菜单外键。
- 在隔离 MySQL 8.4.3 测试 schema，从基线 `f34c32f4d00d6bf8dc0f84d5cacfaabe96277772` 的旧流程表执行业务轮次迁移和请假新表迁移两次：旧 completed 实例保留、旧轮次初值 NULL、已设置轮次不覆盖、请假已有行保留，新建请假列类型/默认值/可空性与规范 SQL 一致。测试 schema 已删除。

原始本地日志：`/tmp/bixi-1c-workflow-full.log`、`/tmp/bixi-leave-final-cloud.log`、`/tmp/bixi-leave-final-single.log`、`/tmp/bixi-1c-frontend.log`、`/tmp/bixi-1c-static.log`。

## 运行确认和明确限制

四组基础启停均已通过；非法业务状态和远程权限错误映射已修复，真实 HTTP 复验通过。前端串单与陈旧请求已修复，16 项脚本回归、完整构建、桌面/手机真实浏览器检查通过。最新完整后端 cloud149项/single147项通过。最终记录和日志见 [阶段一报告](EVIDENCE-STAGE1.md)，迁移清单见 `sql/migrations/README.md`。

本批原始日志另见 `/tmp/bixi-workflow-leave-mysql-both.log`、`/tmp/bixi-leave-h2-fixture-final.log`、`/tmp/bixi-workflow-executor-red.log`、`/tmp/bixi-workflow-executor-final.log`、`/tmp/bixi-workflow-stage1-single-enabled.log`。

本阶段 AFTER_COMMIT 通知只覆盖正常运行闭环，失败记录日志并允许已绑定业务单主动刷新。没有持久化通知、提交命令、请求重试幂等或故障恢复保证；这些仍须阶段二实现与验收。阶段三多副本恢复、阶段四完整审批扩展均未完成。
