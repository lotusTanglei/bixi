# 阶段二 C：Quartz、通知与 AI 黑盒证据

更新：2026-09-27。本文记录隔离 single 本地进程和 `bixi-phase2-cloud` enabled 栈上的真实 HTTP 黑盒结果。Cloud AI 的 enabled 结果使用本地确定性 provider；真实 DashScope/provider 仍单独列为未验证。Cloud Quartz 的 HTTP 边界按部署架构单独说明。Cloud 通用租户隔离矩阵的最新证据见 [EVIDENCE-CLOUD-TENANT-ISOLATION-20260927.json](EVIDENCE-CLOUD-TENANT-ISOLATION-20260927.json)。

## 运行条件

- single 应用：`http://127.0.0.1:29992`，使用隔离项目 `bixi-phase2-verify-single` 的 MySQL/Redis。
- 认证：真实调用 `/admin/oauth2/token` 获取管理员 Bearer token；没有复用过期 token。
- 运行时菜单没有 `/workflow/*` 或 `/demo/leave/*` 路径，故实际进程为 workflow disabled；`AI_ENABLED=false`。
- 本次文档收口没有启动、重启或清理 Cloud 容器；下文 Cloud enabled 结果引用此前已完成且可复查的运行记录。早期 Cloud 验收容器的 `ExitCode=127` 结果不写成通过。

可复查的本轮原始输出（运行目录下的忽略产物）为 `target/c-blackbox/quartz-blackbox.json` 和 `target/c-blackbox/notice-ai-blackbox.json`；以下表格只引用这些输出中已观察到的响应和数据库状态。

## Quartz

执行脚本：

```text
python3 -u target/c-blackbox/FailingHttpServer.py 39991 & server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT
node target/c-blackbox/quartz-blackbox.mjs
```

脚本使用一次性 HTTP 500 端点和一次性 JAR（实际退出码 7），每次结束都暂停并删除测试任务。真实 Controller 验收结果：

| 场景 | 观察结果 |
| --- | --- |
| 创建、详情 | `POST /admin/sys-job`、`GET /admin/sys-job/{id}` 均 HTTP 200/code 0 |
| 启动 | `POST /admin/sys-job/start-job/{id}` 成功，详情状态从 `1` 变为运行 `2` |
| 运行中修改 | HTTP 200/code 1，消息为“运行中的定时任务不能修改,请先暂停” |
| 手动执行 REST | `POST /admin/sys-job/run-job/{id}` 成功；记录为 `status=1`、`triggerType=MANUAL`，异常明确包含 `REST调用返回HTTP 500` |
| 暂停、暂停中修改、恢复 | 暂停后状态 `3` 且可修改；再次启动恢复为状态 `2` |
| JAR 非零退出 | 手动执行成功受理；记录为 `status=1`，异常明确包含 `JAR以非零状态退出(7)` 和输出 `blackbox-failure` |
| 删除 | 两个临时任务均暂停后删除成功；验收结束后查询无 `blackbox-*` 任务 |

这证明 REST 非 2xx 和 JAR 非零退出都会进入失败执行记录，不能被手动触发接口的 HTTP 200（“请求已受理”）掩盖。

## 通知、收件人与 SSE

执行脚本：

```text
node target/c-blackbox/notice-ai-blackbox.mjs
```

脚本创建三条临时通知并在 `finally` 中删除。真实观察结果：

- SSE `/admin/user-notice/stream` 首次连接返回 HTTP 200、`Content-Type: text/event-stream`，先收到 `event:open\ndata:ok`；发布通知后收到包含 `noticeId`、`userNoticeId` 和 `type=notice` 的刷新事件。
- 断开首个 SSE 后重新连接，第二条通知仍收到新的 `open` 和刷新事件，证明连接重建后订阅仍可用。
- 客户端在创建请求中伪造 `status=1`、`senderId=999999`，保存后详情仍为草稿 `status=0`、服务端发送人 `senderId=1`。
- 发布后的收件行初始为 `deliveryStatus=DELIVERED`、`deliveryAttempts=1`；通过 `PUT /admin/user-notice` 标记已读后 `isRead=1` 且有 `readTime`，已读状态与投递状态独立保存。
- 在隔离数据库中将第三条通知收件行受控置为 `FAILED`、`deliveryAttempts=1` 并保留失败原因；调用公开 `POST /admin/notice/{id}/delivery/retry` 后重新收到 SSE，最终状态为 `DELIVERED`、`deliveryAttempts=2`、`deliveryLastError=null`。
- 选取已有其他用户的收件行进行越权检查：`GET /admin/user-notice/{id}` 返回 code 1“记录不存在”；修改已读和删除分别返回 code 0/data false，行未被改变。
- 本轮通知标题为 `blackbox-*` 的查询结果为 0；测试产生的临时通知已删除。

## AI/RAG 关闭态限制与证据

本机没有 DashScope/provider key，且运行时 `AI_ENABLED=false`，因此没有伪造 AI 成功结果，也没有调用外部模型。关闭态黑盒结果如下：

- 菜单没有 `/ai` 路径。
- `/admin/ai/documents/list`、`/admin/ai/config`、`/admin/ai/chat` 和 `/admin/ai/documents/1` 均 HTTP 404/code 1，控制器未注册。
- 向关闭态 chat/delete 请求提交唯一 secret marker，响应没有回显 marker。
- 另以真实管理员登录当前隔离 Single 进程（`SERVER_PORT=29992`）复核上述四个路径，均返回 HTTP 404；AI 进程配置为 `AI_ENABLED=false`。
- Cloud 网关短暂重启期间曾出现 Nacos 无 `bixi-auth` 实例（OAuth 503）和前端 `28080` stale upstream 502；实例恢复后改用当前网关 `127.0.0.1:29997` 直接登录成功，四个 AI 路径同样均返回 HTTP 404。前端入口的那次 502 不作为业务结果，Cloud AI disabled 结论只引用直接网关响应。
- 用临时运行配置执行 `BIXI_ENV_FILE=<tmp> sh scripts/bixi.sh validate-config cloud`，设置 `AI_ENABLED=true` 但留空 `DASHSCOPE_API_KEY`，退出码为 1，并返回 `AI_ENABLED=true requires DASHSCOPE_API_KEY`；启动脚本因此不会在缺少 provider key 时启动 enabled AI。
- AI ownership、摄取、分块、embedding、召回、来源、删除和租户隔离的 enabled 运行态证据见下文；关闭态 404 不当作功能通过。真实 DashScope/provider 仍未验证，Cloud AI 专用跨租户黑盒已在下文单独通过。

## AI/RAG 本地 provider focused 证据

为验证 AI 业务边界而不伪造外部供应商结果，使用 `ai.enabled=true` 的 H2/JDBC 隔离测试上下文和确定性本地 embedding 实现 `bixi-local-hash-v1`。执行命令：

```text
mvn -pl bixi-module/bixi-ai-biz -am \
  -DskipTests=false \
  -Dtest=AiConditionalConfigurationTest,AiControllerSecurityContractTest,AiSchemaContractTest,ChatServiceImplTest,DocumentContentExtractorTest,VectorStoreServiceImplTest,AiOwnershipIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`32 tests, 0 failures, 0 errors, 0 skipped`，Maven `BUILD SUCCESS`。覆盖的可复查行为包括：

- PDF 和 DOCX 从 `uploadDocument` 入口解析，文档分块并写入 embedding；`bixi-local-hash-v1` 向量落库，内容词序反转的查询仍可从 cosine 向量路径召回，证明该断言不只依赖连续文本 `LIKE`。
- 文档删除会软删除对应 embedding，之后的向量检索不再返回该文档。
- 文档、会话和消息按认证用户及租户隔离；伪造 `userId`/`tenantId` 不改变服务端归属，跨用户或跨租户访问被拒绝。
- 分块 embedding 写入中途失败时，文档和已经写入的部分 embedding 在事务中一起回滚。
- 流式 provider 断连时保存已生成的 partial answer，并记录 `stream_error`；RAG prompt 优先使用命中的 chunk `snippet`，缺少 snippet 才回退完整文档内容，响应保留来源。

该证据只覆盖本地确定性 provider、测试数据库和 service 层；没有证明 DashScope 真实 provider 或完整业务数据迁移，也不替代下文的 Cloud HTTP 黑盒。Cloud enabled HTTP 与专用跨租户隔离结果见下文；Single enabled HTTP 的补充结果见 `target/phase2-runtime/ai-chat-rag-stream-29998.json`。

### AI migration probe（真实 MySQL）

在正在运行的隔离容器 `bixi-phase2-verify-single-mysql-1`（MySQL `8.4.3`）中创建一次性数据库，使用缺少新增字段的旧版 AI 表结构，依次执行以下两个迁移各两次：

```text
bixi-project-documents/sql/migrations/20260926_ai_base_entity_columns.sql
bixi-project-documents/sql/migrations/20260926_ai_rag_ingestion.sql
```

两轮均成功。最终检查确认 `ai_session`、`ai_message`、`ai_conversation`、`ai_document` 均具备 `status/data_status/remark`，`ai_embedding` 具备 `embedding/chunk_content/status/data_status/remark`，并存在精确列序的 `idx_embedding_document_chunk(document_id,chunk_index,del_flag)`。临时数据库在检查后删除。该结果证明迁移脚本在真实 MySQL 8.4 上可从旧结构升级且重复执行幂等；不代表完整业务数据迁移或真实 DashScope/provider 行为，Cloud AI HTTP 运行态结果见下文。

同样的旧结构和双次执行在当前 Cloud MySQL 容器 `bixi-phase2-cloud-mysql-1`（同为 MySQL 8.4.3）上复跑成功，`ai_embedding` 的五个新增字段和复合索引检查通过，临时数据库也已删除。

## Cloud AI enabled deterministic provider HTTP（2026-09-27）

在 `bixi-phase2-cloud` enabled 栈上补做了真实 HTTP 验收。AI 服务使用本地
deterministic provider stub（响应标记为 `LOCAL-DETERMINISTIC-ANSWER`），没有使用或
记录真实 DashScope key。运行入口分为两层：前端代理
`http://127.0.0.1:28080/api`，以及用于定位网关转发问题的直接 Gateway
`http://127.0.0.1:29997`。结果摘要保存在脱敏 artifact
`EVIDENCE-CLOUD-AI-HTTP.json`（本地复核副本为
`target/phase2-runtime/ai-cloud-enabled-http-passed.json`）；其中不含 Bearer token、
密码、provider key 或完整动态文档内容。

前端代理探测（`28080/api`）的管理员登录和以下请求均返回 HTTP `200` 且业务
`code=0`：

| 请求 | 结果 |
| --- | --- |
| `/ai/config` | 通过 |
| `POST /ai/documents` | 通过 |
| `/ai/documents/list` | 通过 |
| `POST /ai/search` | 通过，精确 marker 命中数 `1` |
| `POST /ai/rag` | 通过，RAG source 数 `1` |
| `POST /ai/chat` | 通过，响应包含 `LOCAL-DETERMINISTIC-ANSWER` |
| `GET /ai/stream/chat` | 通过，HTTP `200`、`text/event-stream`，事件包含 provider marker |
| `DELETE /ai/documents/{id}` | 通过 |
| 删除后 `POST /ai/search` | 通过，限定文档结果数 `0` |

同一 enabled 栈的 Gateway 直接探测也复现了上述聊天和路由结果，用于定位网关转发问题：

- `POST /ai/chat` 返回 HTTP `200`，同步响应包含 `LOCAL-DETERMINISTIC-ANSWER`。
- `GET /ai/stream/chat` 返回 HTTP `200`、`text/event-stream`，SSE 数据包含同一
  provider marker。
- 删除文档后按该文档限定搜索返回空结果（`search-after-delete` count `0`）。
- AI 菜单、AI 权限、配置/模型查询以及 Auth、UPMS、Generator 基础路由均可访问；
  带 token 的 `/ai/config`、`/ai/models` 均为 HTTP `200`/`code=0`。

### Gateway route 修复

enabled profile 的 Nacos YAML 原先重新声明了 list-valued
`spring.cloud.gateway.routes`。Spring 在 profile 文档中替换整个列表，导致启用
AI 后 `/auth/**`、`/admin/**` 和 `/gen/**` 路由消失。另一个独立问题是
`BixiRequestGlobalFilter` 会剥离第一个公共路径段，AI Controller 需要保留 `/ai`
前缀；否则下游收到 `/config` 等路径并返回静态资源 404。

`deploy/nacos/bixi-gateway-dev.yml` 的 `ai` profile 现完整保留 Auth、UPMS、
Generator 路由，并为 AI 路由增加：

```yaml
filters:
  - PrefixPath=/ai
```

`sh scripts/ai-cloud-runtime-config.test.sh` 已通过；运行观察到 Auth token、
Admin health/user/menu 和 AI config/models 路径均恢复。该配置修复在 Cloud 运行验收
期间已发布并验证；本次文档更新没有再次重启服务。

### 失败运行记录与验证边界

- `target/phase2-runtime/ai-cloud-enabled-http.json` 保留早期运行记录，其中
  `passed=false`，RAG `sourceCount=0`；它只用于追溯失败尝试，**不作为通过证据**。
- `target/phase2-runtime/ai-cloud-ownership-http.json` 保留租户 fixture/login 失败
  记录；它也**不作为通过证据**，不能解释为已完成跨租户越权验收。
- `AiOwnershipIntegrationTest` 覆盖 service/integration 层的归属和租户规则；早期
  `ai-cloud-ownership-http.json` 的 fixture/login 失败只保留为历史。Cloud AI 专用黑盒
  已在 `EVIDENCE-CLOUD-AI-TENANT-ISOLATION-20260927.json` 通过，覆盖跨租户登录选择、
  文档可见性、向量搜索、RAG source、伪造租户头、跨租户删除拒绝和删除后不可召回；通用
  租户矩阵对 Auth 到 UPMS 的 Feign `X-Tenant-Id` 传播仍是独立证据，不能替代 AI 专用
  断言。
- 本节 deterministic provider 只证明 Bixi HTTP、路由、RAG source 和流式响应契约；
  不证明真实 DashScope/provider 的网络、限流、错误码、计费或模型质量。

## 渠道适配器 focused 证据

通知渠道的独立 focused 测试在同一源码上通过：`NoticeChannelDispatcherTest` 12/12、`PublishedNoticeNotifierTest` 2/2、`NoticePublicationIntegrationTest` 18/18、`NoticeLocalDeliveryIntegrationTest` 5/5。Dispatcher 测试使用本地 JDK `HttpServer` 验证 Email/Webhook 的 HTTP 202/204 成功、503 失败、请求状态落库和重试；也验证租户不匹配、地址缺失以及 SMS/WeChat 未配置时的可审计失败。该结果证明 adapter/状态机边界，不代表第三方供应商送达、渠道 fan-out、回执或限频已经完成。

## Cloud 通用租户隔离矩阵（2026-09-27）

在同一 `bixi-phase2-cloud` enabled 栈上运行 `scripts/tenant-isolation-acceptance.mjs`，直接使用
Gateway `29997`、前端健康端口 `28080`、该项目的 MySQL/Redis 容器。命令和脱敏摘要见
[Cloud 租户隔离证据](EVIDENCE-CLOUD-TENANT-ISOLATION-20260927.json)，退出码为 `0`。

真实 HTTP 观察覆盖：同名用户跨租户登录选择、租户内列表/详情/XLSX 导出/插入绑定、跨租户详情/更新/删除拒绝、伪造 `X-Tenant-Id`、普通用户 `ALL` 请求拒绝、管理员 `ALL` 只读查询与写保护，以及停用租户后现有访问和 refresh-token 交换被拒绝。验收结束后临时 MySQL 行数和该租户 `tenant_status` Redis 键均为 `0`。

租户状态的启停在该黑盒中由临时 fixture SQL 完成，随后用真实 HTTP 验证访问和 refresh 拒绝；不把 `/tenant/{id}/status` 管理写接口的权限结果写成通过。AI 文档/搜索/RAG 的跨租户运行态隔离已由专用黑盒单独验证，见 `EVIDENCE-CLOUD-AI-TENANT-ISOLATION-20260927.json`；本通用矩阵仍不能替代 AI 专用断言。

## 未覆盖项

- Cloud Quartz HTTP 黑盒：Cloud 网关没有 `/job/**` 路由，Cloud 编排也没有独立 Quartz 服务；Quartz 由 `bixi-single` 组合运行，因此 Cloud Quartz HTTP 验收不适用。Cloud 通知/SSE/retry 已在下节完成真实 HTTP 黑盒。
- Quartz 真实长时间 CRON、进程重启恢复和完整失败重试退避时序。
- 通知真实第三方供应商送达、渠道 fan-out/回执/限频、Rabbit broker 故障矩阵和完整跨应用容器重启；本轮只验证 Cloud `IN_APP`/SSE 及管理 retry。
- 真实 DashScope/provider 黑盒仍未覆盖；本地 deterministic provider 的 Cloud HTTP 主链路
  以及 Cloud AI 专用运行态跨租户登录/召回隔离均已在上文通过。真实 provider 的网络、
  限流、错误码、计费和模型质量仍需单独验证。

## Cloud 基础栈与统一验收增量（2026-09-26）

本节记录本轮恢复 Docker 存储后执行的真实 Cloud 验收。运行项目为
`bixi-phase2-cloud`；为避免与本机其他 Compose 项目占用端口，使用了从现有
`.env` 复制的忽略运行文件 `target/phase2-runtime/cloud.env`，仅将宿主端口改为
`HTTP=28080`、`Gateway=29997`、`MySQL=23306`、`Redis=26379`、
`Rabbit=25672/25673`、`Nacos=28848`、`Monitor=25001`。凭据值未打印或改写。

先用 `docker compose ... up -d --no-build` 恢复容器；清理任务专属网络的陈旧
endpoint 后重新启动。Gateway 镜像随后按当前工作树重新构建并替换：

```text
docker compose --env-file target/phase2-runtime/cloud.env --profile cloud \
  -p bixi-phase2-cloud build gateway
docker compose --env-file target/phase2-runtime/cloud.env --profile cloud \
  -p bixi-phase2-cloud up -d --no-deps gateway
```

`docker inspect` 对 Nacos、MySQL、Redis、RabbitMQ、Gateway、Auth、UPMS、
Generator、Monitor 和 Frontend 共 10 个容器均观察到 `State.Status=running`、
`State.Health.Status=healthy`，配置容器按预期 `Exited (0)`。当前健康入口为
`http://localhost:28080/healthz`，Gateway 为 `http://localhost:29997`。

统一黑盒命令（等价于 `make verify-cloud`，显式指定本轮隔离 env）及结果：

```text
BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud.env \
  node scripts/acceptance.mjs
```

结果为进程退出码 0，JSON 摘要确认：登录 HTTP 200；管理员用户、角色和
`demo_task_{view,add,edit,del}` 权限正确；示例任务 create/page/details/update/
invalid-request/delete 全部通过；3 条新增/修改/删除操作日志已持久化；
`WORKFLOW_ENABLED=false` 时 Workflow 菜单隐藏且 `/admin/workflow/*` 端点缺失。

本节只证明上述 Cloud 基础栈和共享核心黑盒已经在真实容器中运行；后续段落及
`../generator/EVIDENCE-STAGE2.md` 已记录 Cloud Generator acceptance 和 Single
生成项目 CRUD 运行结果，本节不重复替代这些证据。仍未覆盖的是 Cloud 生成项目自身
运行、真实 DashScope/provider、完整应用容器重启恢复和四组合故障矩阵。Cloud
deterministic provider 的 AI HTTP 证据见本文件上文。

## Cloud enabled Workflow 增量（2026-09-26 22:42 +08:00）

在同一隔离 Compose 项目 `bixi-phase2-cloud` 上启用 Workflow 和 Rabbit 可靠投递，
运行时文件为 `target/phase2-runtime/cloud-enabled.env`。宿主端口保持：前端
`28080`、Gateway `29997`、MySQL `23306`、Redis `26379`、RabbitMQ
`25672/25673`、Nacos `28848`、Monitor `25001`。应用启动前停下 Workflow，使用
受控迁移脚本按仓库顺序初始化 Flowable 7.1.0 及 Workflow 扩展表；迁移完成后
`bixi_schema_migration` 记录数为 `23`，数据库中 `ACT_*` 表数为 `30`，应用保持
`WORKFLOW_SCHEMA_UPDATE=false`。迁移脚本明确指向本任务 Compose 项目，未使用本机
其他 `bixi` 项目的数据库。

迁移后 Workflow 容器重新启动并由 `docker inspect` 观察为
`State.Status=running`、`State.Health.Status=healthy`；Cloud 基础服务、Gateway、
Auth、UPMS、Generator、Monitor 和前端也均为 healthy（一次性 Nacos/Rabbit 配置
容器按预期 `Exited (0)`）。

启用态统一验收命令及结果：

```text
BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud-enabled.env \
  node scripts/acceptance.mjs
```

进程退出码为 `0`。结果覆盖管理员登录/权限/菜单、示例 CRUD、参数校验和审计，
以及 Workflow `APPROVED`、`REJECTED`、`CANCELED` 三种终态、START 重试与冲突、
办理人与数据权限、历史记录、表单版本 `v1/v2` 冻结与新实例使用 `v2`，并持久化
对应操作日志。

同一 enabled 运行时的 Generator 验收也退出码 `0`：单表产生 `17` 个预览/ZIP
条目和 `9` 个 XLSX 条目，父子表产生 `21` 个预览/ZIP 条目和 `9` 个 XLSX 条目，
并通过元数据同步、服务端生成、过期模板版本拒绝、权限/匿名拒绝及审计检查；
产物目录为 `target/generator-acceptance/cloud-StNHXL`，详见
`../generator/EVIDENCE-STAGE2.md`。本节仍不覆盖真实 DashScope/provider、完整应用
容器重启恢复、Cloud Quartz 架构边界以外的专用故障矩阵及剩余故障矩阵；AI
deterministic provider 和通知/SSE/retry 专用结果见本节其他段落。

## Cloud enabled 通知、SSE 与 retry 黑盒（2026-09-26）

本节补齐同一 `bixi-phase2-cloud` enabled 栈的通知专用 HTTP 验收。运行时入口为
`http://localhost:28080/api`；没有输出用户名、密码、Bearer token 或 provider secret。
可复查脚本和脱敏 JSON 输出分别为
`scripts/cloud-notice-sse-blackbox.mjs` 与
`EVIDENCE-CLOUD-NOTICE-SSE.json`。执行命令：

```text
BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud-enabled.env \
  node scripts/cloud-notice-sse-blackbox.mjs
```

脚本退出码为 `0`，输出中的动态 ID 仅用于将 SSE 事件与本次收件行关联，测试结束后已删除通知；随后按标题前缀查询 Cloud MySQL，活动 `cloud-blackbox-*` 通知数为 `0`：

```json
{
  "status": "passed",
  "apiBase": "http://localhost:28080/api",
  "sseOpen": true,
  "refresh": {"type": "notice", "noticeId": "2103874759105613826", "userNoticeId": "2103874759143362561"},
  "deliveryStatus": "DELIVERED",
  "resend": true,
  "retry": true,
  "cleanup": true,
  "activeBlackboxNoticeCountAfterCleanup": 0
}
```

实际断言覆盖：

- 管理员真实登录；`GET /admin/user-notice/stream` 返回 HTTP `200`、`text/event-stream`，首帧为 `event:open/data:ok`。
- 创建 `deliveryChannel=IN_APP` 的临时通知并调用 `POST /admin/notice/send/{id}`；收件行最终为 `DELIVERED`。
- SSE 刷新事件的 `noticeId` 和 `userNoticeId` 与持久化收件行一致。
- 标记已读后重发，再调用 `POST /admin/notice/{id}/delivery/retry`；收件人 ID 与 `isRead=1` 均保持不变，没有重复收件行。

该结果证明 Cloud 本地 IN_APP/SSE 与管理 retry 链路；不证明第三方 Email/SMS/WeChat/Webhook 的真实送达、回执、限频或供应商故障恢复。Cloud 不部署独立 Quartz 应用，网关路由也没有 `/job/**`，所以 Quartz Cloud HTTP 黑盒按架构边界记为不适用；Quartz REST 证据只来自 Single 运行模式。

本次运行期间 UPMS 曾出现 `restart count=4`，当前观察可恢复为 `running/healthy`，但启动约 90 秒而健康检查 `start_period` 约 40 秒，存在重复重启风险。该风险不影响本次已通过的通知断言，但完整稳定性、应用容器重启恢复和故障矩阵仍未闭环。

## 2026-09-27 最新 Cloud/Single 门禁复核

Rabbit 权限初始化完成后，Workflow 与 UPMS 重新启动并成功声明
`bixi.upms`、`bixi.workflow` 及两个 quorum inbox；两个队列各有一个手动 ACK 消费者且无积压。
使用显式隔离 env 执行：

```text
BIXI_ENV_FILE=/tmp/bixi-cloud-env.retOvB \
BIXI_ORIGIN=http://127.0.0.1:28080 \
BIXI_API_BASE_URL=http://127.0.0.1:29997 \
WORKFLOW_ENABLED=true BIXI_RELIABLE_ENABLED=true \
BIXI_RELIABLE_RABBIT_ENABLED=true AI_ENABLED=false \
make verify-cloud
```

退出码为 `0`，覆盖管理员登录、示例 CRUD/审计、三种 Workflow 终态、稳定重试与冲突、
办理人与数据权限、历史和表单 v1/v2 版本冻结。同期 `verify-single` 的健康探针通过，
但复用旧 single 镜像在登录阶段返回 `401`；该运行态结果标为未验证，不能替代 fresh
single 镜像的双模证据。临时 single 容器已清理。

## SBA 多实例运行态（2026-09-27）

在 `bixi-phase2-cloud` enabled 栈中，将 `bixi-upms-biz` 临时扩展为两个健康副本，
两个副本均以 `BIXI_SBA_CLIENT_ENABLED=true` 注册到同一个 Spring Boot Admin。脚本
`scripts/sba-multi-instance-acceptance.mjs` 通过 Basic Auth 请求 `/instances`，要求同一
服务名至少有两个不同 `serviceUrl`、状态均为 `UP`，并核对每个实例的 `managementUrl`
和 `healthUrl`；密码字段在输出中脱敏。测试契约为
`scripts/test-sba-multi-instance-acceptance.test.mjs`，`make sba-multi-instance-static-test`
退出码为 `0`。

本次真实运行命令（动态容器 IP 只写入脱敏证据）为：

```text
SBA_URL=http://127.0.0.1:25001 SBA_USERNAME=monitor \
SBA_PASSWORD=*** SBA_EXPECTED_SERVICE_URLS=<upms-1>,<upms-2> \
node scripts/sba-multi-instance-acceptance.mjs
```

结构化结果见 `target/phase2-runtime/sba-multi-instance-20260927.json`，观测到两个
独立 UPMS 实例均为 `UP`。本次扩容还复现并修复了 Rabbit 初始化脚本在 admin/app
用户名复用时清空管理员 tag 的问题；`deploy/rabbitmq/configure-access.sh` 现在在
用户名复用且密码一致时保留管理员，不一致时快速失败，回归测试见
`scripts/test-reliable-rabbit.test.mjs`。该证据闭合 SBA 多实例注册与状态观测，仍不
覆盖所有依赖同时重启、节点故障接管或完整结果丢失故障矩阵。
