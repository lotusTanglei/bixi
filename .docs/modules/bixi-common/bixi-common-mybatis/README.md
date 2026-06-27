# bixi-common-mybatis

MyBatis-Plus 配置模块，提供分页、自动填充、基础 CRUD 封装等数据访问层公共能力。

## 模块职责

- `MybatisAutoConfiguration` 自动配置，注册分页插件与元数据处理器
- `BixiPaginationInnerInterceptor` 分页拦截器
- `MybatisPlusMetaObjectHandler` 自动填充创建时间、更新时间等字段
- `BaseEntity` / `BaseRelationEntity` 基础实体类
- `BaseController` 通用控制器基类，封装分页查询等通用接口
- `IBaseService` / `BaseService` 通用服务接口与实现
- `SqlFilterArgumentResolver` SQL 注入过滤参数解析器
- JSON 类型处理器：`JsonLongArrayTypeHandler`、`JsonStringArrayTypeHandler`

## 关键文件

| 文件 | 说明 |
|------|------|
| `MybatisAutoConfiguration.java` | MyBatis-Plus 自动配置 |
| `plugins/BixiPaginationInnerInterceptor.java` | 分页拦截器 |
| `config/MybatisPlusMetaObjectHandler.java` | 字段自动填充 |
| `controller/BaseController.java` | 通用控制器基类 |
| `service/BaseService.java` | 通用服务基类 |
| `base/BaseEntity.java` | 基础实体类 |

## 包路径

`com.lotus.bixi.common.mybatis`
