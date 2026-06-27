# bixi-common-core

核心工具类模块，提供全局通用的基础设施：响应封装、常量定义、异常体系、配置类等。

## 模块职责

- `R` 统一响应封装与 `RetOps` 响应操作工具
- 常量定义：`CommonConstants`、`CacheConstants`、`SecurityConstants`、`ServiceNameConstants`
- 枚举：`DictTypeEnum`、`LoginTypeEnum`、`MenuTypeEnum`
- 异常体系：`BixiDeniedException`、`CheckedException`、`ValidateCodeException`、`ErrorCodes`
- 配置类：`JacksonConfiguration`、`RedisTemplateConfiguration`、`RestTemplateConfiguration`、`TaskExecutorConfiguration`、`WebMvcConfiguration`
- 工具类：`RedisUtils`、`WebUtils`、`SpringContextHolder`、`MsgUtils`、`ClassUtils`

## 关键文件

| 文件 | 说明 |
|------|------|
| `util/R.java` | 统一响应体封装 |
| `util/RetOps.java` | 响应结果链式操作 |
| `util/RedisUtils.java` | Redis 操作工具 |
| `config/JacksonConfiguration.java` | Jackson 序列化配置 |
| `config/RedisTemplateConfiguration.java` | RedisTemplate 配置 |
| `constant/CommonConstants.java` | 全局通用常量 |
| `exception/ErrorCodes.java` | 错误码定义 |

## 包路径

`com.lotus.bixi.common.core`
