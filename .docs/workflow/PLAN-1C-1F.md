# 阶段一后续：独立请假业务、页面与四组运行验收

状态：实施计划，未开发验收。前置为 1A/1B 审批基础，完整目标仍为 REQUEST.md 的四个阶段。普通实现选择已经获授权，不需要重复请求许可；不提交、推送或外部部署。

## 业务域与状态

在 UPMS 的 `demo/leave` 下维护唯一实现，依赖 workflow-api 的同步契约，不依赖 workflow-biz、Flowable 或 ACT 表。使用 `demo_leave_request`，业务状态独立命名 `leave_status`，避免混淆 BaseEntity.status。字段包含申请人、服务端验证的审批人、起止日期、原因、业务键、申请轮次、流程引用、提交/结束时间和审计字段。

第一阶段状态为 DRAFT → SUBMITTING → IN_REVIEW → APPROVED / REJECTED / CANCELED。只有申请人可编辑/删除草稿和提交；流程绑定后不能修改申请内容。日期范围和有效审批人在服务端校验；客户端不能写申请人、业务状态、流程引用或轮次。列表默认本人申请，详情仅本人或经工作流契约确认的参与者可读。使用 `demo_leave_view/add/edit/del`，读写同时验证操作权限与记录访问范围。

## 跨模块发起与结果回写

提交协调器不使用覆盖两个业务模块的大事务。先在请假本地事务中把草稿置为 SUBMITTING，随后调用 `WorkflowService.startProcess` 启动 `demo_leave_approval`，最后在新的请假本地事务中绑定流程引用并进入 IN_REVIEW。业务键和申请轮次由服务端生成，传入经过校验的 approverId。要处理同步结束通知先于发起响应的顺序，不能覆盖已经回写的终态。

结果使用 workflow-api 中的类型化结果 DTO 和接收契约；single 本地适配，cloud 复用既有内部 Feign 认证适配。UPMS 接收处理器验证业务键、轮次和流程绑定关系，在自己的事务中执行同一状态映射。Workflow 不包含请假表访问或业务实现。

第一阶段在工作流事务提交后发出结果通知；这只承担正常运行下的基础闭环，不作为可靠投递证据。明确记录通知失败，并提供已绑定流程的主动刷新/对账入口。对无法确定是否启动成功的远程失败，保留 SUBMITTING，不能盲目重复启动。阶段二必须把业务提交命令、结果事件与去重分别持久化，引入 Outbox/inbox 后复用同一业务状态处理器，再执行丢响应、杀进程、重投等故障验收；阶段一不能据此宣称故障可靠性。

## 页面与数据库

- 增加共享请假申请列表、草稿表单、详情和审批历史；复用现有 Workflow 待办/已办组件并修正实际 VO 字段和状态值。
- 完成申请、审批通过、拒绝、转办、委派/解决入口的权限、表单校验、加载/错误/空状态与小屏布局。UI 使用统一 `/admin/...` API 地址和同一权限值。
- 规范 SQL 增加请假表、查询索引、菜单/按钮/角色关联；同时提供已有库的增量脚本。不得改动 `.docs/.chiwen.state.json`。
- workflow.enabled 关闭时，UPMS 菜单与业务消费者一起禁用；更新权限缓存/菜单缓存的启停行为，避免返回失效页面入口。

## 运行编排

single 启用同一 workflow-biz，不新增独立 Workflow、Gateway、Nacos 或工作流 MQ 依赖。cloud 增加独立 Workflow 服务、Nacos 配置和有条件的网关路由，沿用现有认证和服务发现。网关上工作流专用路径应优先于通用 admin 转发。配置、诊断、启动及关闭入口复用 Make/Compose，保持默认关闭。

## 测试与验收

先以真实服务/数据库覆盖草稿权限和状态转换，再验证与真实引擎的发起/通过/拒绝/取消/业务回写。通过最小故障隔离替身替代外部传输时，不得 mock 被测业务实现。类型化 HTTP 契约和 single 本地装配必须分别验证。

在 `scripts/acceptance.mjs` 增加一套共享申请与审批断言，按模式选择连接。测试同一申请人的草稿、提交，另一个人的待办/审批，业务最终状态、历史、非法转换、越权读取/办理和持久化操作日志。接着运行：

1. single enabled：无独立 Workflow/Gateway/Nacos，完成同一审批闭环；明确现有其他模块的 MQ 边界。
2. cloud enabled：通过正常网关与服务链完成相同断言。
3. single disabled：核心验收通过，无引擎/执行器/工作流菜单，数据保留。
4. cloud disabled：核心验收通过，无失效 Workflow 路由/菜单和强依赖。

完成 architecture-check、runtime-config-check、双模后端 CI、frontend-ci、verify-single/cloud、CodeGraph 同步和 diff 检查。更新 PROGRESS、OPERATIONS、ADR 和阶段一报告；只有四组实际运行结果齐备后才勾选阶段一验收，然后继续阶段二至四。
