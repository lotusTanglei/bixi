# bixi-workflow-biz

工作流业务模块，基于 Flowable 引擎实现流程定义、部署、实例、任务、表单、权限等完整工作流功能。

## 模块职责

- 流程定义管理：`ProcessDefinitionController` + `ProcessDefinitionServiceImpl`
- 流程实例管理：`ProcessInstanceController` + `ProcessInstanceServiceImpl`
- 任务管理：`TaskController`，支持任务完成、驳回、转办、评论
- 表单管理：`FormController` + `FormServiceImpl`，表单定义 CRUD
- 表单数据：`FormDataController` + `FormDataServiceImpl`，表单数据存储
- 表单版本：`FormVersionController` + `FormVersionServiceImpl`
- 表单权限：`FormPermissionController` + `FormPermissionServiceImpl`，字段级权限控制
- 流程分类：`CategoryController` + `CategoryServiceImpl`
- 事件监听器：`TaskCreateListener`、`TaskCompleteListener`、`ProcessEndListener`、`GlobalEventListener`

## 关键文件

| 文件 | 说明 |
|------|------|
| `WorkflowApplication.java` | 工作流模块启动类 |
| `controller/ProcessInstanceController.java` | 流程实例控制器 |
| `controller/TaskController.java` | 任务管理控制器 |
| `controller/FormController.java` | 表单管理控制器 |
| `listener/TaskCreateListener.java` | 任务创建监听器 |
| `listener/ProcessEndListener.java` | 流程结束监听器 |

## 包路径

`com.lotus.bixi.workflow`
