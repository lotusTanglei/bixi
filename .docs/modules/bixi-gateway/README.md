# bixi-gateway — Spring Cloud Gateway 网关

基于 Spring Cloud Gateway 的 API 网关服务，作为微服务模式下所有外部请求的统一入口。

## 核心职责

- 请求路由转发与负载均衡（基于 Nacos 服务发现）
- 自定义限流（基于 Redis 的请求频率限制）
- 全局过滤器（Token 校验、请求头处理、黑名单拦截）
- SpringDoc API 文档聚合（汇总各微服务的 OpenAPI 文档）

## 关键目录结构

```
bixi-gateway/
├── src/main/java/com/lotus/bixi/gateway/
│   ├── config/       # 路由配置、SpringDoc 聚合配置、限流配置
│   ├── filter/       # 全局过滤器（验证码校验、请求装饰、Token 校验）
│   ├── handler/      # 验证码生成处理器、全局异常处理
│   └── BixiGatewayApplication.java
├── src/main/resources/
│   └── application.yml    # 路由规则、限流策略配置
├── Dockerfile
└── pom.xml
```

## 依赖关系

- 依赖 `bixi-common-core`（核心工具）
- 微服务模式下为必需组件，单体模式下不启用
- 通过 Nacos 发现并路由到 bixi-auth、bixi-upms-biz、bixi-ai-biz 等后端服务
