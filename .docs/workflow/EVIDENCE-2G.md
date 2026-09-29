# 阶段二 2G：独立 owner 进程重启证据

更新：2026-09-28。历史 Docker/MySQL/Rabbit 杀进程演练已执行通过；本日补齐 single/cloud enabled/disabled 的隔离运行矩阵和个人电脑资源审计。本记录仍不代表生产级恢复目标、数据库高可用或阶段二全部完成。

## 已实现

- 新增 `RabbitProcessRestartIntegrationTest` 两阶段测试：
  - `hold` 阶段在 Inbox 处理事务已经写入业务表、但尚未提交时写入独立连接 marker 并保持进程存活；
  - 外部脚本确认 marker 后只杀该测试 JVM；
  - `resume` 阶段由全新 JVM 接管过期 lease，验证回滚后的业务副作用只写入一次，Inbox 进入 `PROCESSED`。
- 新增 `scripts/test-workflow-process-restart.sh`，每次创建带 PID 和随机后缀的临时 MySQL 容器，轮询持久化 marker 后发送 `SIGKILL`，再启动恢复 JVM；退出时只删除本脚本创建的容器和卷。
- 新增 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest.realRabbitBookingAndCompensationRaceConvergesToOneCancellation`，并发执行登记请求与补偿请求，检查 booking 单行、`CANCELED` 终态和流程终止。
- 新增 `RabbitAckCrashIntegrationTest` 三阶段真实 Rabbit 用例。seed 阶段发布持久消息；hold 阶段在业务事务和 Inbox `PROCESSED` 已提交、Rabbit manual ack 尚未执行时写 marker；外部脚本杀掉 consumer JVM；resume 阶段由新 JVM 接收同一 Rabbit redelivery，确认 handler 不会再次执行、业务计数保持一次且队列最终清空。
- `RabbitInboxListener` 增加包内 before-ack 测试屏障；公开构造器仍固定使用 no-op，生产配置和确认顺序不变。`scripts/test-reliable-rabbit.sh` 在同一临时 MySQL/Rabbit 项目中自动执行该故障窗口，并只杀脚本启动的测试 JVM。

## 已执行

| 命令 | 结果 |
| --- | --- |
| `WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS=5 WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS=10 scripts/test-workflow-process-restart.sh`（2026-09-26 fresh） | 通过；临时 MySQL 中确认 hold JVM 到达未提交业务事务 marker 后被 `SIGKILL`，新 JVM 接管过期 lease，业务副作用一次，Inbox 为 `PROCESSED`；从 kill 到 resume 测试成功的脚本墙钟观测为 `2731 ms` |
| `scripts/test-reliable-rabbit.sh` | 通过；MySQL 8.0.45 / `REPEATABLE-READ`，Rabbit owner/listener 6/6、broker 重启 seed/consume 2/2、真实 Workflow/UPMS 自动任务 5/5 |
| 同一 Rabbit 脚本的 ACK 崩溃三阶段 | seed 1/1；hold JVM 在 `COMMITTED_BEFORE_ACK` marker 后被 `SIGKILL`；resume 1/1，redelivery 被持久 Inbox 去重，业务值仍为 1，队列清空 |
| `make architecture-check runtime-config-check`、`make workflow-cluster-config` | 均通过 |

### 2026-09-27 Rabbit 复跑更正

早期有界重跑曾在启动请求阶段报告 Workflow/UPMS 自动任务用例 `4/5` 失败。根因已确认是 Rabbit MySQL 测试夹具没有创建 `wf_process_definition`；`prepareStart` 查询该表时触发 `BadSqlGrammarException`，因此不是 Rabbit 传输或业务竞争失败。夹具已在 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest` 的 `WorkflowTestSchema.create(...)` 调用中补齐该表（源码第 113-115 行）。修复后的同一真实 Rabbit 脚本退出码为 `0`：common MQ owner/listener `6/6`，broker restart seed/consume `2/2`，Workflow/UPMS 自动任务 `5/5`，ACK crash seed/hold/resume `1/1`。

可复查的持久化 Surefire 报告为：

- `bixi-module/bixi-workflow-biz/target/surefire-reports/com.lotus.bixi.workflow.service.WorkflowUpmsAutomaticTaskRabbitIntegrationTest.txt`（`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`）
- `bixi-module/bixi-workflow-biz/target/surefire-reports/TEST-com.lotus.bixi.workflow.service.WorkflowUpmsAutomaticTaskRabbitIntegrationTest.xml`
- `bixi-common/bixi-common-mq/target/surefire-reports/com.lotus.bixi.common.mq.reliable.RabbitAckCrashIntegrationTest.txt`
- `bixi-common/bixi-common-mq/target/surefire-reports/com.lotus.bixi.common.mq.reliable.RabbitBrokerRestartIntegrationTest.txt`
- `bixi-common/bixi-common-mq/target/surefire-reports/com.lotus.bixi.common.mq.reliable.RabbitOwnerEndpointIntegrationTest.txt`

脚本为保护敏感临时输出会在退出清理 ACK worker 临时日志；以上 Surefire 文件和测试源码是本次复核的持久证据。

2026-09-27 再次执行同一隔离脚本（使用有界 Docker 命令超时）得到独立复核结果：

```text
WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS=5 \
WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS=20 \
bash scripts/test-workflow-process-restart.sh

Docker server ready: 29.4.0
Killing isolated worker JVM 13959 after handler entry.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Workflow process restart recovery passed (observed elapsed_ms=4417; marker-to-resume script wall clock, not an SLO).
Workflow owner process restart recovery passed.
```

该次运行创建的临时 MySQL 容器由脚本退出清理；现有 Cloud、AI 和 Single 运行容器未被停止或重启。

## 未执行与限制

当前演练杀的是隔离 Maven 测试 JVM，已覆盖 Inbox 事务提交前和 Rabbit ACK 前两个确定窗口，但没有启动并杀掉完整 UPMS/Workflow 应用容器，也没有覆盖网关连接丢失或 single 完整进程。2026-09-27 隔离演练观测为 `4417 ms`（2026-09-26 为 `2731 ms`）；这些值不能据此填写完整应用恢复时间目标。完整自动任务恢复报告、四组运行态故障矩阵和应用容器级重启仍需后续补齐。

## 2026-09-26/27 历史静态收口与覆盖矩阵

本轮先做脚本/证据静态复核，随后只启动并清理了该脚本创建的临时 MySQL 和测试 JVM，没有启动、重启或停止现有应用容器。`scripts/test-workflow-process-restart.sh` 现在会先用有界的 Docker Server 探针检查 daemon；探针失败时输出 `Environment blocked` 并以退出码 `2` 结束，不创建临时容器。运行条件满足时，脚本会输出从发送 `SIGKILL` 到恢复 JVM 测试成功的 `observed elapsed_ms`。2026-09-27 隔离演练观测为 `4417 ms`（此前 `2731 ms`）；这些值是脚本墙钟观测，不能当作 SLO 或完整应用恢复时延。

| 验收维度 | single | cloud | 当前可引用证据 | 状态 |
| --- | --- | --- | --- | --- |
| 完整 UPMS/Workflow 应用容器重启 | 未覆盖 | 未覆盖 | 只有隔离 Maven 测试 JVM 的 2G 脚本 | `[ ]` |
| 发起成功后的响应丢失再重试 | 2A 浏览器响应丢失已覆盖；2G 应用进程窗口未覆盖 | 2A 浏览器响应丢失已覆盖；网关断连窗口未覆盖 | `EVIDENCE-2A.md`；本记录无应用容器证据 | `[~]` |
| 恢复时延 | 隔离 JVM 观测 `4417 ms`；完整应用未观测 | 隔离 JVM 观测 `4417 ms`；完整应用未观测 | 2026-09-27 fresh `test-workflow-process-restart.sh`（此前 `2731 ms`）；不是应用 SLO | `[~]` |
| 业务结果在 Inbox 提交前丢失 | 隔离 JVM 回滚后单次业务副作用；本地完整应用链路未覆盖 | 隔离 JVM 回滚后单次业务副作用；Rabbit/应用完整链路未覆盖 | fresh `RabbitProcessRestartIntegrationTest` 1/1 | `[~]` |
| 业务结果在提交后、ACK 前丢失 | 本地 ACK 窗口未覆盖 | Rabbit ACK 前 redelivery 已覆盖 | `RabbitAckCrashIntegrationTest` 三阶段脚本 | `[~]` |
| 结果重复、乱序、旧轮次、未知版本、过期 | 聚焦测试及部分 H2/MySQL 证据；完整本地运行矩阵未覆盖 | 聚焦测试及部分 MySQL/Rabbit 证据；完整跨 owner 运行矩阵未覆盖 | `WorkflowUpmsAutomaticTaskRabbitIntegrationTest`、`WorkflowLeaveBusinessTaskIntegrationTest` | `[~]` |
| 四组 single/cloud enabled/disabled 重启矩阵 | 未完成 | 未完成 | `PROGRESS.md` 明确列为待补 | `[ ]` |

`[x]` 表示该维度有对应的真实运行证据，`[~]` 表示只有局部或聚焦证据，`[ ]` 表示仍需运行验收。矩阵中的“未覆盖”不是失败结论；在 Docker daemon 不可用时不能把它改写为通过。

静态收口命令：

```text
bash -n scripts/test-workflow-process-restart.sh
node --test scripts/test-workflow-process-restart.test.mjs
```

真实收口仍需在隔离环境执行完整应用容器重启、single/cloud 响应丢失、结果丢失/乱序/过期矩阵，并保存每个场景的 requestId、eventId、operationId、状态转移和恢复耗时。运行前必须确认脚本只清理自身创建的临时资源。

## 2026-09-27 结果确认窗口与矩阵复核

`WorkflowUpmsAutomaticTaskRabbitIntegrationTest` 现为 5/5。新增场景先让 UPMS
提交业务结果，再让 Workflow owner 离线，并用 MySQL trigger 使 Rabbit 传输已确认但
Outbox `markDelivered` 更新失败；断言源记录保持 `IN_FLIGHT`，租约过期后以同一
`eventId` 重放，Workflow 恢复后最终只产生一条 booking 并收敛到 APPROVED/BOOKED。
同一测试套件已有重复请求只产生一条 booking、timer 先补偿后忽略迟到登记结果、旧轮次
`IGNORED`、未知 schema 和同 eventId 不同 payload 隔离，以及登记/补偿竞争场景。

同日真实脚本结果：

- `scripts/test-reliable-rabbit.sh`：Rabbit owner/listener 6/6、broker restart 2/2、ACK 前 JVM kill/redelivery 1/1；
- `scripts/test-workflow-process-restart.sh`：Workflow owner 提交窗口恢复 1/1，业务回滚后由新 JVM 接管，单次副作用，观测墙钟约 2.8--4.4 秒；
- 以上耗时是隔离测试脚本的观测值，不是完整应用恢复 SLO，也不代表所有依赖同时重启。

因此结果丢失确认窗口、Rabbit broker/consumer 重启和 owner JVM 提交窗口已有可复查证据；
截至该历史窗口，完整 UPMS/Workflow/Gateway/依赖同时重启、四组 single/cloud
enabled/disabled 矩阵以及第三方通知回执仍标为未覆盖。后续结果见下节。

## 2026-09-28 最新运行矩阵与个人电脑适配

### 四组运行证据

本轮使用独立 Compose project、随机宿主端口和预构建镜像；没有停止或删除其他项目。四组结果如下：

| 场景 | 真实证据 | 结果与观测 | 边界 |
| --- | --- | --- | --- |
| single + enabled | `runtime-evidence/single-enabled-20260927.json`；`runtime-evidence/full-application-restart-20260927t01091790471346z-91250/evidence.json` | 本地可靠传输、审批通知和状态回写通过；4 个服务完整停止后恢复，`recovery_ms=48513`，重启后 acceptance 通过 | 不证明 Rabbit、压力或 HA |
| single + disabled | `runtime-evidence/single-disabled-reenable-20260927/evidence.json` | 禁用观察 54 秒，Outbox 保持 `PENDING`、无 consumer；重新启用后 Outbox/Inbox 各一次，业务恢复为 `IN_REVIEW/STARTED` | 依赖显式 Flowable schema 维护窗口；不证明并发容量 |
| cloud + enabled | `runtime-evidence/cloud-enabled-acceptance-20260927.json`；`runtime-evidence/cloud-full-application-restart-20260927.json` | Rabbit/Workflow 真实 HTTP acceptance 通过；13 个服务完整停止并按依赖顺序恢复，`recovery_ms=166814`，Nacos 注册断言通过 | 不证明第三方回执、数据库 HA 或生产 SLO |
| cloud + disabled | `runtime-evidence/cloud-disabled-reenable-20260927/evidence.json` | 禁用观察 37 秒，Outbox 保持 `PENDING`、Workflow consumer 日志匹配为 0；重新启用后 36 秒内 Outbox/Inbox 各一次，队列清空，业务恢复为 `IN_REVIEW/STARTED` | 重新启用前需完成 ACT_* schema bootstrap；不证明压力容量 |

Cloud disabled 证据文件 SHA-256 为 `91a5591178319e8f826be66f8a560ad78d8f30dd9ba391e20b1866d58f683526`。上述秒数和 `recovery_ms` 是本机隔离运行观测，不能直接转成生产 SLO。

### 个人电脑资源审计

2026-09-28 本机复核：Docker Server `29.4.0`（aarch64），10 CPU，Docker VM 内存
`8,393,289,728` bytes（约 7.82 GiB）；宿主数据盘 460 GiB 总量、397 GiB 已用、39 GiB
可用（92%）。当前 Docker 资源约为 images 30.57 GiB、local volumes 48.22 GiB、build
cache 11.21 GiB。Compose 默认最大堆约为 cloud 后端 2.56 GiB（加 Nacos 约 3.07 GiB），
AI 开启后还会增加约 512 MiB；single 默认最大堆为 768 MiB。此前并行旧项目已出现
退出码 137、`SIGBUS` 和 `no space left on device`，因此这些不是适合个人电脑默认并行执行的场景。

推荐执行层级：

1. **Tier 1，本机默认**：架构/运行配置检查、`bash -n`、聚焦 Maven/Node 测试和脚本静态测试；不启动完整应用栈。
2. **Tier 2，本机可选**：一次只启动 single 或 cloud 一个模式，关闭 AI 与 Workflow 集群；使用预构建镜像和低堆配置执行 `verify-*` 或单次重启演练。每次运行前确认 Docker daemon 可用、宿主至少保留 10 GiB 空间，并记录实际内存/耗时。
3. **Tier 3，CI/专用机器**：四组矩阵反复运行、cloud 与 single 并行、Workflow/SBA 多副本故障注入、压力/容量/长时间浸泡、数据库 HA/跨地域、真实 DashScope/通知厂商送达。这些测试应避免作为普通个人电脑的默认步骤。

本机资源不足时，正确结果是输出 `Environment blocked` 或记录 OOM/磁盘阻塞并停止追加容器；不得把未执行的运行验收改写为通过。完整重启脚本已经支持通过 `FULL_RESTART_MODES=cloud` 或 `FULL_RESTART_MODES=single` 分开执行，且使用低堆覆盖和自身 project 清理。
