# bixi-common-security

OAuth2 资源服务器安全模块，提供认证授权、权限校验、用户信息获取等安全基础设施。

## 模块职责

- `@EnableBixiResourceServer` 注解，一键启用 OAuth2 资源服务器
- `@Inner` 注解，标记内部调用接口免鉴权（`BixiSecurityInnerAspect` 切面拦截）
- `@HasPermission` / `@HasFormPermission` 注解，声明式权限校验
- `PermissionService` / `FormPermissionService` 权限判断服务
- `SecurityUtils` 工具类，获取当前登录用户信息
- `BixiUser` 用户主体，`BixiUserDetailsService` 用户加载服务
- OAuth2 令牌内省（`BixiCustomOpaqueTokenIntrospector`）
- Redis 存储授权信息（`BixiRedisOAuth2AuthorizationService`）
- Feign 调用 OAuth2 令牌传递（`BixiOAuthRequestInterceptor`）

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/Inner.java` | 内部调用免鉴权注解 |
| `annotation/HasPermission.java` | 权限校验注解 |
| `annotation/EnableBixiResourceServer.java` | 资源服务器启用注解 |
| `util/SecurityUtils.java` | 安全工具类 |
| `service/BixiUser.java` | 用户主体对象 |
| `component/BixiResourceServerAutoConfiguration.java` | 资源服务器自动配置 |
| `component/PermissionService.java` | 权限判断服务 |

## 包路径

`com.lotus.bixi.common.security`
