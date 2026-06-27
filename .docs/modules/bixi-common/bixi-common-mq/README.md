# bixi-common-mq

RabbitMQ 消息队列自动配置模块，为各业务模块提供统一的消息中间件基础设施。

## 模块职责

- 提供 RabbitMQ 自动配置（`RabbitMQAutoConfiguration`）
- 统一初始化 RabbitMQ 连接工厂、消息转换器等基础 Bean
- 各业务模块引入本模块即可直接使用 RabbitMQ 收发消息

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/RabbitMQAutoConfiguration.java` | RabbitMQ 自动配置入口 |

## 包路径

`com.lotus.bixi.common.mq`
