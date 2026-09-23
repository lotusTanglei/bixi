# 阶段二 2G：独立 owner 进程重启证据

更新：2026-09-22。真实 Docker/MySQL/Rabbit 杀进程演练已执行通过；本记录仍不代表完整应用容器重启、精确恢复时延或阶段二全部完成。

## 已实现

- 新增 `RabbitProcessRestartIntegrationTest` 两阶段测试：
  - `hold` 阶段在 Inbox 处理事务已经写入业务表、但尚未提交时写入独立连接 marker 并保持进程存活；
  - 外部脚本确认 marker 后只杀该测试 JVM；
  - `resume` 阶段由全新 JVM 接管过期 lease，验证回滚后的业务副作用只写入一次，Inbox 进入 `PROCESSED`。
- 新增 `scripts/test-workflow-process-restart.sh`，每次创建带 PID 后缀的临时 MySQL 容器，轮询持久化 marker 后发送 `SIGKILL`，再启动恢复 JVM；退出时只删除本脚本创建的容器和卷。
- 新增 `WorkflowUpmsAutomaticTaskRabbitIntegrationTest.realRabbitBookingAndCompensationRaceConvergesToOneCancellation`，并发执行登记请求与补偿请求，检查 booking 单行、`CANCELED` 终态和流程终止。
- 新增 `RabbitAckCrashIntegrationTest` 三阶段真实 Rabbit 用例。seed 阶段发布持久消息；hold 阶段在业务事务和 Inbox `PROCESSED` 已提交、Rabbit manual ack 尚未执行时写 marker；外部脚本杀掉 consumer JVM；resume 阶段由新 JVM 接收同一 Rabbit redelivery，确认 handler 不会再次执行、业务计数保持一次且队列最终清空。
- `RabbitInboxListener` 增加包内 before-ack 测试屏障；公开构造器仍固定使用 no-op，生产配置和确认顺序不变。`scripts/test-reliable-rabbit.sh` 在同一临时 MySQL/Rabbit 项目中自动执行该故障窗口，并只杀脚本启动的测试 JVM。

## 已执行

| 命令 | 结果 |
| --- | --- |
| `scripts/test-workflow-process-restart.sh` | 通过；临时 MySQL 中确认 hold JVM 到达未提交业务事务 marker 后被 `SIGKILL`，新 JVM 接管过期 lease，业务副作用一次，Inbox 为 `PROCESSED` |
| `scripts/test-reliable-rabbit.sh` | 通过；MySQL 8.0.45 / `REPEATABLE-READ`，Rabbit owner/listener 6/6、broker 重启 seed/consume 2/2、真实 Workflow/UPMS 自动任务 5/5 |
| 同一 Rabbit 脚本的 ACK 崩溃三阶段 | seed 1/1；hold JVM 在 `COMMITTED_BEFORE_ACK` marker 后被 `SIGKILL`；resume 1/1，redelivery 被持久 Inbox 去重，业务值仍为 1，队列清空 |
| `make architecture-check runtime-config-check`、`make workflow-cluster-config` | 均通过 |

## 未执行与限制

当前演练杀的是隔离 Maven 测试 JVM，已覆盖 Inbox 事务提交前和 Rabbit ACK 前两个确定窗口，但没有启动并杀掉完整 UPMS/Workflow 应用容器，也没有覆盖网关连接丢失或 single 完整进程。脚本尚未输出 marker 到恢复完成的精确耗时，不能据此填写恢复时间目标。完整自动任务恢复报告、四组运行态故障矩阵和应用容器级重启仍需后续补齐。
