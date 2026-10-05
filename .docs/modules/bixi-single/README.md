# bixi-single — 单体部署聚合模块

`bixi-single` 是 single 模式的组合根。Maven Profile `-Psingle` 将 Auth、UPMS、Generator、Quartz、Workflow 和 AI 的业务实现装入一个 Spring Boot 可执行 JAR；业务 Controller、Service、Mapper 和前端页面仍由各自共享模块提供。

## 聚合内容

| 依赖 | 运行边界 |
|------|----------|
| `bixi-auth` | OAuth2.1 认证和令牌端点 |
| `bixi-upms-biz` | 用户权限、通知、文件、验收资源等业务实现 |
| `bixi-generator` | 由 `GENERATOR_ENABLED` 控制，默认开启 |
| `bixi-quartz` | 定时任务业务实现 |
| `bixi-workflow-biz` | 由 `WORKFLOW_ENABLED` 控制，默认关闭，使用进程内适配器 |
| `bixi-ai-biz` | 由 `AI_ENABLED` 控制，默认关闭；开启时需要 `DASHSCOPE_API_KEY` |

## 运行边界

- `application.yml` 关闭 Nacos config/discovery，并排除 Spring Boot 的 RabbitMQ 自动配置；标准 single Compose 只启动 MySQL、Redis、single 和 single 前端。
- 应用进程监听 `9999`，上下文路径为 `/admin`。Compose 默认将宿主机 `${SINGLE_PORT:-9998}` 映射到容器 `9999`；健康检查地址为 `/admin/actuator/health`。
- Workflow 启用后必须同时设置 `BIXI_RELIABLE_ENABLED=true`；single 的可靠投递使用本地持久化适配，`BIXI_RELIABLE_RABBIT_ENABLED` 可保持关闭。
- `WORKFLOW_SCHEMA_UPDATE=true` 只适用于可丢弃的新库；存量数据库按 SQL 增量迁移说明执行。
- 开发配置将 S3 兼容对象存储设为可选外部服务（`file.oss.enable=true`）。Compose 不创建 MinIO，需通过 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY` 提供服务连接信息。

## 使用方式

```bash
# 构建单体模式
mvn -Psingle -pl bixi-single -am clean package

# 启动单体应用
java -jar bixi-single/target/bixi-single.jar
```

常用环境变量：`GENERATOR_ENABLED`、`WORKFLOW_ENABLED`、`AI_ENABLED`、`DASHSCOPE_API_KEY`、`BIXI_RELIABLE_ENABLED`、`WORKFLOW_SCHEMA_UPDATE`。配置修改后需要重启应用。

## 与 cloud 模式的区别

- cloud 由 Gateway、Auth、UPMS、Generator、Quartz 等独立应用组成，使用 Nacos 和 Feign；Workflow/AI 通过独立服务和开关运行。
- single 不运行 Gateway、Nacos 或 RabbitMQ，直接由一个应用提供 `/admin` 下的认证和业务接口；通知和 Workflow 的跨模块调用使用进程内适配器。

## 包路径

`com.lotus.bixi`
