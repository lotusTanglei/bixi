# 阶段二 2A：请求与操作幂等验收记录

状态：**2A 已完成**。实现及独立规格/质量审查通过，最终源码已通过双模后端 CI、cloud/single HTTP 验收和浏览器响应丢失恢复。阶段二可靠事件尚未接入，本记录不替代阶段二最终验收。

## 实现范围

同一操作者、服务器固定的 `default` 范围与同一 UUID `requestId` 只能保存一个成功命令。START 及 complete/reject/transfer/delegate/resolve/claim/unclaim/comment/terminate/cancel/suspend/activate 均接入此边界；缺少标识直接拒绝，不自动生成新请求。流程定义启停属于后续版本管理，不在本批实例操作范围。

有效输入按实际执行规则规范化，递归排序对象键、保留数组顺序和精确十进制数；同 ID 改变操作、资源或有效内容返回 `WORKFLOW_REQUEST_CONFLICT`。变量使用工作流专用 JSON 编解码，响应快照使用应用 ObjectMapper，避免 Long 展示序列化、高精度小数和时间格式破坏幂等契约。孤立 UTF-16 surrogate 在文本、键及哈希身份字段中被拒绝，HTTP 返回 400；合法 emoji 保留。

同步命令使用独立事务代理，命令预留、Flowable、流程扩展、表单及审批记录一同提交。请求/终结任务唯一键竞争先结束失败事务，再以新事务读取已提交胜者。任务定位在重放查询之后、写事务之前；取得流程扩展行锁后重新判断任务状态和办理人，避免 MySQL REPEATABLE-READ 旧快照。不同终结请求竞争返回 `WORKFLOW_OPERATION_CONFLICT`，关闭引擎历史后也可从当前 actor 的持久化命令识别该冲突。

成功响应保存为不可变快照，重试发生在当前任务状态检查之前；任务消失、转办或流程结束后仍能重放。命令查询只返回当前操作者的记录，并要求当前读权限。

共享界面在发送前按账号、资源与 UUID 保存原意图，提供原样重试、结果查询和明确的新意图操作。待办消失后仍可从待确认面板恢复，面板显示操作、名称、编号和原内容。认证清理保留明确的工作流命名空间，同时删除认证 Cookie/令牌和其他缓存；另一账号不能查看或清除原账号的记录。保留限当前浏览器 session，不包含主动清除浏览器数据。

## 已执行的验证

| 验证 | 实际结果 |
| --- | --- |
| 最终源码后端 CI | `make backend-cloud-ci` **304 项**、`make backend-single-ci` **302 项**，零失败、错误、跳过。使用独立固定源码副本，计数来自各 Profile 的本次 Maven suite 日志，排除另一个 Profile 遗留报告。包含该副本已有的安全测试，不代表同工作区安全任务的后续变更已验收。 |
| MySQL 任务/流程完整批次 | 8.0.45、REPEATABLE-READ，两 Profile 各 91 项：Approval 46、START 17、history=none 3、Leave 25，共 **182 项**通过。此批次早于最后 Unicode 修正。 |
| Unicode 修正后的定向验证 | API JSON 5、hasher 4、START 19、Approval 46 通过；实际 MySQL START **19 项 × 两 Profile**通过。独立 Java 17 probe 验证 21 条非法哈希路径、HTTP 解析、局部序列化及合法 emoji。最终全部 H2/适配/装配回归已包含在上述 CI。 |
| 数据库迁移 | MySQL 增量迁移 7 项通过，覆盖旧数据/命令保留、重复运行、重复实例预检、错误同名索引及 mysql --force 防绕过。运行库 8.4.3 完整备份后迁移，迁移前 8 个实例保留。 |
| 共享前端 | **44 项**真实 helper/SFC/API/模板渲染及认证清理测试通过，独立规格/质量审查通过，完整 ESLint 和生产构建通过。 |
| 启用模式 HTTP | 最终 Unicode 镜像在 cloud、single 均通过共用 `make verify-*`：并发 START/COMPLETE/REJECT、稳定重放、内容/操作冲突、actor 隔离、单条审批记录、生命周期重放、非法 Unicode 400/无命令及原任务保留。 |
| 浏览器响应丢失 | cloud/single 最终轮均通过三场景：服务端成功后丢弃响应，刷新至空待办再查询、原样重试、清认证后重新登录查询。均保留原 UUID/内容，只有一条审批记录，无页面运行异常。 |
| 关闭模式 | cloud/single 共用核心验收通过，菜单隐藏/Workflow 接口不可用。流程、命令/结果、审批及活动任务共 **103 条**状态记录，关闭前后 SHA-256 完全一致；未因关闭丢数据。 |
| 静态检查 | `make architecture-check runtime-config-check` 与 `git diff --check` 通过；`codegraph sync .` 通过。 |

single 运行检查使用无 MQ 覆盖配置，实际停止 Rabbit、Nacos、Gateway、独立 Workflow 及 cloud 应用；同一套业务实现通过本地适配运行。

本地证据包含 `bixi-task-backend-final-evidence.txt`、`bixi-task-ui-auth-clear-*.log` 与 `bixi-workflow-stage2-runtime` 下的固定源码清单、CI、HTTP、浏览器及数据保留记录。临时运行凭据和数据库备份不纳入源码交付。

## 验证中修正的问题

- START 页面原先发送 processDefinitionId，已改为实际契约 processKey，并验证请求内容。
- JSON 边界、有效默认值与空变量语义、任务定位旧快照、历史关闭后的终结竞争及认证清理丢 UUID，均经失败回归和独立复审修正。
- 非法 Unicode 原先可在 UTF-8 编码时归并成替代字符，导致不同请求摘要相同；现已从 HTTP/本地输入及 hasher 两层拒绝。
- 本地旧验收库缺少并行安全任务新增的管理权限，最初返回 403；只同步固定源码中的缺失规范菜单/角色行并失效相关缓存后通过，未修改安全任务代码。
- 累计审计日志超过 500 条后，无排序的验收查询只读到旧记录，导致误报。数据库确认新日志存在后，将共用脚本改为按 ID 倒序读取；最终 cloud 重跑通过，single HTTP 也通过。
- 前端 Docker 入口脚本被既有 `*.sh` 规则忽略，已为 `bixi-ui/docker-build.sh` 增加源码例外，干净固定副本可以构建。

## 当前边界与下一步

2A 只增加请求唯一约束、引擎实例 ID 唯一约束及终结任务约束；不同 requestId 的业务轮次唯一性留到 2C。当前公开启动仍可携带业务关联，请假提交仍使用阶段一同步 SUBMITTING 协调；稳定 UUID 本身不代表已具备可靠提交或跨服务故障恢复。

下一批继续 Outbox/Inbox、可信持久化提交、single/Rabbit 共用处理器、自动任务/补偿与恢复管理。公共业务关联与保留模型的封闭、旧数据对账及业务轮次唯一约束须在 2B/2C 一次性运行切换中完成。阶段三、四仍是原始任务的后续范围。
