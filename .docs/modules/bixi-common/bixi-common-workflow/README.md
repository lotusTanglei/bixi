# bixi-common-workflow

Flowable 工作流公共配置模块，提供条件自动配置、运行属性、监听器基类和工具类。

## 模块职责

- `WorkflowAutoConfiguration` 仅在 `workflow.enabled=true` 时初始化 Flowable 相关公共 Bean。
- `WorkflowProperties` 绑定 `workflow.*`，包含引擎开关、数据库 schema 更新、历史级别、异步执行器和任务锁/恢复参数。
- `BaseExecutionListener`、`BaseTaskListener` 提供执行和任务监听器基类。
- `WorkflowUtils` 封装常用流程操作。

## 运行边界

- cloud 和 single 的 Workflow 默认关闭；启用条件由部署应用和 `bixi-workflow-biz` 共同决定。
- 本模块不负责跨服务可靠投递。outbox/inbox、Rabbit/local transport 和恢复接口由 Workflow/UPMS 业务模块配置。
- 已有数据库不应依赖应用自动建表，存量库按 `bixi-project-documents/sql/migrations/` 的受控迁移执行。

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/WorkflowAutoConfiguration.java` | Workflow 条件自动配置 |
| `config/WorkflowProperties.java` | `workflow.*` 属性配置 |
| `listener/BaseExecutionListener.java` | 执行监听器基类 |
| `listener/BaseTaskListener.java` | 任务监听器基类 |
| `util/WorkflowUtils.java` | 流程工具类 |

## 包路径

`com.lotus.bixi.common.workflow`
