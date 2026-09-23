# 阶段三 3B：双副本配置、异步边界与故障接管证据

更新：2026-09-22。此记录覆盖源码、配置、H2/Flowable 验证，以及隔离 Docker 环境中的真实 MySQL/Rabbit、双 Workflow 副本、受控迁移和锁过期接管。它不代表数据库高可用或跨地域容灾。

## 已实现

- `compose.workflow-cluster.yaml` 通过 `extends` 复用 cloud `workflow` 制品，提供 `workflow-a`/`workflow-b` 两个副本；两者共享 MySQL/Nacos/网络，不发布宿主端口，并分别固定 `WORKFLOW_LOCK_OWNER=workflow-a|workflow-b`。
- `scripts/bixi.sh` 在 `WORKFLOW_CLUSTER_ENABLED=true` 时自动加载覆盖层，启动和停止两个副本；默认仍是单副本 `workflow`。
- `demo_leave_approval_v3.bpmn20.xml` 保留同一 process key 和 v2 的事件/Delegate 契约，只把登记与补偿 service task 标记为显式 `flowable:async=true`；新增 `POST /workflow/definition/deploy-demo/v3`，部署必须由操作员显式调用。
- `scripts/migrate-workflow-schema.sh` 强制 `WORKFLOW_SCHEMA_UPDATE=false`、维护开关、一次性确认和 Workflow 副本已停止，按持久化迁移记录执行受控 SQL；不把多副本启动时的自动 schema update 当作迁移方案。
- `scripts/test-workflow-cluster-failover.sh` 每次创建独立 Compose 项目、数据库、Nacos namespace 和随机宿主端口，只允许终止该临时项目内已核对 label 的 Workflow 容器，结束后删除临时卷。
- `scripts/workflow-cluster-failover.mjs` 通过受控数据库屏障分别制造异步 Job 和 Timer Job 已领取但未完成的窗口，杀死持锁节点并记录存活节点接管时间。Timer 屏障只锁目标 `ACT_RU_JOB.ID_` 的插入间隙，不再锁整张 `ACT_RU_JOB`；整表锁会阻塞 Flowable 过期重置线程先扫描可执行 Job，导致无法继续清理过期 Timer 锁。故障收敛后，脚本再以 30 秒停止上限优雅停止一个副本，验证 Nacos 摘除、存活副本处理新流程及停止副本重新加入。

## 已执行

| 命令 | 结果 |
| --- | --- |
| `mvn -Pcloud -pl bixi-module/bixi-workflow-biz -am -Dtest=WorkflowLeaveBusinessTaskIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | `BUILD SUCCESS`，4/4 通过；包含 v2/v3 并存、旧实例完成、新实例异步 job 执行 |
| `make workflow-cluster-config` | 通过；只启用 `workflow-a/workflow-b`，owner 不同且无重复端口 |
| `make runtime-config-check` | 通过 |
| `bash -n scripts/migrate-workflow-schema.sh` | 通过 |
| `node --test scripts/workflow-cluster-failover.test.mjs` | 12/12 通过；覆盖类初始化顺序、MySQL marker 刷新、失败清理、子进程强制退出、Timer 目标 ID 屏障、优雅停机链路、存量实例维护窗口及申请人/审批人隔离 |
| `make workflow-cluster-failover-test` | 通过；最新报告 `target/workflow-cluster-failover/20260922091956-84802/report.json` |
| `git diff --check` | 通过 |

## 真实故障结果

- 两个 Workflow 副本同时健康，lock owner 分别为 `workflow-a`、`workflow-b`，Nacos 健康实例数为 2；两端实际读取到异步锁 8 秒、Timer 锁 8 秒、过期扫描 1 秒和每次领取 1 条。
- v2/v3 定义均成功部署，最新定义资源为 `demo_leave_approval_v3.bpmn20.xml`；受控 Flowable 7.1.0 与可靠消息迁移在副本启动前实际落库，运行时保持 `schema-update=false`。
- 存量 v2 实例维护窗口先因 Workflow 副本仍在运行而拒绝迁移，迁移记录 before/after 为 **0/0**；两个 Workflow 副本随后均优雅停止，退出码均为 143，Nacos 健康实例数降为 0。
- 受控迁移使用匹配 schema 成功执行，迁移执行/记录为 **1/1**；副本重启并显式部署 v3 后，原存量实例仍绑定 v2，最终完成为 `completed`，请假状态为 `APPROVED`，登记状态为 `BOOKED`，Flowable dead letter 为 0。
- 异步 `requestBusiness` Job 的持锁节点 `workflow-a` 被 `SIGKILL` 后，由 `workflow-b` 在 **9406ms** 接管；原节点随后重启，双副本重新健康。
- `businessTimeout` Timer Job 的持锁节点 `workflow-a` 被 `SIGKILL` 后，由 `workflow-b` 在 **9172ms** 接管；原节点随后重启，双副本重新健康。
- 最终流程状态为 `terminated`，请假状态为 `CANCELED`，登记状态为 `CANCELED`，审批记录 1 条，Flowable dead letter 为 0。
- 七类 Outbox 均为 `DELIVERED`；登记结果因 Timer 已推进而按设计 `IGNORED`，补偿结果为 `PROCESSED`，未产生重复业务副作用。
- `workflow-a` 收到优雅停止后退出，容器状态为 `exited`、退出码 143、`OOMKilled=false` 且无 Docker state error；Nacos 健康实例数降为 1。存活的 `workflow-b` 随即处理新请假流程并绑定实例 `4b3a145e-b667-11f1-8b4b-e60fba5b287c`，随后 `workflow-a` 重启，两个固定 lock owner 和 Nacos 健康实例数恢复为 2。

## 根因修复记录

早期故障脚本曾稳定失败于 `timed out waiting for timer job lock takeover`。Flowable 7.1.0 的 Timer 获取事务先提交 `ACT_RU_TIMER_JOB` 锁，再由独立线程把同一 ID 插入 `ACT_RU_JOB`；过期重置线程按可执行 Job、Timer Job、外部任务顺序扫描。脚本原先对 `ACT_RU_JOB` 使用 `LOCK TABLES ... WRITE`，同时阻塞了重置线程的第一步，因此它永远无法到达 Timer 表清锁。改为目标主键的 InnoDB 间隙锁后，插入窗口仍可控，而普通过期扫描不再受阻；完整演练随后通过。

首次加入优雅停机探针时，脚本以管理员令牌创建请假，同时误把同一管理员设置为审批人，业务端按规则拒绝。新增回归断言后，探针改用已创建并完成登录验证的独立临时审批人；完整隔离演练随后通过。

存量实例维护窗口最初还暴露了 MySQL CLI 输出语义差异：SQL NULL 会被输出为字面值 `NULL`；归一化仅应用于可空的运行时/历史定义 ID，定义 ID 不匹配检查仍保持严格，不会把真实不一致静默放过。

## 未完成与限制

- 本轮优雅停机发生在主故障流程已经收敛之后，证明应用正常退出、注册摘除和存活副本继续接收新请求；执行中业务任务的 shutdown drain 等待时间尚未单独构造并量化，仍是阶段三剩余证据项。
- 当前只记录故障恢复时延，不是容量或性能结论；尚未按硬件、并发、延迟、错误率和积压输出性能报告。
- 单 MySQL 上的两个应用副本不证明数据库高可用，故障脚本也不验证跨地域容灾。
