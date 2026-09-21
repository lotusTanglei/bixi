# 6. 第一阶段发布基线

## 1. 基线范围

第一阶段基线版本为 `0.0.3`，默认入口为微服务模式，单体模式作为轻量部署选项。两种模式共享 UPMS 与 `demo/task` 示例业务实现。

基线包含：

- MySQL、Redis、RabbitMQ、Nacos、Gateway、Auth、UPMS 和 Vue 前端的 Compose 编排。
- Auth、UPMS、Generator、Quartz 聚合的单体应用编排。
- 随机本地凭据、健康检查、模式互斥切换、状态与诊断命令。
- 示例任务数据库、CRUD、菜单、四类按钮权限、操作日志、前端页面和双模黑盒验收。
- cloud/single 后端构建、前端构建、架构规则、运行配置和双模运行 CI 门禁。

## 2. 安全基线

- `.env` 首次由 `make init-env` 创建，数据库、Redis、RabbitMQ、OAuth、Jasypt、前端密码加密和管理员密码均随机生成。
- `.env`、本地 CodeGraph 数据、构建产物和运行卷不提交 Git。
- 容器使用非 root 用户运行应用，宿主端口默认只绑定 `127.0.0.1`。
- 管理员密码和 OAuth 客户端密钥在数据库首次初始化时由运行环境覆盖。
- Docker 构建参数只使用 `PUBLIC_*` 名称传递必须进入浏览器包的公开客户端配置；构建日志不得输出配置值。
- 发布环境必须通过外部 Secret 管理注入凭据，不得复用本地 `.env`。

## 3. 启动与验收

```bash
make init-env
make doctor
make start-cloud
make verify-cloud

make start-single
make verify-single
```

统一入口为 `http://localhost:8080`，API 前缀为 `http://localhost:8080/api`。`make credentials` 查看本地生成的登录信息，`make diagnose` 输出各端点可达性。

## 4. 升级与回滚

**全新环境**：Compose 按 `01_schema -> 02_data -> 03_constraints -> 04_indexes -> 05_runtime_secrets` 自动初始化。

**已有环境**：升级前备份 MySQL 和对象存储；对比 `bixi-project-documents/sql` 后增量执行新增表、菜单、角色关联与索引。初始化脚本含 `DROP TABLE`，禁止直接对已有数据库整体重放。

**回滚**：

1. 停止当前应用但保留数据卷：`make stop`。
2. 恢复上一版本镜像和配置。
3. 如果新版本发生数据写入，先恢复升级前数据库备份，再启动上一版本。
4. `biz_demo_task` 是独立示例表；只有确认无需保留示例数据时才可单独删除。

`make reset` 会删除 Compose 数据卷，仅用于可丢弃的本地或 CI 环境，不能作为生产回滚命令。

## 5. 已知限制

- 单体聚合 Auth、UPMS、Generator 和 Quartz，另包含默认关闭的 Workflow；AI、Monitor 仍是独立应用。工作流基础审批及四组启停已通过阶段一验收，可靠协作尚未完成，见 [工作流清单](workflow/PROGRESS.md)。
- Compose 默认闭环不启动 MinIO、AI、Workflow、Quartz 和 Monitor，第一阶段验收只覆盖认证、菜单、权限、UPMS 与示例任务。
- Nacos 在从 cloud 切换到 single 后可能继续运行，但 single 不依赖其服务发现或配置中心。
- 初始化 SQL 不是增量迁移工具；正式版本升级机制属于第二阶段工作。
- GitLab 双模验收需要支持 privileged Docker-in-Docker 的 Runner。
