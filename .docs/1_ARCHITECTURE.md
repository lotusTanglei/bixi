# 1. 架构文档

## 1. 技术选型

**后端**

- 语言：Java 17
- 框架：Spring Boot 3.4.1 + Spring Cloud 2024.0.0 + Spring Cloud Alibaba 2023.0.3.2
- ORM：MyBatis-Plus 3.5.9，数据源管理使用 Druid 1.2.23，支持动态数据源（dynamic-datasource 4.3.1）
- 认证授权：Spring Authorization Server 1.4.1（OAuth 2.1 + JWT）
- 工作流引擎：Flowable 7.1.0
- AI 集成：Spring AI Alibaba 1.0.0.2
- API 文档：SpringDoc OpenAPI 2.7.0
- 工具库：Hutool 5.8.35、Fastjson2 2.0.53、Lombok 1.18.38
- 配置加密：Jasypt 3.0.5
- 监控：Spring Boot Admin 3.4.1 + Actuator
- 构建工具：Maven，使用 flatten-maven-plugin 统一 revision 版本管理
- 数据库：MySQL 8.0+

**前端**

- 框架：Vue 3 + TypeScript + Composition API（`<script setup>`）
- 构建工具：Vite
- 状态管理：Pinia
- UI 组件库：Element Plus
- CSS：Tailwind CSS + SCSS
- 国际化：vue-i18n
- 路由：Vue Router 4

项目支持双模部署：通过 Maven Profile 切换微服务模式（`-Pcloud`，默认）和单体模式（`-Psingle`）。微服务模式下使用 Nacos 作为注册中心和配置中心，Spring Cloud Gateway 作为 API 网关。当前单体模式聚合 Auth、UPMS、Generator 和 Quartz；AI、Workflow、Monitor 仍作为独立应用运行。

## 2. 分层架构

```
┌─────────────────────────────────────────────────────┐
│                    bixi-ui (Vue 3)                   │  前端层
├─────────────────────────────────────────────────────┤
│                  bixi-gateway (网关)                  │  网关层
├──────────┬──────────┬───────────┬───────────────────┤
│ bixi-auth│bixi-upms │ bixi-ai   │ bixi-workflow     │  业务服务层
│ (认证)   │ (用户权限)│ (AI 对话) │ (工作流)          │
├──────────┴──────────┴───────────┴───────────────────┤
│ bixi-module (业务模块聚合)                            │
│  ├─ *-api  (Feign 接口 + DTO)                        │
│  ├─ *-biz  (业务实现 + Controller)                   │
│  ├─ bixi-generator (代码生成器)                       │
│  ├─ bixi-monitor   (监控中心)                         │
│  └─ bixi-quartz    (定时任务)                         │
├─────────────────────────────────────────────────────┤
│ bixi-common (公共模块)                                │  基础设施层
│  ├─ bixi-common-core       (核心工具、常量、异常)      │
│  ├─ bixi-common-security   (安全框架封装)             │
│  ├─ bixi-common-mybatis    (ORM 封装)                │
│  ├─ bixi-common-datasource (数据源配置)               │
│  ├─ bixi-common-feign      (Feign 客户端封装)         │
│  ├─ bixi-common-swagger    (API 文档配置)             │
│  ├─ bixi-common-log        (日志切面)                 │
│  ├─ bixi-common-oss        (对象存储)                 │
│  ├─ bixi-common-mq         (消息队列)                 │
│  ├─ bixi-common-xss        (XSS 防护)                │
│  ├─ bixi-common-seata      (分布式事务)               │
│  ├─ bixi-common-ai         (AI 公共配置)              │
│  ├─ bixi-common-workflow   (工作流公共配置)            │
│  └─ bixi-common-bom        (依赖版本管理)             │
├─────────────────────────────────────────────────────┤
│ bixi-single (单体部署聚合模块，-Psingle 激活)          │  可选部署层
├─────────────────────────────────────────────────────┤
│ bixi-project-documents (SQL 脚本、部署文档)            │  项目文档
└─────────────────────────────────────────────────────┘
```

## 3. 模块职责映射

| 层级 | 核心文件/目录 | 职责说明 |
|:--|:--|:--|
| 前端层 | `bixi-ui/` | Vue 3 + TypeScript 前端应用，包含页面视图、路由、状态管理、API 调用 |
| 网关层 | `bixi-gateway/` | Spring Cloud Gateway 网关，负责路由转发、请求过滤、限流、SpringDoc 聚合 |
| 认证服务 | `bixi-auth/` | OAuth 2.1 认证中心，处理登录认证、令牌签发与刷新、验证码校验 |
| 业务模块 | `bixi-module/bixi-upms-api/` | 用户权限管理系统 Feign 接口定义和 DTO |
| 业务模块 | `bixi-module/bixi-upms-biz/` | 用户权限管理系统业务实现（用户、角色、菜单、部门、岗位、字典、参数、日志等） |
| 示例业务 | `bixi-module/bixi-upms-biz/.../demo/` | 独立示例任务域，提供两种模式共享的数据库、CRUD、权限和操作日志闭环 |
| 业务模块 | `bixi-module/bixi-ai-api/` | AI 模块 Feign 接口定义和 DTO |
| 业务模块 | `bixi-module/bixi-ai-biz/` | AI 对话业务实现（会话管理、消息处理、多模型调用、知识库、SSE 流式响应） |
| 业务模块 | `bixi-module/bixi-workflow-api/` | 工作流模块 Feign 接口定义和 DTO |
| 业务模块 | `bixi-module/bixi-workflow-biz/` | 工作流业务实现（流程定义、部署、任务审批、流程实例管理、表单关联） |
| 业务模块 | `bixi-module/bixi-generator/` | 代码生成器，根据数据库表结构自动生成 CRUD 代码 |
| 业务模块 | `bixi-module/bixi-quartz/` | 定时任务管理，基于 Quartz 的任务调度与执行记录 |
| 业务模块 | `bixi-module/bixi-monitor/` | Spring Boot Admin 监控中心，服务健康状态与缓存监控 |
| 基础设施层 | `bixi-common/bixi-common-core/` | 核心工具类、常量定义、统一异常处理、通用响应封装 |
| 基础设施层 | `bixi-common/bixi-common-security/` | Spring Security + OAuth2 资源服务器配置、权限注解封装 |
| 基础设施层 | `bixi-common/bixi-common-mybatis/` | MyBatis-Plus 配置、分页插件、基础 Mapper/Service 封装 |
| 基础设施层 | `bixi-common/bixi-common-datasource/` | 数据源自动配置 |
| 基础设施层 | `bixi-common/bixi-common-feign/` | OpenFeign 客户端配置、请求拦截器（传递认证头） |
| 基础设施层 | `bixi-common/bixi-common-swagger/` | SpringDoc OpenAPI 文档自动配置 |
| 基础设施层 | `bixi-common/bixi-common-log/` | 操作日志切面，通过注解记录操作日志 |
| 基础设施层 | `bixi-common/bixi-common-oss/` | 对象存储（兼容 AWS S3 协议）封装 |
| 基础设施层 | `bixi-common/bixi-common-mq/` | 消息队列公共配置 |
| 基础设施层 | `bixi-common/bixi-common-xss/` | XSS 防护过滤器和 JSON 反序列化清洗 |
| 基础设施层 | `bixi-common/bixi-common-seata/` | Seata 分布式事务配置 |
| 基础设施层 | `bixi-common/bixi-common-ai/` | Spring AI Alibaba 公共配置 |
| 基础设施层 | `bixi-common/bixi-common-workflow/` | Flowable 工作流公共配置和工具类 |
| 基础设施层 | `bixi-common/bixi-common-bom/` | Maven BOM，统一管理所有第三方依赖版本 |
| 单体部署 | `bixi-single/` | 单体模式聚合模块，通过 `-Psingle` Profile 激活；当前聚合 Auth、UPMS、Generator、Quartz，无需网关和注册中心 |
| 项目文档 | `bixi-project-documents/` | SQL 初始化脚本、数据字典文档、部署工具脚本 |

## 4. 双模边界与依赖规则

模块按以下职责划分：

| 类型 | 模块 | 依赖规则 |
| --- | --- | --- |
| 公共基础模块 | `bixi-common/*` | 提供跨业务基础能力，不得依赖 `bixi-module/*-biz` 或部署入口 |
| 业务 API | `bixi-module/*-api` | 保存跨模块契约、DTO 和远程适配定义，不包含业务实现 |
| 业务实现 | `bixi-module/*-biz`、`bixi-generator`、`bixi-quartz` | 保存唯一一套 Controller、Service、Mapper 和领域实现，可依赖业务 API 与公共模块 |
| 独立应用 | `bixi-gateway`、`bixi-auth`、各业务应用入口 | 在微服务模式独立启动，通过 Nacos、Gateway 和 Feign 协作 |
| 聚合应用 | `bixi-single` | 组合业务实现并提供单一进程入口，不复制业务代码 |

依赖方向必须保持为：部署入口 -> 业务实现 -> 业务 API / 公共模块。公共模块不能反向依赖业务实现，业务 API 不能依赖对应业务实现。

跨模块调用使用同一业务接口、两种适配器：微服务模式选择 Feign 远程适配器，单体模式选择 `@Primary` 本地实现。消费者只依赖 `*-api/service` 下的传输无关契约。Feign 自动配置仅在 `bixi.deployment.mode=cloud` 时启用，单体不注册 Feign 客户端，也不使用本机 HTTP 回环。

当前 UPMS 已固化六个传输无关契约：用户查询、客户端查询、字典查询、公共参数查询、操作日志和 Token 管理。`bixi-auth`、安全组件、日志组件及 UPMS Controller 均通过这些契约协作。

## 5. 核心执行流程

**微服务模式请求处理流程：**

1. 用户通过浏览器访问 `bixi-ui` 前端应用
2. 前端通过 `src/utils/request.ts` 封装的 Axios 实例发起 HTTP 请求，自动携带 JWT Token
3. 请求到达 `bixi-gateway` 网关，网关执行路由匹配、Token 校验等过滤逻辑
4. 网关将请求转发到对应的后端微服务（bixi-upms-biz、bixi-ai-biz、bixi-workflow-biz 等）
5. 微服务通过 `bixi-common-security` 解析 Token，提取用户信息，执行权限校验
6. Controller 接收请求，调用 Service 层处理业务逻辑
7. Service 层通过 MyBatis-Plus Mapper 操作数据库，必要时通过 Feign 调用其他微服务
8. 响应沿原路返回前端

**认证流程：**

1. 用户在登录页输入凭证，前端调用 `bixi-auth` 的 OAuth2 Token 端点
2. `bixi-auth` 验证凭证（支持密码模式、手机号模式），签发 JWT Access Token + Refresh Token
3. 前端将 Token 存储在 Session Storage，后续请求自动携带
4. Token 过期时，前端使用 Refresh Token 自动续期

**单体模式：**

激活 `-Psingle` Profile 后，`bixi-single` 将 Auth、UPMS、Generator 和 Quartz 聚合为单个 Spring Boot 应用，无需网关和注册中心。AI、Workflow 和 Monitor 当前不在单体聚合范围内。

## 6. 双模验证命令

```bash
make backend-cloud-ci
make backend-single-ci
make frontend-ci
```

这两条后端命令分别激活 `cloud` 和 `single` Profile，执行编译和单元测试。运行级验收使用同一脚本验证登录、用户信息、菜单、示例任务权限、CRUD、非法请求和操作日志：

```bash
make start-cloud && make verify-cloud
make start-single && make verify-single
```

GitLab CI 的 `accept_dual_mode` 作业按上述顺序验证两种模式。

## 7. ADR 快速索引

暂无 ADR 记录，请在 [4_DECISIONS.md](4_DECISIONS.md) 中添加。
