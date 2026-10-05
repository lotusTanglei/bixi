# bixi-workflow-biz

Workflow 业务实现模块，基于 Flowable 提供流程定义、实例、任务、表单和字段权限能力，并承载跨模块业务事件与可靠投递实现。cloud 模式由 `WorkflowApplication` 作为独立服务启动；single 模式复用同一业务实现并由 `bixi-single` 聚合。

## 启用条件

Workflow 的引擎和业务 Bean 使用 `workflow.enabled` 条件装配，当前 cloud 和 single 配置默认值均为 `false`。启用可靠协作和恢复时还要设置 `bixi.reliable.enabled=true`；cloud 的 Rabbit 传输还需要 `bixi.reliable.rabbit.enabled=true`，single 使用进程内本地传输。已有数据库先按 [增量迁移说明](../../../../bixi-project-documents/sql/migrations/README.md) 完成表结构迁移，避免依赖应用启动时自动建表。

## 模块职责

- 流程定义、部署和查询：`ProcessDefinitionController`、`ProcessDefinitionServiceImpl`
- 流程实例和审批记录：`ProcessInstanceController`、`ProcessInstanceServiceImpl`、`ApprovalRecordServiceImpl`
- 任务操作：`TaskController`、`WfTaskServiceImpl`，支持完成、驳回、转办和评论
- 表单能力：`FormController`、`FormDataController`、`FormVersionController`，提供定义、数据和版本管理
- 表单权限和分类：`FormPermissionController`、`CategoryController` 及对应服务
- 幂等命令与可信启动：`WorkflowCommandController`、`WorkflowCommandExecutor`、`WorkflowRequestHasher`
- 可靠事件和恢复：Workflow/业务任务事件发布、outbox/inbox/quarantine 存储、`WorkflowRecoveryController` 及恢复审计
- Flowable 监听：`TaskCreateListener`、`TaskCompleteListener`、`ProcessEndListener`、`GlobalEventListener`

## 关键文件

| 文件 | 作用 |
|---|---|
| `WorkflowApplication.java` | cloud 部署入口，注册发现并启用 Workflow 条件装配 |
| `controller/ProcessDefinitionController.java` | 流程定义和部署接口 |
| `controller/ProcessInstanceController.java` | 流程实例接口 |
| `controller/TaskController.java` | 任务审批接口 |
| `controller/FormController.java` | 表单定义接口 |
| `controller/WorkflowCommandController.java` | 幂等命令和可信启动入口 |
| `controller/WorkflowRecoveryController.java` | 可靠投递恢复管理；仅在 Workflow 和可靠投递同时启用时存在 |
| `config/WorkflowReliableConfiguration.java` | outbox/inbox、传输和恢复组件装配 |
| `service/local/LocalWorkflowService.java` | single 模式的本地 Workflow 服务适配 |
| `listener/TaskCreateListener.java` | 任务创建事件处理 |
| `listener/ProcessEndListener.java` | 流程结束事件处理 |

## 运行配置

| 配置 | 默认值 | 说明 |
|---|---:|---|
| `WORKFLOW_ENABLED` | `false` | 通过 `workflow.enabled` 开关 Workflow 业务和引擎 |
| `BIXI_RELIABLE_ENABLED` | `false` | 通过 `bixi.reliable.enabled` 开关可靠投递组件 |
| `BIXI_RELIABLE_RABBIT_ENABLED` | `false` | cloud 使用 Rabbit 传输时开启；single 保持关闭并使用本地传输 |
| `WORKFLOW_SCHEMA_UPDATE` | `false` | `workflow.database-schema-update`；存量库应使用受控迁移 |

## 验证与迁移

Workflow 的运行说明、模式切换和恢复操作见 [Workflow 运行说明](../../../workflow/OPERATIONS.md)。Flowable 7.1.0 表结构、Workflow 菜单、幂等命令、可靠投递和租户迁移均在 `bixi-project-documents/sql/migrations/` 中维护。

## 包路径

`com.lotus.bixi.workflow`
