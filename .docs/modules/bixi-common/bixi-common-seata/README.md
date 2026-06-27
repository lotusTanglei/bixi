# bixi-common-seata

Seata 分布式事务自动配置模块，为微服务间的数据一致性提供支持。

## 模块职责

- 提供 Seata 自动配置（`SeataAutoConfiguration`）
- 统一初始化 Seata 分布式事务相关 Bean
- 各业务模块引入本模块即可使用 `@GlobalTransactional` 注解开启分布式事务

## 关键文件

| 文件 | 说明 |
|------|------|
| `SeataAutoConfiguration.java` | Seata 自动配置入口 |

## 包路径

`com.lotus.bixi.common.seata`
