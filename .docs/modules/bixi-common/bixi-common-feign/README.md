# bixi-common-feign

OpenFeign 客户端配置模块，集成 Sentinel 熔断降级，提供服务间调用的统一基础设施。

## 模块职责

- `@EnableBixiFeignClients` 注解，统一扫描 Feign 客户端
- `@NoToken` 注解，标记无需携带 Token 的 Feign 调用
- 请求拦截器：`BixiFeignInnerRequestInterceptor`（内部调用标识）、`BixiFeignRequestCloseInterceptor`（连接关闭）
- Sentinel 集成：`BixiSentinelFeign`、`BixiSentinelInvocationHandler` 实现熔断降级
- 全局异常处理：`GlobalBizExceptionHandler`、`BixiUrlBlockHandler`
- 请求来源解析：`BixiHeaderRequestOriginParser`

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/EnableBixiFeignClients.java` | Feign 客户端启用注解 |
| `BixiFeignAutoConfiguration.java` | Feign 自动配置 |
| `sentinel/ext/BixiSentinelFeign.java` | Sentinel Feign 增强 |
| `sentinel/handle/GlobalBizExceptionHandler.java` | 全局业务异常处理 |
| `core/BixiFeignInnerRequestInterceptor.java` | 内部调用请求拦截器 |

## 包路径

`com.lotus.bixi.common.feign`
