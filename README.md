<div align="center">

# Bixi

### 基于 Java 17 + Spring Boot 3 的企业级微服务 / 单体双模式开发脚手架

[![JDK](https://img.shields.io/badge/JDK-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.12-brightgreen.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

现代化 · 企业级 · 双模式部署 · 开箱即用

</div>

---

## 项目介绍

Bixi（碧玺）是一套面向企业后台和 SaaS 场景的前后端开发脚手架，后端基于 Java 17、Spring Boot 3.4、Spring Cloud Alibaba 构建，前端基于 Vue 3、TypeScript 和 Vite 构建。

项目支持两种部署形态：

- **微服务模式**：各业务服务独立运行，通过 Nacos 注册与配置，通过 Spring Cloud Gateway 统一入口。
- **单体模式**：认证、用户权限、代码生成和定时任务等模块组合为一个 `bixi-single` 应用，不依赖 Nacos 和 Gateway。

### 核心能力

- OAuth2.1 / JWT 认证授权与接口、菜单、按钮权限控制
- 用户、角色、菜单、部门、字典、参数和日志管理
- MyBatis-Plus、动态数据源和 Druid 数据库连接池
- RabbitMQ 消息、Redis 缓存、MinIO 对象存储
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
| 应用形态 | Gateway、Auth、UPMS、AI、Workflow、Quartz、Monitor 等服务独立部署 | `bixi-single` 组合为一个 Spring Boot 应用 |
| 注册与配置 | 使用 Nacos 服务发现和配置中心 | 配置文件本地加载，Nacos 运行时关闭 |
| API 入口 | Spring Cloud Gateway | 直接访问单体应用 |
| 服务调用 | OpenFeign + LoadBalancer | 不需要外部服务发现；保留公共调用组件依赖 |
| 限流与降级 | Gateway Redis 限流、Sentinel / Feign 降级 | 不启用 Gateway 层能力 |
| DynamicTp | 本地默认配置 + Nacos 动态刷新 | 本地配置 + 环境变量，修改后重启 |
| 外部基础设施 | MySQL、Redis、RabbitMQ、MinIO 等 | MySQL、Redis、RabbitMQ、MinIO 等 |

### 单体模式当前聚合范围

当前 [bixi-single/pom.xml](bixi-single/pom.xml) 直接聚合以下模块：

- `bixi-auth`：认证授权
- `bixi-upms-biz`：用户权限管理
- `bixi-generator`：代码生成
- `bixi-quartz`：定时任务

AI、Workflow 和 Monitor 在当前工程中作为独立模块存在，默认不在 `bixi-single` 的依赖列表中；需要按部署形态单独启动或按实际需求加入单体聚合。

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
| Flowable | 7.1.0 | 工作流引擎，独立 Workflow 模块使用 |
| Spring AI Alibaba | 1.0.0.2 | AI / DashScope 集成，独立 AI 模块使用 |
| SpringDoc OpenAPI | 2.7.0 | OpenAPI 接口文档 |
| Undertow | Spring Boot Starter | Web 容器 |

### 中间件清单

| 中间件 / 组件 | 主要用途 | 单体模式 | 微服务模式 |
|---|---|:---:|:---:|
| MySQL | 业务数据库 | ✅ | ✅ |
| Redis | 缓存、Token、验证码、限流 | ✅ | ✅ |
| RabbitMQ | 消息队列 | ✅ | ✅ |
| MinIO / S3 | 文件对象存储 | ✅ | ✅ |
| Druid | 数据库连接池、SQL 监控 | ✅ | ✅ |
| Dynamic Datasource | 动态数据源 | ✅ | ✅ |
| MyBatis-Plus | ORM | ✅ | ✅ |
| Spring Authorization Server + JWT | 登录、Token 签发与校验 | ✅ | ✅ |
| Quartz | 定时任务调度 | ✅ | ✅ |
| Nacos | 注册中心、配置中心 | 运行时关闭 | ✅ |
| Spring Cloud Gateway | 路由、全局过滤、网关限流 | — | ✅ |
| OpenFeign + LoadBalancer | 微服务间调用与负载均衡 | 按模块依赖 | ✅ |
| Sentinel | Feign 降级、熔断和限流 | 按模块依赖 | ✅ |
| Flowable | 工作流引擎 | 当前未聚合 | ✅ / 独立模块 |
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
│   ├── bixi-common-security      # OAuth2、JWT、安全组件
│   ├── bixi-common-swagger       # OpenAPI 文档
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

- Docker 24+
- Docker Compose v2
- GNU Make

只有本地源码构建与调试才需要 JDK 17、Maven 3.8、Node.js 18 和 npm 8。Compose 会自动准备 MySQL、Redis、RabbitMQ、Nacos 和应用镜像。

### 1. 默认微服务模式

```bash
git clone https://github.com/lotus-bixi/bixi.git
cd bixi
make init-env
make doctor
make start-cloud
make verify-cloud
```

首次执行 `make init-env` 会在忽略提交的 `.env` 中随机生成数据库、Redis、RabbitMQ、OAuth、Jasypt、前端密码加密和管理员密码。不要从 `.env.example` 手工复制固定密码。

启动完成后访问 <http://localhost:8080>，API 统一前缀为 <http://localhost:8080/api>。查看本地登录信息：

```bash
make credentials
```

`make verify-cloud` 会真实验证健康检查、登录、用户信息、菜单、示例任务四类权限、CRUD、非法请求和操作日志。

### 2. 切换单体模式

```bash
make start-single
make verify-single
```

两种模式使用同一 `http://localhost:8080` 入口和同一业务验收脚本。启动脚本会停止另一模式的应用容器；MySQL、Redis 和 RabbitMQ 数据保持不变。

### 3. 诊断与停止

```bash
make status
make diagnose
make logs
make stop
```

`make stop` 保留数据卷。`make reset` 会删除本地 Compose 数据卷，只应用于可丢弃的开发或 CI 环境。

### 4. 源码门禁

```bash
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
```

AI 开发约束、模块职责、常用命令和任务模板见 [AGENTS.md](AGENTS.md) 与 [.docs/5_AI_DEVELOPMENT.md](.docs/5_AI_DEVELOPMENT.md)。发布安全、升级回滚和已知限制见 [.docs/6_RELEASE_BASELINE.md](.docs/6_RELEASE_BASELINE.md)。

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

RabbitMQ 和 MinIO 也在该文件中配置，生产环境请通过环境变量注入账号、密码和 endpoint。

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
# 后端开发编译
make backend-dev

# 微服务 / 单体分别执行验证构建
make backend-cloud-ci
make backend-single-ci

# 前端开发 / 测试 / 生产构建
make frontend-dev
make frontend-test
make frontend-prod

# 前后端 CI 门禁
make ci-gate
```

---

## 功能模块

| 模块 | 主要能力 | 当前形态 |
|---|---|---|
| Auth | 登录、OAuth2 Token、验证码、客户端认证 | 单体 / 微服务 |
| UPMS | 用户、角色、菜单、部门、岗位、字典、参数、日志、文件 | 单体 / 微服务 |
| Demo Task | 独立业务表、CRUD、按钮权限、操作日志、统一验收 | 单体 / 微服务 |
| Generator | 数据库表导入、模板配置、代码生成、预览下载 | 单体 / 微服务 |
| Quartz | Cron 任务、执行记录、手动触发、暂停恢复 | 单体 / 微服务 |
| AI | 会话、消息、模型调用、SSE、知识库 | 独立 AI 服务 |
| Workflow | 流程定义、部署、实例、任务、表单和审批 | 独立 Workflow 服务 |
| Monitor | Spring Boot Admin 服务监控 | 独立 Monitor 服务 |

---

## 文档入口

- [架构说明](.docs/1_ARCHITECTURE.md)
- [数据库脚本说明](bixi-project-documents/sql/README.md)
- [数据字典](bixi-project-documents/sql/DATA_DICTIONARY.md)
- [环境变量模板](.env.example)
- [AI 辅助开发契约](AGENTS.md)
- [第一阶段发布基线](.docs/6_RELEASE_BASELINE.md)
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

---

## 常见问题

### 单体启动时报连接失败

单体模式不依赖 Nacos。优先运行 `make diagnose`，再检查 MySQL、Redis、RabbitMQ 以及 `.env` 中的连接配置。

### 微服务启动后 Gateway 找不到服务

检查 Nacos 是否可访问、服务是否注册成功、命名空间是否一致，以及各服务是否加载了正确的 Nacos 配置。

### 前端访问接口出现 404 或跨域

两种模式都应通过 `http://localhost:8080/api` 访问接口。运行 `make diagnose`，并检查当前前端容器是否与启动模式一致。

### DynamicTp 参数修改后没有生效

单体模式修改本地配置或环境变量后必须重启。微服务模式需要确认对应的 Nacos Data ID 已发布，并且包含完整的 `dynamictp.executors[0]` 配置对象。

---

## 许可证

[MIT License](LICENSE)

Copyright (c) 2025 Lotus Bixi Team
