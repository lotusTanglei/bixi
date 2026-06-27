# bixi-workflow-api

工作流模块 API 层，定义 Feign 远程调用接口、数据传输对象和实体类。

## 模块职责

- DTO 数据传输对象：
  - 流程相关：`ProcessStartDTO`、`ProcessQueryDTO`
  - 任务相关：`TaskCompleteDTO`、`TaskRejectDTO`、`TaskTransferDTO`、`TaskCommentDTO`
  - 表单相关：`FormDTO`、`FormDataDTO`、`FormQueryDTO`、`FormVersionDTO`
  - 权限相关：`FormPermissionDTO`、`RoleFormPermissionDTO`
- VO 视图对象：`ProcessInstanceVO`、`FormVO`、`FormDataVO`、`FormVersionVO`、`FormRenderVO`、`FormFieldPermissionVO`、`RoleFormPermissionVO`、`ApprovalRecordVO`

## 关键文件

| 文件 | 说明 |
|------|------|
| `dto/ProcessStartDTO.java` | 流程启动请求 DTO |
| `dto/TaskCompleteDTO.java` | 任务完成请求 DTO |
| `dto/FormDTO.java` | 表单定义 DTO |
| `vo/ProcessInstanceVO.java` | 流程实例视图对象 |
| `vo/FormVO.java` | 表单视图对象 |
| `vo/ApprovalRecordVO.java` | 审批记录视图对象 |

## 包路径

`com.lotus.bixi.workflow.api`
