# bixi-auth — OAuth 2.1 认证中心

基于 Spring Authorization Server 1.4.1 的认证服务，负责登录、OAuth2 授权端点、访问令牌管理和验证码流程。cloud 独立部署时使用 Nacos；single 由 `bixi-single` 聚合同一实现。

## 核心职责

- 密码和手机号等自定义资源所有者登录流程，以及登录成功/失败/退出事件处理。
- OAuth2 授权、令牌签发、刷新、注销、查询和内部 token 校验端点。
- 访问令牌使用 OAuth2 reference/opaque bearer token；Gateway 通过 Auth 的 `/token/check_token` 做远程 introspection，不按 JWT 本地解析。
- 图形验证码和密码参数加解密过滤；登录页面、授权确认页使用 FreeMarker 模板。

## 关键目录结构

```text
bixi-auth/
├── src/main/java/com/lotus/bixi/auth/
│   ├── config/       # AuthorizationServerConfiguration、WebSecurityConfiguration
│   ├── endpoint/     # BixiTokenEndpoint、ImageCodeEndpoint
│   ├── support/      # 密码/短信认证转换器、过滤器、事件处理和 token 生成器
│   └── BixiAuthApplication.java
├── src/main/resources/
│   ├── application.yml
│   ├── static/       # 静态资源
│   └── templates/    # FreeMarker 登录和确认页
└── pom.xml
```

## 关键端点

- `/token/login`、`/token/form`：登录页面和表单认证流程。
- `/oauth2/token`、`/oauth2/authorize`：OAuth2 授权服务器端点。
- `/token/check_token`：供 Gateway 内部校验 reference token 的端点，受内部调用约束。
- `/code/**`：验证码相关端点；`/token/logout` 和 `/token/remove/{token}` 用于令牌注销。

## 依赖关系

- 依赖 `bixi-upms-api` 查询用户、客户端和令牌相关契约，依赖 `bixi-common-security`、`bixi-common-feign` 和 `bixi-common-log`。
- cloud 模式由 `bixi-gateway` 路由并通过 Nacos 发现；single 模式直接由 `bixi-single` 提供认证端点。

## 包路径

`com.lotus.bixi.auth`
