# bixi-ai-biz

AI 对话业务模块，基于 Spring AI Alibaba 实现智能对话、向量存储、模型配置等核心功能。

## 模块职责

- `ChatService` / `ChatServiceImpl` 聊天服务，处理 AI 对话请求与流式响应
- `SessionService` / `SessionServiceImpl` 会话管理，维护用户对话上下文
- `MessageService` / `MessageServiceImpl` 消息管理，存储对话历史
- `VectorStoreService` / `VectorStoreServiceImpl` 向量存储服务，支持文档嵌入与语义检索
- `ModelConfigService` / `ModelConfigServiceImpl` 模型配置管理
- `AiController` / `AiSessionController` REST 接口
- Mapper 层：`AiSessionMapper`、`AiMessageMapper`、`AiConversationMapper`、`AiEmbeddingMapper`、`AiDocumentMapper`

## 关键文件

| 文件 | 说明 |
|------|------|
| `BixiAiApplication.java` | AI 模块启动类 |
| `service/impl/ChatServiceImpl.java` | 聊天服务实现 |
| `service/impl/VectorStoreServiceImpl.java` | 向量存储服务实现 |
| `service/impl/SessionServiceImpl.java` | 会话管理服务实现 |
| `controller/AiController.java` | AI 对话控制器 |

## 包路径

`com.lotus.bixi.ai`
