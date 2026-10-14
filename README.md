<div align="center">

# Bixi

### 基于 Java 17 + Spring Boot 3 的企业级微服务 / 单体双模式开发脚手架

[![JDK](https://img.shields.io/badge/JDK-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.12-brightgreen.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Java 17 · Spring Boot 3 · Vue 3 · cloud / single 双模式

</div>

---

## 项目介绍

Bixi（碧玺）是一套面向企业后台和 SaaS 场景的前后端开发脚手架，后端基于 Java 17、Spring Boot 3.4、Spring Cloud Alibaba 构建，前端基于 Vue 3、TypeScript 和 Vite 构建。

项目支持两种部署形态：

- **微服务模式**：各业务服务独立运行，通过 Nacos 注册与配置，通过 Spring Cloud Gateway 统一入口。
- **单体模式**：认证、用户权限、代码生成和定时任务等模块组合为一个 `bixi-single` 应用，不依赖 Nacos 和 Gateway。

### 核心能力

- OAuth2.1 / opaque bearer token 认证授权与接口、菜单、按钮权限控制
- 用户、角色、菜单、部门、字典、参数和日志管理
- MyBatis-Plus、动态数据源和 Druid 数据库连接池
- RabbitMQ 消息、Redis 缓存、S3 / MinIO 对象存储适配
- Quartz 定时任务
- Flowable 工作流模块
- Spring AI Alibaba AI 模块
- Spring Boot Actuator / Admin 服务监控
- DynamicTp 全局异步线程池
- Vue 3 + Element Plus 管理后台

---

## 架构模式

| 对比项 | 微服务模式（`cloud`） | 单体模式（`single`） |
|---|---|---|
| 构建方式 | `mvn clean package -Pcloud`，根项目默认激活 | `mvn -Psingle -pl bixi-single -am clean package` |
| 应用形态 | Gateway、Auth、UPMS、Generator、Quartz、Monitor 独立部署；AI/Workflow 按开关启动 | `bixi-single` 组合为一个 Spring Boot 应用，并可条件装配共享 AI/Workflow 业务实现 |
| 注册与配置 | 使用 Nacos 服务发现和配置中心 | 配置文件本地加载，Nacos 运行时关闭 |
| API 入口 | Spring Cloud Gateway | 直接访问单体应用 |
| 服务调用 | OpenFeign + LoadBalancer | 不需要外部服务发现；保留公共调用组件依赖 |
| 限流与降级 | Gateway Redis 限流、Sentinel / Feign 降级 | 不启用 Gateway 层能力 |
| DynamicTp | 本地默认配置 + Nacos 动态刷新 | 本地配置 + 环境变量，修改后重启 |
| 运行时依赖 | MySQL、Redis、RabbitMQ、Nacos；MinIO/S3 需外部提供 | MySQL、Redis；默认不启动 RabbitMQ、Nacos 和 Gateway；MinIO/S3 需外部提供 |

### 单体模式当前聚合范围

当前 [bixi-single/pom.xml](bixi-single/pom.xml) 直接聚合以下模块：

- `bixi-auth`：认证授权
- `bixi-upms-biz`：用户权限管理
- `bixi-generator`：代码生成
- `bixi-quartz`：定时任务
- `bixi-workflow-biz`：工作流，默认关闭，通过 `workflow.enabled=true` 按需启用

`bixi-ai-biz` 在 cloud 中作为独立服务运行，是否启动由 `AI_ENABLED` 控制；single 复用同一业务实现，`AI_ENABLED` 默认值为 `false`，设置为 `true` 后按需装配。Monitor 仍保持独立运行。Workflow 在 single 中复用已聚合的同一业务实现，在 cloud 中作为独立服务运行；启停配置和当前交付边界见[工作流运行说明](.docs/workflow/OPERATIONS.md)。

---

## 技术栈与中间件

### 后端技术栈

| 技术 | 版本 / 实现 | 用途 |
|---|---|---|
| Java | 17 | 后端运行环境 |
| Spring Boot | 3.4.1 | 应用基础框架 |
| Spring Cloud | 2024.0.0 | 微服务基础能力 |
| Spring Cloud Alibaba | 2023.0.3.2 | Nacos、Sentinel 等集成 |
| Spring Authorization Server | 1.4.1 | OAuth2.1 授权服务器 |
| MyBatis-Plus | 3.5.9 | ORM 与分页能力 |
| Druid | 1.2.23 | 数据库连接池与监控 |
| Dynamic Datasource | 4.3.1 | 动态数据源切换 |
| Flowable | 7.1.0 | 工作流引擎，cloud/single 复用同一 Workflow 模块 |
| Spring AI Alibaba | 1.0.0.2 | AI / DashScope 集成，cloud 独立服务与 single 条件装配共用 |
| SpringDoc OpenAPI | 2.7.0 | OpenAPI 接口文档 |
| Undertow | Spring Boot Starter | Web 容器 |

### 中间件清单

| 中间件 / 组件 | 主要用途 | 单体模式 | 微服务模式 |
|---|---|:---:|:---:|
| MySQL | 业务数据库 | ✅ | ✅ |
| Redis | 缓存、Token、验证码、限流 | ✅ | ✅ |
| RabbitMQ | 消息队列；cloud 默认运行，single 仅在 Rabbit 适配或专项验证时使用 | 默认不运行 | ✅ |
| MinIO / S3 | 文件对象存储适配；Compose 不启动服务 | 外部可选 | 外部可选 |
| Druid | 数据库连接池、SQL 监控 | ✅ | ✅ |
| Dynamic Datasource | 动态数据源 | ✅ | ✅ |
| MyBatis-Plus | ORM | ✅ | ✅ |
| Spring Authorization Server + opaque reference token | 登录、Token 签发与 introspection 校验 | ✅ | ✅ |
| Quartz | 定时任务调度 | ✅ | ✅ |
| Nacos | 注册中心、配置中心 | 运行时关闭 | ✅ |
| Spring Cloud Gateway | 路由、全局过滤、网关限流 | — | ✅ |
| OpenFeign + LoadBalancer | 微服务间调用与负载均衡 | 按模块依赖 | ✅ |
| Sentinel | Feign 降级、熔断和限流 | 按模块依赖 | ✅ |
| Flowable | 工作流引擎 | 已聚合，默认关闭 | 独立服务，默认关闭 |
| Spring Boot Admin + Actuator | 服务监控与运行指标 | Actuator | ✅ / Monitor 服务 |
| DynamicTp | 全局 `@Async` 异步线程池 | 本地配置 | Nacos 可刷新 |

Seata、ShardingSphere 等目前仅存在版本管理或公共代码目录中，当前业务模块没有确认实际引入，因此不列为已启用中间件。

### 前端技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | 3.5.12 | 前端框架 |
| TypeScript | 5.6.3 | 类型系统 |
| Vite | 5.3.3 | 开发与构建工具 |
| Element Plus | 2.8.6 | UI 组件库 |
| Pinia | 2.2.6 | 状态管理 |
| Vue Router | 4.4.5 | 路由管理 |
| Tailwind CSS | 3.4.14 | 原子化 CSS |

---

## 项目结构

```text
bixi/
├── bixi-common/                  # 公共基础能力
│   ├── bixi-common-bom           # 依赖与版本管理
│   ├── bixi-common-core          # 核心工具、Redis、DynamicTp
│   ├── bixi-common-datasource    # 动态数据源
│   ├── bixi-common-feign         # Feign、Sentinel、负载均衡
│   ├── bixi-common-log           # 日志与审计能力
│   ├── bixi-common-mq            # RabbitMQ 封装
│   ├── bixi-common-mybatis       # MyBatis-Plus 封装
│   ├── bixi-common-oss           # S3 / MinIO 对象存储
│   ├── bixi-common-security      # OAuth2、opaque token、安全组件
│   ├── bixi-common-swagger       # OpenAPI 文档
│   ├── bixi-common-xss           # XSS 防护
│   ├── bixi-common-seata         # Seata 公共配置（可选）
│   ├── bixi-common-workflow      # Flowable 公共配置
│   └── bixi-common-ai            # AI 公共配置
├── bixi-gateway/                 # 微服务 API 网关
├── bixi-auth/                    # 认证授权服务
├── bixi-module/
│   ├── bixi-upms-api             # UPMS API、DTO
│   ├── bixi-upms-biz             # 用户权限管理
│   ├── bixi-generator            # 代码生成器
│   ├── bixi-quartz               # 定时任务
│   ├── bixi-monitor              # Spring Boot Admin 监控服务
│   ├── bixi-ai-api / bixi-ai-biz # AI API 与业务服务
│   └── bixi-workflow-api / biz   # Workflow API 与业务服务
├── bixi-single/                  # 单体模式入口
├── bixi-ui/                      # Vue 3 管理前端
├── bixi-project-documents/sql/   # 数据库脚本与数据字典
├── .env.example                  # 环境变量模板
├── Makefile                      # 常用构建命令
└── pom.xml                       # Maven 根配置
```

---

## 快速开始

### 环境要求

- MySQL 8.0.16+、Redis 7+；cloud 模式还需要 RabbitMQ 4+ 和 Nacos 2+
- Docker 24+、Docker Compose v2、GNU Make（仅 Compose 部署需要）
- JDK 17、Maven 3.8+、Node.js 22.18+、npm（打包部署或前端构建需要）

Compose 会按启动模式准备 MySQL、Redis、RabbitMQ、Nacos 和应用镜像，宿主机不要求安装 JDK、Maven 或 Node.js。直接打包部署时由外部服务提供这些依赖；MinIO/S3 在两种方式下都需要单独提供。

### 1. 默认微服务模式

```bash
git clone https://github.com/lotus-bixi/bixi.git
cd bixi
make init-env
make doctor
make start-cloud
make verify-cloud
make credentials
```

首次执行 `make init-env` 会在忽略提交的 `.env` 中随机生成数据库、Redis、RabbitMQ、OAuth、Jasypt、服务端旧版加密链和管理员密码。浏览器构建不使用这些密钥；不要从 `.env.example` 手工复制固定密码。

启动完成后访问 <http://localhost:8080>，API 统一前缀为 <http://localhost:8080/api>。`make credentials` 会打印本地管理员登录信息。

`make verify-cloud` 会真实验证健康检查、登录、用户信息、菜单、示例任务权限、CRUD、非法请求和操作日志；Workflow 默认关闭时还会检查对应菜单隐藏和接口不可用。`make verify-cloud` 和 `make verify-single` 需要宿主机安装 `redis-cli` 并能连接验收环境的 Redis，以写入一次性图片验证码测试数据；外部 Redis 可通过 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_DATABASE` 指定。

### 2. 切换单体模式

```bash
make start-single
make verify-single
```

两种模式使用同一 `http://localhost:8080` 入口和同一业务验收脚本。启动脚本会停止另一模式的应用容器；MySQL、Redis 和 RabbitMQ 数据保持不变。

single 只启动 MySQL、Redis、`bixi-single` 和 single 前端，不启动 Nacos、Gateway 或 RabbitMQ。Workflow 与 AI 默认关闭；启用 Workflow 时使用进程内适配器，RabbitMQ 仍可保持关闭。

### 生产 HTTPS 入口（Compose）

为域名准备受信任的证书，并将 `fullchain.pem` 和 `privkey.pem` 放在同一个宿主机目录中；挂载后这两个文件必须在容器内可读，不能是指向目录外的符号链接。在不提交的运行环境文件 `.env` 中设置：

```dotenv
BIXI_HTTPS_ENABLED=true
BIXI_TLS_DIR=/absolute/path/to/tls
BIXI_HTTPS_PORT=443
```

然后运行 `make start-cloud` 或 `make start-single`，按实际域名访问 `https://<域名>/`。这两个命令会启动对应模式的 HTTPS 入口，模式切换、关闭开关后再次启动以及 `make stop` 都会停止旧入口。外部只发布 HTTPS 端口；`BIXI_HTTP_PORT` 仍只绑定本机回环地址，供本机验收使用。启用前需确保域名解析指向服务器，并用真实域名验证证书链和登录回调。

冻结提交与镜像摘要的生产发行入口是 `make start-cloud-release` / `make start-single-release`，使用独立的 `.env.release` 清单，不在目标机重建镜像。制品准备、Compose 2.24+ 前提、空库首装与恢复限制见 [Compose 发行指南](deploy/compose-release.md)。上述普通 `start-*` 命令仍用于开发环境。

### 3. 诊断与停止

```bash
make status
make diagnose
make logs
make stop
make reset
```

`make diagnose` 会探测 cloud 和 single 的健康端点；未运行的模式显示不可达属于预期。`make stop` 保留数据卷，`make reset` 会删除本地 Compose 数据卷，只应用于可丢弃的开发或 CI 环境。

Ubuntu 22.04/24.04 LTS x86_64 的 cloud 与 single 原生部署，以 [原生发行指南](deploy/native/README.md) 为唯一操作入口。该指南涵盖生产构建与校验、`systemd`/Nginx 安装、cloud 的受认证 `bixi-prod` Nacos 配置、启动验收和回退；不要使用开发环境的 Nacos 配置或 Compose Nginx 文件替代发行包内容。

本次稳定版只支持**全新空库首装**。原生部署须使用发行包内的 `ops/init-db.sh`，它在导入前检查数据库为空；`01_schema.sql` 含有 `DROP TABLE`，绝不可在已有数据或部分导入的数据库上重放。真实 Ubuntu 安装与回退须通过发行门禁，不能仅凭文档视为已验收。

## 源码门禁

```bash
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
make generator-ci
make workflow-test
make ci-gate
```

`make generator-ci` 和 `make workflow-test` 分别验证代码生成器与 Workflow 重点链路。AI 开发约束、模块职责、常用命令和任务模板见 [AGENTS.md](AGENTS.md) 与 [.docs/5_AI_DEVELOPMENT.md](.docs/5_AI_DEVELOPMENT.md)。v1.0.0 的发行边界和验收状态分别见[发行规格](docs/superpowers/specs/2026-10-14-stable-release-design.md)与[发行验收总账](.docs/RELEASE_1_0_0_LEDGER.md)。

---

## 配置说明

### 单体模式基础配置

单体模式的关键配置位于 [application-dev.yml](bixi-single/src/main/resources/application-dev.yml)：

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          type: com.alibaba.druid.pool.DruidDataSource
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:bixi}
```

RabbitMQ 的连接变量由 Compose 和 cloud 配置传入；single 默认排除 Rabbit 自动配置。MinIO/S3 不由标准 Compose 启动，使用文件能力时请通过 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY` 连接外部服务。生产环境请通过环境变量注入账号、密码和 endpoint。

single 模式下 AI 默认关闭；需要 DashScope 能力时设置 `AI_ENABLED=true` 并提供 `DASHSCOPE_API_KEY`。

### 可选能力开关

| 能力 | 默认值 | cloud | single | 前置条件 |
|---|---|---|---|---|
| `AI_ENABLED` | `false` | 启动独立 AI 服务 | 条件装配同一 `bixi-ai-biz` 实现 | 开启时必须提供 `DASHSCOPE_API_KEY` |
| `WORKFLOW_ENABLED` | `false` | 启动独立 Workflow 服务 | 条件装配已聚合的 Workflow 实现 | 必须同时设置 `BIXI_RELIABLE_ENABLED=true` |
| `BIXI_RELIABLE_RABBIT_ENABLED` | `false` | Workflow 开启时必须为 `true` | 可保持 `false`，使用本地持久化适配 | 仅 cloud 使用 Rabbit 可靠适配 |
| `WORKFLOW_SCHEMA_UPDATE` | `false` | 仅可丢弃的新库可临时开启 | 仅可丢弃的新库可临时开启 | 开发期存量库使用受控迁移；v1.0.0 只支持空库首装 |

Workflow 开关修改后需要重启应用。v1.0.0 使用空库初始化；[增量迁移说明](bixi-project-documents/sql/migrations/README.md)及 `make phase2-migration-list`、`make phase2-schema-migrate` 仅供开发期已有数据库维护参考，不属于本版生产升级路径。

### 微服务模式 Nacos 配置

微服务模块的 `application.yml` 会导入以下类型的 Nacos Data ID：

```text
application-${profiles.active}.yml
${spring.application.name}-${profiles.active}.yml
${spring.application.name}-dtp-${profiles.active}.yml  # 可选，DynamicTp
```

常用服务包括：

- `bixi-gateway`
- `bixi-auth`
- `bixi-upms-biz`
- `bixi-ai-biz`
- `bixi-workflow-biz`
- `bixi-quartz`
- `bixi-monitor`

Nacos 地址默认由 `NACOS_HOST` 和 `NACOS_PORT` 提供，命名空间和账号由 Maven Profile / 部署环境决定。

### DynamicTp 全局异步线程池

DynamicTp 在单体和微服务模式下都会启用，默认执行器名称为 `taskExecutor`，用于未指定执行器的 `@Async` 任务。

默认配置：

| 参数 | 默认值 | 环境变量 |
|---|---:|---|
| 核心线程数 | `2` | `BIXI_ASYNC_CORE_POOL_SIZE` |
| 最大线程数 | `8` | `BIXI_ASYNC_MAX_POOL_SIZE` |
| 队列容量 | `1024` | `BIXI_ASYNC_QUEUE_CAPACITY` |
| 队列类型 | `VariableLinkedBlockingQueue` | — |
| 拒绝策略 | `CallerRunsPolicy` | — |
| 优雅停机等待 | `60s` | — |

- 单体模式：读取公共 [dynamic-tp-config.yml](bixi-common/bixi-common-core/src/main/resources/dynamic-tp-config.yml) 和环境变量，修改后重启。
- 微服务模式：在本地默认值基础上，可从对应的 Nacos Data ID 动态刷新，通常形如 `bixi-auth-dtp-dev.yml`。
- 可通过 `/actuator/dynamictp` 查看线程池信息，具体访问权限沿用 Actuator 安全配置。
- DynamicTp 当前只管理全局 `@Async` 执行器，不接管 Undertow 或 Quartz 自身的线程池。

---

## 常用构建命令

```bash
# 本地源码开发前置检查
make doctor-dev

# 后端开发编译
make backend-dev

# 微服务 / 单体分别执行验证构建
make backend-cloud-ci
make backend-single-ci

# 前端开发 / 测试 / 生产构建
make frontend-dev
make frontend-test
make frontend-prod

# 专项验证
make generator-ci
make workflow-test

# 前后端 CI 门禁
make ci-gate
```

---

## 功能模块

| 模块 | 主要能力 | 当前形态 |
|---|---|---|
| Auth | 登录、OAuth2 Token、验证码、客户端认证 | 单体 / 微服务 |
| UPMS | 用户、角色、菜单、部门、岗位、字典、参数、日志、文件 | 单体 / 微服务 |
| 验收资源 | 字典聚合、公共参数分页 CRUD、Excel 导入导出、按钮权限和操作日志 | 单体 / 微服务，共享同一业务实现 |
| Demo Task | 独立业务表、CRUD、按钮权限、操作日志、统一验收 | 单体 / 微服务 |
| Generator | 数据库表导入、模板配置、代码生成、预览下载 | 单体 / 微服务 |
| Quartz | Cron 任务、执行记录、手动触发、暂停恢复 | 单体 / 微服务 |
| Notice | 站内通知、SSE，以及可配置的 Email/Webhook 适配 | 单体 / 微服务；外部供应商需单独配置 |
| AI | 会话、消息、模型调用、SSE、知识库 | cloud 独立服务 / single 条件装配 |
| Workflow | 流程定义、部署、实例、任务、表单和审批 | 单体可选聚合 / 独立 Workflow 服务，默认关闭 |
| Monitor | Spring Boot Admin 服务监控 | 独立 Monitor 服务 |

---

## 文档入口

- [架构说明](.docs/1_ARCHITECTURE.md)
- [AI 开发约束](.docs/5_AI_DEVELOPMENT.md)
- [数据库脚本说明](bixi-project-documents/sql/README.md)
- [开发期数据库增量迁移说明（非 v1.0.0 升级路径）](bixi-project-documents/sql/migrations/README.md)
- [数据字典](bixi-project-documents/sql/DATA_DICTIONARY.md)
- [环境变量模板](.env.example)
- [AI 辅助开发契约](AGENTS.md)
- [历史第一阶段发布基线（0.0.3）](.docs/6_RELEASE_BASELINE.md)
- [Workflow 运行说明](.docs/workflow/OPERATIONS.md)
- [Workflow 进度与验证记录](.docs/workflow/PROGRESS.md)
- [许可证](LICENSE)

---

## 安全注意事项

生产部署前至少完成以下配置：

1. 使用外部 Secret 管理注入管理员、数据库、OAuth2、Jasypt 等凭据，不复用本地 `.env`。
2. 验证所有运行凭据均为强随机值，并建立轮换流程。
3. 不要在 Nacos、Compose、镜像构建参数或日志中写入生产密钥。
4. 不要将真实 `.env`、Nacos 配置和密钥提交到 Git。
5. 通过 HTTPS、网络 ACL 和防火墙限制管理端口。
6. 按需限制 `/actuator`、`/druid` 和接口文档的访问权限。
7. MinIO/S3 不由标准 Compose 管理，生产环境要单独配置 endpoint、访问密钥和最小权限。

---

## 当前限制

- v1.0.0 仅支持全新空库首装；非空库不得运行初始化脚本。开发期已有数据库的[增量迁移说明](bixi-project-documents/sql/migrations/README.md)仅供历史维护参考，不属于本版受支持的升级路径。
- AI、Email、Webhook、SMS 和 WeChat 的外部供应商能力需要对应配置；仓库内的构建或接口测试不等于第三方实际送达。
- Workflow 默认关闭。启用后必须满足可靠投递配置；模式切换和恢复应按 [Workflow 运行说明](.docs/workflow/OPERATIONS.md) 执行。
- 静态门禁和模块测试不能替代双模 HTTP 验收、外部中间件验证或生产级高可用演练，实际覆盖范围以[进度与验证记录](.docs/workflow/PROGRESS.md)为准。

---

## 常见问题

### 单体启动时报连接失败

单体模式不依赖 Nacos、Gateway 或 RabbitMQ。优先运行 `make diagnose`，再检查 MySQL、Redis 和 `.env` 中的连接配置；如果启用了外部 MinIO/S3，也检查对应 endpoint 和凭据。

### 微服务启动后 Gateway 找不到服务

检查 Nacos 是否可访问、服务是否注册成功、命名空间是否一致，以及各服务是否加载了正确的 Nacos 配置。

### 前端访问接口出现 404 或跨域

两种模式都应通过 `http://localhost:8080/api` 访问接口。运行 `make diagnose`，并检查当前前端容器是否与启动模式一致。

### DynamicTp 参数修改后没有生效

单体模式修改本地配置或环境变量后必须重启。微服务模式需要确认对应的 Nacos Data ID 已发布，并且包含完整的 `dynamictp.executors[0]` 配置对象。

### Workflow 或 AI 启动校验失败

Workflow 需要同时设置 `WORKFLOW_ENABLED=true` 和 `BIXI_RELIABLE_ENABLED=true`，cloud 还需要 `BIXI_RELIABLE_RABBIT_ENABLED=true`。AI 需要设置 `AI_ENABLED=true` 并提供 `DASHSCOPE_API_KEY`；不满足条件时保持功能关闭。

### `make diagnose` 显示部分服务不可达

该命令会同时探测 cloud 和 single 的端点。只启动一种模式时，另一种模式的端点显示不可达是预期结果；先用 `make status` 确认当前 Compose 服务集合。

---

## 许可证

[MIT License](LICENSE)

Copyright (c) 2025 Lotus Bixi Team
