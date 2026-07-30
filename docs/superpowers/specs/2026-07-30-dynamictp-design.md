# DynamicTp 全局异步线程池设计

## 目标

使用 DynamicTp 替换项目现有的静态 `ThreadPoolTaskExecutor`，让所有部署统一使用原生
`DtpExecutor` 作为 Spring `@Async` 的默认执行器。

- 微服务模式通过 Nacos 在线调整线程池参数。
- 单机模式使用本地配置，不具备在线调参能力，但保留统一实现、运行指标和 Actuator
  查询能力。
- 第一阶段只纳管全局 `@Async` 执行器，不纳管 Tomcat、Undertow 或 Quartz 线程池。
- 第一阶段启用 Micrometer 和 Actuator，不配置通知告警。

## 技术基线

- Java 17
- Spring Boot 3.4.1
- Spring Cloud 2024.0.0
- Spring Cloud Alibaba 2023.0.3.2
- DynamicTp 1.2.2-x（Spring Boot 3 / Spring 6 版本）
- Nacos Cloud 配置中心

## 架构

项目直接采用 DynamicTp 的原生配置创建模式，不保留旧线程池实现或兼容配置。

1. 根 `pom.xml` 通过 `dynamic-tp-dependencies:1.2.2-x` BOM 管理版本。
2. `bixi-common-core` 引入 `dynamic-tp-spring-boot-starter-common`，使所有部署都具备
   DynamicTp 基础能力。
3. 根 POM 的 `cloud` Profile 引入
   `dynamic-tp-spring-cloud-starter-nacos`，仅微服务构建获得 Nacos 刷新能力。
4. 公共自动配置启用 DynamicTp 并加载本地 `dynamic-tp-config.yml`。
5. DynamicTp 从配置自动创建名为 `taskExecutor` 的原生 `DtpExecutor`。
6. Spring `@Async` 按标准默认执行器名称选中 `taskExecutor`，不增加包装器或业务侧执行器
   注解。
7. 公共核心模块通过 `AutoConfigurationImportFilter` 排除 Spring Boot 自带的静态
   `TaskExecutionAutoConfiguration`，避免它抢先注册同名 `taskExecutor`。
8. 删除现有 `TaskExecutorConfiguration` 和全部 `thread.pool.*` 配置。

`bixi-single` 通过 `single` Profile 构建，不激活默认的 `cloud` Profile，因此只有 Common
Starter，不包含 DynamicTp 的 Nacos Cloud Starter。

## 公共默认配置

公共资源 `dynamic-tp-config.yml` 提供以下配置。数值是跨环境的保守启动基线，不代表所有
工作负载的最优值；微服务部署应根据指标在线调整。

```yaml
dynamictp:
  enabled: true
  enabledCollect: true
  collectorTypes: micrometer
  monitorInterval: 5
  executors:
    - threadPoolName: taskExecutor
      threadPoolAliasName: Bixi全局异步线程池
      executorType: common
      corePoolSize: ${BIXI_ASYNC_CORE_POOL_SIZE:2}
      maximumPoolSize: ${BIXI_ASYNC_MAX_POOL_SIZE:8}
      queueCapacity: ${BIXI_ASYNC_QUEUE_CAPACITY:1024}
      queueType: VariableLinkedBlockingQueue
      rejectedHandlerType: CallerRunsPolicy
      keepAliveTime: 60
      threadNamePrefix: Bixi-Async-
      allowCoreThreadTimeOut: false
      waitForTasksToCompleteOnShutdown: true
      awaitTerminationSeconds: 60
      notifyEnabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,dynamictp
```

公共自动配置通过项目已有的 `YamlPropertySourceFactory` 导入该资源。应用本地配置、环境
变量和 Nacos Config Data 可以覆盖这些默认值。

Actuator Web Exposure 包含 `dynamictp` 端点，但访问控制继续沿用各应用现有的 Actuator
安全策略，不额外开放匿名访问。

## Nacos 配置

每个微服务在现有 `spring.config.import` 中增加：

```yaml
- optional:nacos:${spring.application.name}-dtp-@profiles.active@.yml
```

使用独立 Data ID 可让不同服务分别调整线程池参数。Data ID 缺失时，应用使用公共本地
默认配置继续启动。由于 Spring 配置绑定不会跨 Property Source 合并列表，Nacos 文件必须
包含完整的 `dynamictp.executors[0]` 对象，而不能只发布单个线程池字段；该对象的
`threadPoolName` 固定为 `taskExecutor`。

配置变化的数据流如下：

1. 应用启动时加载公共本地默认配置并创建 `taskExecutor`。
2. 微服务加载自己的 DTP Nacos Data ID，远程值覆盖本地默认值。
3. Nacos 发布变更事件。
4. DynamicTp 校验配置并原地更新现有执行器。
5. `taskExecutor` Bean 身份保持不变，后续 `@Async` 任务使用新参数。

单机版不加载 DTP Nacos Data ID。它可以通过环境变量或应用配置覆盖公共默认值，但修改
后需要重启生效。

## 运行与故障行为

- 本地启动配置非法时快速失败，不使用错误参数继续运行。
- DTP Nacos Data ID 缺失时使用公共本地配置。
- Nacos 刷新值非法时拒绝本次刷新、记录错误并保留上一次有效参数。
- 队列饱和时使用 `CallerRunsPolicy`，由提交线程执行任务以施加反压，不静默丢弃任务。
- 应用关闭时最多等待 60 秒处理已提交任务，超时后继续关闭。
- 第一阶段不配置通知平台、不启用任务超时中断，也不实现自动调参算法。

## 代码边界

### 根构建

- 修改 `pom.xml`：增加 DynamicTp 版本与 BOM。
- 修改 `cloud` Profile：增加 DynamicTp Nacos Cloud Starter。

### 公共核心模块

- 修改 `bixi-common/bixi-common-core/pom.xml`：增加 Common Starter。
- 删除
  `bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/config/TaskExecutorConfiguration.java`。
- 新增
  `bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/config/DynamicTpConfiguration.java`，
  只负责启用 DynamicTp 和导入公共 YAML。
- 新增
  `bixi-common/bixi-common-core/src/main/resources/dynamic-tp-config.yml`。
- 新增自动配置过滤器并通过 `META-INF/spring.factories` 注册，统一排除 Spring Boot
  静态任务执行器。
- 更新 Spring Boot 自动配置 imports，用新配置替代旧配置。

### 应用配置

以下微服务的 `application.yml` 增加可选的 DTP Nacos Data ID：

- `bixi-auth`
- `bixi-gateway`
- `bixi-module/bixi-ai-biz`
- `bixi-module/bixi-generator`
- `bixi-module/bixi-monitor`
- `bixi-module/bixi-quartz`
- `bixi-module/bixi-upms-biz`
- `bixi-module/bixi-workflow-biz`

`bixi-single` 不增加 Nacos 导入。

## 测试策略

使用测试驱动方式完成实现。

1. 自动配置测试验证上下文中只存在一个名为 `taskExecutor` 的原生 `DtpExecutor`。
2. 默认配置测试验证核心线程数为 2、最大线程数为 8、队列容量为 1024、队列类型为
   `VariableLinkedBlockingQueue`、拒绝策略为 `CallerRunsPolicy`，线程前缀为
   `Bixi-Async-`。
3. 配置覆盖测试验证三个 `BIXI_ASYNC_*` 环境变量能够覆盖默认容量参数。
4. `@Async` 集成测试验证未指定执行器名称的方法实际运行在 `Bixi-Async-` 线程中。
5. 动态刷新测试模拟有效配置变化，验证核心线程数、最大线程数和队列容量发生变化，同时
   Bean 实例保持不变。
6. 非法刷新测试验证非法参数不会替换上一次有效配置。
7. Maven 依赖验证确认 `single` Profile 不包含 DynamicTp Nacos Cloud Starter，而
   `cloud` Profile 包含该 Starter。
8. 完成 core 模块测试，并分别执行云模式和单机模式的构建验证。

## 验收标准

- 项目中不存在 `TaskExecutorConfiguration` 或 `thread.pool.*`。
- 所有部署均使用原生 `DtpExecutor` 作为默认 `@Async` 执行器。
- 单机模式无需 Nacos 即可启动并执行异步任务。
- 微服务模式修改对应 Nacos DTP Data ID 后，无需重启即可更新线程池参数。
- 动态修改队列容量有效，且不替换执行器 Bean。
- DynamicTp 指标可通过 Micrometer 收集，`dynamictp` Actuator 端点可按现有安全策略访问。
- Quartz 自身的 `SimpleThreadPool` 保持不变。
- 所有新增测试和云/单机构建验证通过。

## 非目标

- Tomcat、Undertow、Quartz 或其他中间件线程池适配。
- 企业微信、钉钉、飞书、邮件等通知渠道。
- 根据运行指标自动计算或自动调整线程池参数。
- 旧 `thread.pool.*` 配置兼容层。
