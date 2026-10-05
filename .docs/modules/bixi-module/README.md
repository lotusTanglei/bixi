# bixi-module — 业务模块聚合

所有业务功能模块的父级聚合工程，包含用户权限、AI 对话、工作流、代码生成、定时任务、监控等子模块。

## 子模块清单

| 子模块 | 职责说明 |
|:--|:--|
| `bixi-upms-api` | 用户权限管理跨模块契约、DTO、实体和查询服务接口；不包含业务实现 |
| `bixi-upms-biz` | 用户权限业务实现：用户、角色、菜单、部门、岗位、字典、参数、日志、文件、通知、租户和验收资源 |
| `bixi-ai-api` | AI 模块跨模块契约、DTO 和 VO |
| `bixi-ai-biz` | AI 业务实现：会话、消息、模型调用、知识库、SSE；由 `AI_ENABLED` 条件启用 |
| `bixi-workflow-api` | Workflow 跨模块契约、DTO、VO 和事件模型 |
| `bixi-workflow-biz` | Workflow 业务实现：流程、任务、表单、命令、可靠事件和恢复；由 `WORKFLOW_ENABLED` 条件启用 |
| `bixi-generator` | 数据库表导入、元数据和模板维护、预览、生成、下载；可由 `GENERATOR_ENABLED` 关闭 |
| `bixi-quartz` | 定时任务管理和执行记录 |
| `bixi-monitor` | Spring Boot Admin 服务监控 |

## 模式边界

- cloud 以独立应用运行各业务模块，并通过 Feign/API 契约跨模块调用。
- single 将 Auth、UPMS、Generator、Quartz、Workflow 和 AI 业务实现聚合到一个进程，复用同一 Controller、Service、Mapper 和前端实现；本地适配器替代 cloud 的远程调用。
- `*-api` 不依赖对应的 `*-biz`；业务实现只放在 `*-biz`，部署入口不复制业务代码。

## 直接运行制品

cloud 构建使用 `mvn -Pcloud clean package`，single 构建使用 `mvn -Psingle -pl bixi-single -am clean package`。cloud 制品和默认端口如下；每个 cloud JAR 都要先连接已发布 `deploy/nacos/*.yml` 的 Nacos：

| 制品 | 端口 | 用途 |
|---|---:|---|
| `bixi-module/bixi-upms-biz/target/bixi-upms-biz-exec.jar` | 4000 | UPMS 业务服务；使用 `-exec.jar` |
| `bixi-module/bixi-generator/target/bixi-generator.jar` | 5002 | 代码生成 |
| `bixi-module/bixi-quartz/target/bixi-quartz.jar` | 5007 | 定时任务 |
| `bixi-module/bixi-monitor/target/bixi-monitor.jar` | 5001 | Spring Boot Admin |
| `bixi-module/bixi-ai-biz/target/bixi-ai-biz.jar` | 5000 | `AI_ENABLED=true` 时启动 |
| `bixi-module/bixi-workflow-biz/target/bixi-workflow-biz.jar` | 5008 | `WORKFLOW_ENABLED=true` 时启动 |

认证和网关制品位于 `bixi-auth/target/bixi-auth.jar`（3000）和 `bixi-gateway/target/bixi-gateway.jar`（9999）。single 聚合制品和 `/admin` 上下文见 [`bixi-single` 说明](../bixi-single/README.md)；完整的数据库、Nacos、JAR 启动和前端静态部署步骤见根 [README](../../../README.md#直接打包部署不使用-docker)。

## 模块设计规范

- `*-api` 模块仅承载跨模块接口、DTO、VO、实体和事件契约，不放业务 Service 实现。
- `*-biz` 模块包含 Controller、Service、Mapper、Entity 和运行时适配器，是实际业务实现。
- Generator、Quartz、Monitor 是独立部署入口；Generator 业务实现也可由 single 聚合。
- 各业务模块按需引入 `bixi-common` 子模块，Workflow/AI 默认关闭时不装配对应公共自动配置。
