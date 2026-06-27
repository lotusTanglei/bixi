# bixi-common-ai

Spring AI Alibaba 公共配置模块，为 AI 业务模块提供统一的基础设施支持。

## 模块职责

- 提供 AI 自动配置（`AiAutoConfiguration`），统一初始化 Spring AI Alibaba 相关 Bean
- 管理 AI 属性配置（`AiProperties`），集中维护模型参数、API 密钥等配置项
- 输入校验（`AiInputValidator`），对用户输入进行合法性检查，防止恶意或无效请求
- 敏感数据过滤（`SensitiveDataFilter`），在 AI 交互过程中过滤敏感信息

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/AiAutoConfiguration.java` | AI 模块自动配置入口 |
| `properties/AiProperties.java` | AI 配置属性类 |
| `util/AiInputValidator.java` | 输入校验工具 |
| `util/SensitiveDataFilter.java` | 敏感数据过滤器 |

## 包路径

`com.lotus.bixi.common.ai`
