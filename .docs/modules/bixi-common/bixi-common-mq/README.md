# bixi-common-mq

消息基础设施模块。它同时提供 Spring AMQP 的公共消息转换器和 JDBC 可靠投递原语；具体业务模块负责决定何时装配 Rabbit 或本地传输。

## 模块职责

- `RabbitMQAutoConfiguration` 注册 Jackson JSON `MessageConverter`，Rabbit 连接和监听由 Spring Boot AMQP 自动配置提供。
- `IdempotencyAutoConfiguration` 在存在单一数据源和事务管理器时提供 JDBC 幂等存储、执行器和切面。
- 提供 outbox/inbox/quarantine 存储、幂等、租约、重试和恢复所需的 `ReliableDeliveryWorker`、`OutboxDispatcher`、`InboxExecutor` 等组件。
- 提供 `LocalDurableTransport` 和 `RabbitDurableTransport` 两种传输实现；业务模块通过条件配置选择其中一种。

## 双模式边界

- cloud 的通知和 Workflow 可靠链路可以使用 RabbitMQ；需要配置 `spring.rabbitmq.*` 以及对应的 `bixi.reliable.rabbit.*`。
- single 的应用配置排除 RabbitMQ 自动配置，标准 Compose 不启动 RabbitMQ；single 的通知和 Workflow 可靠链路使用本地持久化传输。
- 可靠投递组件只在所属业务模块启用 `bixi.reliable.enabled=true` 后装配，模块本身不会自动开启业务可靠链路。

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/RabbitMQAutoConfiguration.java` | Jackson JSON 消息转换器配置 |
| `config/IdempotencyAutoConfiguration.java` | JDBC 幂等组件的条件自动配置 |
| `reliable/JdbcOutboxStore.java` | 事务 outbox 存储 |
| `reliable/JdbcInboxStore.java` | 幂等 inbox 存储 |
| `reliable/JdbcQuarantineStore.java` | 失败消息隔离存储 |
| `reliable/LocalDurableTransport.java` | single 进程内持久化传输 |
| `reliable/RabbitDurableTransport.java` | cloud Rabbit 路由原语 |

## 包路径

`com.lotus.bixi.common.mq`
