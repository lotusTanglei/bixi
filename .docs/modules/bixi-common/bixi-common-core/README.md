# bixi-common-core

核心工具类模块，提供全局通用的基础设施：响应封装、常量定义、异常体系、配置类等。

## 模块职责

- `R` 统一响应封装与 `RetOps` 响应操作工具
- 常量定义：`CommonConstants`、`CacheConstants`、`SecurityConstants`、`ServiceNameConstants`
- 枚举：`DictTypeEnum`、`LoginTypeEnum`、`MenuTypeEnum`
- 异常体系：`BixiDeniedException`、`CheckedException`、`ValidateCodeException`、`ErrorCodes`
- 配置类：`JacksonConfiguration`、`RedisTemplateConfiguration`、`RestTemplateConfiguration`、`DynamicTpConfiguration`、`WebMvcConfiguration`
- 工具类：`RedisUtils`、`WebUtils`、`SpringContextHolder`、`MsgUtils`、`ClassUtils`

## 关键文件

| 文件 | 说明 |
|------|------|
| `util/R.java` | 统一响应体封装 |
| `util/RetOps.java` | 响应结果链式操作 |
| `util/RedisUtils.java` | Redis 操作工具 |
| `config/JacksonConfiguration.java` | Jackson 序列化配置 |
| `config/RedisTemplateConfiguration.java` | RedisTemplate 配置 |
| `config/DynamicTpConfiguration.java` | DynamicTp 全局异步线程池配置 |
| `constant/CommonConstants.java` | 全局通用常量 |
| `exception/ErrorCodes.java` | 错误码定义 |

## DynamicTp 全局异步线程池

所有部署都使用名为 `taskExecutor` 的原生 `DtpExecutor` 处理未指定执行器的
`@Async` 任务。Spring Boot 自带的静态任务执行器自动配置已统一排除，避免创建第二个
同名执行器。

默认参数如下：

| 参数 | 默认值 | 环境变量 |
|------|--------|----------|
| 核心线程数 | `2` | `BIXI_ASYNC_CORE_POOL_SIZE` |
| 最大线程数 | `8` | `BIXI_ASYNC_MAX_POOL_SIZE` |
| 队列容量 | `1024` | `BIXI_ASYNC_QUEUE_CAPACITY` |
| 队列类型 | `VariableLinkedBlockingQueue` | - |
| 拒绝策略 | `CallerRunsPolicy` | - |
| 优雅停机等待 | `60s` | - |

单机版从公共 `dynamic-tp-config.yml` 和本地环境变量读取配置，修改后需要重启。
微服务版额外从 Nacos 导入：

```text
${spring.application.name}-dtp-${profiles.active}.yml
```

例如 `bixi-auth-dtp-dev.yml` 可发布：

```yaml
dynamictp:
  executors:
    - threadPoolName: taskExecutor
      threadPoolAliasName: Bixi全局异步线程池
      executorType: common
      corePoolSize: 4
      maximumPoolSize: 16
      queueCapacity: 2048
      queueType: VariableLinkedBlockingQueue
      rejectedHandlerType: CallerRunsPolicy
      keepAliveTime: 60
      threadNamePrefix: Bixi-Async-
      allowCoreThreadTimeOut: false
      waitForTasksToCompleteOnShutdown: true
      awaitTerminationSeconds: 60
      notifyEnabled: false
```

Nacos 文件必须发布完整的 `dynamictp.executors[0]`，且 `threadPoolName` 固定为
`taskExecutor`。有效变更会原地刷新执行器；非法变更会被拒绝并保留上一版有效配置。

运行指标由 Micrometer 收集，可通过 `/actuator/dynamictp` 查询。当前只纳管全局
`@Async` 执行器，不纳管 Tomcat、Undertow 和 Quartz 线程池，也未配置通知告警。

## 包路径

`com.lotus.bixi.common.core`
