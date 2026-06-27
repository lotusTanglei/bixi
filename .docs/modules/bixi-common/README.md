# bixi-common — 公共基础模块

项目公共基础设施层，提供所有业务模块共享的工具类、配置封装和通用能力。共包含 14 个子模块。

## 子模块清单

| 子模块 | 职责说明 |
|:--|:--|
| `bixi-common-core` | 核心工具类、常量定义、统一异常处理、通用响应封装（R 类） |
| `bixi-common-security` | Spring Security + OAuth2 资源服务器配置、权限注解封装 |
| `bixi-common-mybatis` | MyBatis-Plus 配置、分页插件、基础 Mapper/Service 封装 |
| `bixi-common-feign` | OpenFeign 客户端配置、请求拦截器（自动传递认证头） |
| `bixi-common-log` | 操作日志切面，通过 `@SysLog` 注解记录操作日志 |
| `bixi-common-swagger` | SpringDoc OpenAPI 文档自动配置 |
| `bixi-common-oss` | 对象存储封装，兼容 AWS S3 协议 |
| `bixi-common-xss` | XSS 防护过滤器和 JSON 反序列化清洗 |
| `bixi-common-datasource` | 数据源自动配置，支持动态数据源切换 |
| `bixi-common-mq` | 消息队列公共配置 |
| `bixi-common-seata` | Seata 分布式事务配置 |
| `bixi-common-ai` | Spring AI Alibaba 公共配置和工具类 |
| `bixi-common-workflow` | Flowable 工作流公共配置和工具类 |
| `bixi-common-bom` | Maven BOM，统一管理所有第三方依赖版本 |

## 设计原则

- 各子模块通过 Spring Boot 自动配置（`spring.factories` / `AutoConfiguration.imports`）按需加载
- 业务模块按需引入所需的 common 子模块，避免全量依赖
- `bixi-common-bom` 作为依赖版本管理的唯一入口
