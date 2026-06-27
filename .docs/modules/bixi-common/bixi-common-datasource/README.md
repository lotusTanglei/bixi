# bixi-common-datasource

动态数据源自动配置模块，支持运行时切换数据源，适用于多租户或多数据库场景。

## 模块职责

- 提供 `@EnableDynamicDataSource` 注解，一键开启动态数据源功能
- 自动配置（`DynamicDataSourceAutoConfiguration`），基于 JDBC 动态加载数据源
- 支持从数据库读取数据源配置（`JdbcDynamicDataSourceProvider`）
- 请求级数据源切换（`LastParamDsProcessor`）与线程变量清理（`ClearTtlDataSourceFilter`）
- 数据源类型枚举（`DsConfTypeEnum`、`DsJdbcUrlEnum`）

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/EnableDynamicDataSource.java` | 启用动态数据源注解 |
| `DynamicDataSourceAutoConfiguration.java` | 自动配置入口 |
| `config/JdbcDynamicDataSourceProvider.java` | JDBC 数据源提供者 |
| `config/DataSourceProperties.java` | 数据源属性配置 |
| `support/DataSourceConstants.java` | 数据源常量 |

## 包路径

`com.lotus.bixi.common.datasource`
