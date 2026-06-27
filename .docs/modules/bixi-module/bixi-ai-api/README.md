# bixi-ai-api

AI 模块 API 层，定义 Feign 远程调用接口、数据传输对象和实体类。

## 模块职责

- `RemoteAiService` Feign 客户端接口，供其他微服务远程调用 AI 能力
- DTO 数据传输对象：`ChatDTO`、`SessionDTO`、`MessageDTO`、`ModelConfigDTO`、`EmbeddingDTO`、`DocumentDTO`、`SearchDTO`
- VO 视图对象：`ChatVO`、`SessionVO`、`MessageVO`、`ModelConfigVO`、`DocumentVO`、`SourceVO`
- Entity 实体类：`AiSession`、`AiMessage`、`AiConversation`、`AiEmbedding`、`AiDocument`
- `AiConstants` AI 模块常量定义

## 关键文件

| 文件 | 说明 |
|------|------|
| `feign/RemoteAiService.java` | AI 远程调用 Feign 接口 |
| `dto/ChatDTO.java` | 聊天请求 DTO |
| `entity/AiSession.java` | 会话实体 |
| `entity/AiMessage.java` | 消息实体 |
| `constant/AiConstants.java` | AI 常量定义 |

## 包路径

`com.lotus.bixi.ai.api`
