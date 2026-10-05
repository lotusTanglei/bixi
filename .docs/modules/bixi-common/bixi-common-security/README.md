# bixi-common-security

OAuth2 资源服务器和请求安全基础设施，提供 opaque token 校验、权限注解、租户上下文和服务间认证传递。

## 模块职责

- `@EnableBixiResourceServer` 启用资源服务器；`BixiCustomOpaqueTokenIntrospector` 通过授权信息服务解析 reference/opaque bearer token。
- `@Inner` 标记内部调用接口，`@HasPermission` / `@HasFormPermission` 分别校验菜单按钮权限和 Workflow 表单权限。
- `TenantContextFilter` 根据认证用户、租户请求头和超级管理员范围设置 `TenantContextHolder`，并校验租户状态；Feign 拦截器只转发可信租户上下文。
- `EncryptionFilter` 在配置 `bixi.encrypt.key` 或 `security.encode-key` 后处理加密请求体/响应体；未配置密钥时不应发送加密请求。
- `SecurityUtils`、`BixiUser`、用户详情服务和 Redis OAuth2 授权服务提供当前用户、客户端和授权信息访问。

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/Inner.java` | 内部调用免鉴权注解 |
| `annotation/HasPermission.java` | 菜单/按钮权限注解 |
| `annotation/HasFormPermission.java` | 表单字段权限注解 |
| `annotation/EnableBixiResourceServer.java` | 资源服务器启用注解 |
| `component/BixiCustomOpaqueTokenIntrospector.java` | opaque token 解析 |
| `component/TenantContextFilter.java` | 租户上下文过滤器 |
| `filter/EncryptionFilter.java` | 可选请求加密过滤器 |
| `feign/BixiOAuthRequestInterceptor.java` | OAuth、租户上下文和内部头传递 |
| `util/SecurityUtils.java` | 安全工具类 |

## 包路径

`com.lotus.bixi.common.security`
