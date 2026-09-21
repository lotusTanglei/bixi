# 请假示例：阶段 1 边界

唯一业务实现位于 UPMS 的 `demo/leave`，仅依赖 `workflow-api`，由 `workflow.enabled` 开关控制。cloud 经 Feign 调用工作流，single 使用相同契约的本地适配器。申请人和审批人使用独立账户；审批人选项仅返回有效用户的 ID、姓名、用户名。

申请人只能编辑、删除、提交草稿。服务端生成申请人、业务键、轮次和初始状态，使用带状态条件的数据库更新防止重复提交。提交先独立提交 `DRAFT -> SUBMITTING`，再启动 `demo_leave_approval`，最后独立绑定流程 ID。外部调用没有业务数据库长事务。

工作流结果仅更新已确认绑定的申请，验证业务表、业务 ID、业务键、轮次、流程标识、流程 ID 和发起人。相同终态重复通知安全忽略，矛盾终态拒绝更新。提前到达的回调不能绑定流程；提交协调器绑定后重新查询流程，以捕获同步或快速结束。流程结果接收始终使用新事务，支持工作流事务提交后的本地调用。共享接收服务记录“回写请假审批结果”操作日志；匿名内部回调保留未知系统操作人，人工审批人以工作流审批记录为准。

HTTP 结果入口仅在 cloud 注册，使用现有 `@Inner` 校验并依赖网关清除外部 FROM 头；single 不注册该 HTTP 入口，直接调用本地接收器。cloud 后端内部端口沿用项目既有内网边界。

`completed / rejected / terminated` 分别映射为 `APPROVED / REJECTED / CANCELED`。申请人可查看自己的申请，其他人必须经工作流参与者校验后查看详情或历史。提交后可以显式刷新绑定流程的权威状态。

共享界面按实际调用组合权限：待办/已办列表需要 `workflow_task_view`；任务详情还需 `workflow_process_view`，流程详情中的请假日期和原因仅在具备 `demo_leave_view` 时读取。审批、拒绝、认领与交回委派使用 `workflow_task_edit`；转办/委派同时需要 `demo_leave_view` 访问本阶段复用的有效用户目录。组合按钮使用 `v-auth-all`，不会放宽后端权限。配置审批角色时，应按需授予上述查看权限。

对话框忽略关闭、切换申请或卸载后的旧请求结果、错误和加载回调；保存、审批和转办从表单校验开始阻止重复提交与关闭切换。`node scripts/test-workflow-ui.mjs` 使用现有 Vue/TypeScript 依赖执行真实组件脚本，通过受控异步响应检查跨申请串写、旧请求覆盖、保存锁和权限受限的详情加载；该回归不替代实际浏览器验收。

阶段 2 尚未实现：持久化 outbox/inbox、自动重试、结果投递恢复、异常提交的按业务键定位、自动对账与重提轮次。启动请求超时等结果不明时保留 `SUBMITTING`，禁止盲目重试；无确认绑定的申请需要人工核查。此阶段不能视为生产可靠投递保证。

新数据库使用规范初始化 SQL。已有数据库执行增量迁移 `bixi-project-documents/sql/migrations/20260921_demo_leave_request.sql`；迁移只新增表及索引，不删除已有数据。

验证：`LeaveRequestIntegrationTest` 使用实际服务、MyBatis、事务和规范 SQL 建立的 H2 数据库，仅工作流和用户外部契约使用替身。覆盖 CRUD、分页、可信身份、审批人过滤、提交竞争、事务边界、结果冲突与回调时序，以及实际 Controller 的校验、权限及日志切面。`LeaveAdapterConfigurationTest` 检查开关和模式装配；`WorkflowResultContractTest` 经真实测试 HTTP 连接验证结果 DTO 与内部请求头。双模运行验收由统一验收脚本执行；本页不以单模块测试代替运行验收。
