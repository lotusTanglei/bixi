# bixi-common-xss

XSS 跨站脚本攻击过滤模块，支持表单参数和 Jackson 反序列化两种过滤方式。

## 模块职责

- `BixiXssAutoConfiguration` 自动配置，注册 XSS 过滤组件
- `BixiXssProperties` 配置属性，支持自定义过滤规则和排除路径
- 表单参数过滤：`FormXssClean` + `XssCleanInterceptor` 拦截表单提交中的 XSS 内容
- Jackson 反序列化过滤：`JacksonXssClean` + `XssCleanDeserializer` 在 JSON 反序列化时清理 XSS
- `XssCleaner` 接口 + `DefaultXssCleaner` 默认实现，可扩展自定义清理策略
- `@XssCleanIgnore` 注解，标记不需要 XSS 过滤的字段或方法
- 异常体系：`XssException`、`FromXssException`、`JacksonXssException`

## 关键文件

| 文件 | 说明 |
|------|------|
| `BixiXssAutoConfiguration.java` | XSS 自动配置入口 |
| `config/BixiXssProperties.java` | XSS 配置属性 |
| `core/FormXssClean.java` | 表单 XSS 过滤 |
| `core/JacksonXssClean.java` | Jackson XSS 过滤 |
| `core/DefaultXssCleaner.java` | 默认 XSS 清理实现 |
| `core/XssCleanIgnore.java` | 忽略 XSS 过滤注解 |

## 包路径

`com.lotus.bixi.common.xss`
