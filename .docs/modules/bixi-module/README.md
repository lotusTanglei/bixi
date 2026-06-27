# bixi-module — 业务模块聚合

所有业务功能模块的父级聚合工程，包含用户权限、AI 对话、工作流、代码生成、定时任务、监控等子模块。

## 子模块清单

| 子模块 | 职责说明 |
|:--|:--|
| `bixi-upms-api` | 用户权限管理系统 Feign 接口定义和 DTO，供其他服务远程调用 |
| `bixi-upms-biz` | 用户权限管理系统业务实现：用户、角色、菜单、部门、岗位、字典、参数、日志、文件、通知 |
| `bixi-ai-api` | AI 模块 Feign 接口定义和 DTO |
| `bixi-ai-biz` | AI 对话业务实现：会话管理、消息交互、多模型调用、知识库、SSE 流式响应 |
| `bixi-workflow-api` | 工作流模块 Feign 接口定义和 DTO |
| `bixi-workflow-biz` | 工作流业务实现：流程定义、部署、实例管理、任务审批、表单关联 |
| `bixi-generator` | 代码生成器：根据数据库表结构自动生成 CRUD 代码 |
| `bixi-quartz` | 定时任务管理：基于 Quartz 的任务调度、执行记录 |
| `bixi-monitor` | 系统监控：基于 Spring Boot Admin 的服务健康监控 |

## 模块设计规范

- `*-api` 模块仅包含 Feign 接口、DTO、枚举，不包含业务逻辑
- `*-biz` 模块包含 Controller、Service、Mapper、Entity，是实际的可运行服务
- 独立模块（generator、quartz、monitor）为自包含的 Spring Boot 应用
- 各业务模块按需引入 `bixi-common` 子模块
