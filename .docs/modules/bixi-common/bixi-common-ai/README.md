# bixi-common-ai

Spring AI Alibaba 公共配置模块，为 AI 业务模块提供条件装配、模型属性和输入处理工具。

## 模块职责

- `AiAutoConfiguration` 仅在 `ai.enabled=true` 时装配；聊天和向量模型还分别受 `spring.ai.dashscope.chat.enabled` 与 `spring.ai.dashscope.embedding.enabled` 控制。
- `AiProperties` 绑定 `spring.ai.dashscope.*`，集中维护 DashScope API key、聊天模型、embedding 模型和模型选项。
- `AiInputValidator` 校验输入，`SensitiveDataFilter` 在 AI 交互前过滤敏感数据。

## 运行边界

- cloud 的 AI 业务服务和 single 聚合应用都默认关闭 AI；启用时需要提供 `DASHSCOPE_API_KEY`，真实模型调用仍依赖外部供应商。
- 本模块只提供公共配置和工具，不包含会话、消息、知识库等业务 Controller。

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/AiAutoConfiguration.java` | AI 条件自动配置入口 |
| `properties/AiProperties.java` | `spring.ai.dashscope.*` 属性类 |
| `util/AiInputValidator.java` | 输入校验工具 |
| `util/SensitiveDataFilter.java` | 敏感数据过滤器 |

## 包路径

`com.lotus.bixi.common.ai`
