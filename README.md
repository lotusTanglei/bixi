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
| 构建方式 | `mvn clean package -Pcloud`，根项目默认激活 | `mvn clean package -Psingle` |
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

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 8+

运行时中间件：

- 两种模式都需要 MySQL、Redis；启用消息和文件功能时需要 RabbitMQ、MinIO。
- 微服务模式额外需要 Nacos。
- 单体模式不需要 Nacos 和 Gateway。

### 1. 获取项目

```bash
git clone https://github.com/lotus-bixi/bixi.git
cd bixi
```

### 2. 初始化数据库

单体配置默认使用 `bixi_single` 数据库；也可以通过 `MYSQL_DATABASE` 指定其他名称。

```bash
mysql -u root -p
CREATE DATABASE bixi_single CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
exit

mysql -u root -p bixi_single < bixi-project-documents/sql/01_init_all_tables.sql
mysql -u root -p bixi_single < bixi-project-documents/sql/04_init_data.sql

# 可选：增加约束和索引
mysql -u root -p bixi_single < bixi-project-documents/sql/02_add_constraints.sql
mysql -u root -p bixi_single < bixi-project-documents/sql/03_add_indexes.sql
```

`04_init_data.sql` 包含系统运行所需的基础数据。默认管理员账号通常为 `admin` / `admin123`，首次登录后请立即修改密码。

### 3. 配置环境变量

```bash
cp .env.example .env
```

根据部署模式填写数据库、Redis、RabbitMQ、MinIO、Nacos、短信和 AI 等配置。应用配置中的环境变量也可以直接由启动环境提供。

### 4. 启动单体模式

```bash
# 在项目根目录执行
mvn clean package -Psingle -DskipTests

java -jar bixi-single/target/bixi-single.jar
```

默认访问地址：<http://localhost:9999/admin>

单体模式会读取 `bixi-single/src/main/resources/application.yml` 和 `application-dev.yml`，并关闭 Nacos 服务发现和配置中心。

### 5. 启动微服务模式

```bash
# 编译微服务模式，cloud Profile 为根项目默认 Profile
mvn clean package -Pcloud -DskipTests
```

启动顺序建议：

1. 启动 Nacos，并准备各服务需要的配置。
2. 启动 `bixi-gateway`。
3. 启动 `bixi-auth`。
4. 启动 `bixi-module` 下需要的业务服务，例如 `bixi-upms-biz`、`bixi-ai-biz`、`bixi-workflow-biz`、`bixi-quartz`。
5. 按需启动 `bixi-monitor`。

开发时也可以在各模块目录执行：

```bash
mvn spring-boot:run
```

微服务应用会通过各自的 `application.yml` 从 Nacos 导入公共配置和服务配置。

### 6. 启动前端

```bash
cd bixi-ui
npm ci
npm run dev
```

前端开发服务器默认地址：<http://localhost:3000>

常用构建命令：

```bash
npm run build:dev
npm run build:test
npm run build:prod
```

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
          url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:bixi_single}
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

# 后端验证构建
make backend-ci

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
- [AI 辅助开发说明](CLAUDE.md)
- [许可证](LICENSE)

---

## 安全注意事项

生产部署前至少完成以下配置：

1. 修改默认管理员密码。
2. 使用强随机值替换 OAuth2、JWT、Jasypt 等密钥。
3. 不要使用示例中的 Redis、RabbitMQ、MinIO、Druid 默认密码。
4. 不要将真实 `.env`、Nacos 配置和密钥提交到 Git。
5. 通过 HTTPS、网络 ACL 和防火墙限制管理端口。
6. 按需限制 `/actuator`、`/druid` 和接口文档的访问权限。

---

## 常见问题

### 单体启动时报连接失败

单体模式不依赖 Nacos。优先检查 MySQL、Redis、RabbitMQ、MinIO 是否已启动，以及 `application-dev.yml` 或环境变量中的地址、端口、账号和密码是否正确。

### 微服务启动后 Gateway 找不到服务

检查 Nacos 是否可访问、服务是否注册成功、命名空间是否一致，以及各服务是否加载了正确的 Nacos 配置。

### 前端访问接口出现 404 或跨域

单体模式确认后端上下文路径为 `/admin`；微服务模式确认前端 API 地址指向 Gateway，并检查 Gateway 的路由和跨域配置。

### DynamicTp 参数修改后没有生效

单体模式修改本地配置或环境变量后必须重启。微服务模式需要确认对应的 Nacos Data ID 已发布，并且包含完整的 `dynamictp.executors[0]` 配置对象。

---

## 许可证

[MIT License](LICENSE)

Copyright (c) 2025 Lotus Bixi Team
