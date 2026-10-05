# bixi-gateway — Spring Cloud Gateway 网关

cloud 模式的 API 网关和资源服务器边界。它负责路由、服务发现、入口鉴权、限流配置和 OpenAPI 聚合；single 模式不启用此模块。

## 核心职责

- 基于 Nacos 服务发现和 LoadBalancer 将 `/auth`、`/admin`、`/gen`、`/job`、`/ai` 等前缀路由到后端服务；Workflow 路由在 `workflow.enabled=true` 时装配。
- 通过 `GatewayReactiveOpaqueTokenIntrospector` 调用 Auth 的 `/token/check_token` 校验 opaque reference token；下游业务服务仍负责自身的权限注解和租户边界。
- 使用 Spring Cloud Gateway 的 Redis `RequestRateLimiter` 配置和多个 `KeyResolver` 提供 IP、用户、API 路径及租户维度的限流键。
- 动态聚合 Nacos 发现服务的 SpringDoc OpenAPI 地址。

## 过滤与安全边界

- `BixiRequestGlobalFilter` 只清理内部 `from` 请求头、写入请求开始时间并去除外层路径前缀；它不负责 token 或验证码校验。
- `GatewaySecurityConfiguration` 负责 opaque bearer token 鉴权，健康检查、OAuth 登录入口和通知回执等明确路径按配置放行。
- `GlobalExceptionHandler` 和限流异常处理器负责统一错误响应。

## 关键目录结构

```text
bixi-gateway/
├── src/main/java/com/lotus/bixi/gateway/
│   ├── config/       # 路由、限流、Workflow 路由和 SpringDoc 聚合
│   ├── filter/       # 请求清洗和示例限流过滤器
│   ├── handler/      # 全局异常和限流异常处理
│   ├── security/     # opaque token introspection 和 Gateway 安全链
│   └── BixiGatewayApplication.java
├── src/main/resources/
│   └── application.yml
└── pom.xml
```

## 配置入口

- `bixi.gateway.security.introspection-uri`：Auth token 校验地址，默认值仅适用于本地开发；Compose/cloud 应使用内部 Auth 服务地址。
- `bixi.gateway.security.request-timeout`：单次 token 校验超时，默认 3 秒。
- Nacos Data ID 中维护服务路由、限流规则和 SpringDoc 发现配置。

## 依赖关系

- 依赖 `bixi-common-core`、Spring Cloud Gateway、Nacos Discovery、Redis 限流和 Spring Security WebFlux。
- 由 cloud 前端和外部客户端访问；single 直接访问聚合应用，不经过 Gateway。

## 包路径

`com.lotus.bixi.gateway`
