# bixi-auth — OAuth 2.1 认证中心

基于 Spring Authorization Server 1.4.1 实现的 OAuth 2.1 认证服务，负责统一身份认证和令牌管理。

## 核心职责

- 用户登录认证（密码模式、手机号短信模式）
- JWT Access Token + Refresh Token 签发与刷新
- 图形验证码生成与校验
- 认证事件处理（登录成功/失败回调）

## 关键目录结构

```
bixi-auth/
├── src/main/java/com/lotus/bixi/auth/
│   ├── config/       # 认证服务器配置（AuthorizationServerConfig、SecurityConfig）
│   ├── endpoint/     # Token 端点、登录端点
│   ├── support/      # 自定义认证模式（密码模式、短信模式）、Token 生成器
│   └── BixiAuthApplication.java
├── src/main/resources/
│   ├── application.yml
│   ├── static/       # 静态资源
│   └── templates/    # 登录页模板
├── Dockerfile
└── pom.xml
```

## 依赖关系

- 依赖 `bixi-common-core`（核心工具）
- 依赖 `bixi-common-feign`（调用 UPMS 服务获取用户信息）
- 依赖 `bixi-common-security`（安全框架基础配置）
- 被 `bixi-gateway` 路由转发
