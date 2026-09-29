# 工作流启停与验证（开发中）

当前实现和未完成范围见 [PROGRESS.md](PROGRESS.md)。阶段一请假闭环和四组基础启停已通过，见 [阶段报告](EVIDENCE-STAGE1.md)；阶段二 B 的 Flowable 表单/BPMN 切片、可靠双模链路和 single/cloud enabled/disabled 运行矩阵已通过复核，见 [表单/BPMN 证据](EVIDENCE-4F.md) 与 [2G 证据](EVIDENCE-2G.md)。结果丢失组合矩阵、生产级恢复目标以及路线图第二阶段 A/C 的剩余项仍未完成。

## 配置

```yaml
workflow:
  enabled: false
  database-schema-update: 'false'
  async-executor-activate: true
  history-level: full
```

升级既有独立 Workflow 服务时需显式设置 `WORKFLOW_ENABLED=true`：旧代码缺省启用，新代码缺省关闭。对已有 Flowable 表保持 `WORKFLOW_SCHEMA_UPDATE=false`，先核实兼容版本再启动。

`workflow.enabled=true` 才启用；关闭/缺省时不创建 Flowable 引擎、执行器和工作流业务组件。重启应用使开关生效；当前不承诺在线热切换。后续事件投递器必须使用同一开关并在禁用时保留记录、不消费。

启用 Workflow 必须同时设置 `BIXI_RELIABLE_ENABLED=true`。cloud 还必须设置 `BIXI_RELIABLE_RABBIT_ENABLED=true`，single 使用本地持久化适配器时保持 `BIXI_RELIABLE_RABBIT_ENABLED=false`。`scripts/bixi.sh` 在访问 Docker 前校验这些依赖，也可先执行 `scripts/bixi.sh validate-config cloud|single`；应用装配还会拒绝缺少持久化事件发布器的请假工作流。工作流关闭时两个可靠投递开关可保持关闭。

single 在同一制品内包含 workflow-biz，无需另起 Workflow 服务。Compose 将 `.env` 中的工作流及可靠投递配置传递给应用。请假提交先在同一事务内保存 `SUBMITTING` 业务状态、`ACCEPTED` 命令和 Outbox 事件，再由本地持久化适配器启动流程并通过生命周期事件绑定实例。标准 `make start-single` 只启动 MySQL、Redis、single 和前端；通知也选择进程内投递器，因此 single 不需要 RabbitMQ 镜像、端口、健康探针或运行容器。

cloud 的独立 Workflow 应用同样读取这三个变量，应用名 `bixi-workflow-biz`、容器内端口 5008。`make start-cloud` 根据 `.env` 的启用值选择是否构建/启动 `workflow` 服务；网关仅启用时注册 `/admin/workflow/**`，优先于通用 `/admin/**` 路由。UPMS 与 Gateway 使用同一环境开关，菜单缓存按开关状态隔离。独立配置见 `deploy/nacos/bixi-workflow-biz-dev.yml`。四组真实运行结果仍以进度记录为准。

## 开发命令

```bash
make init-env
make doctor
make workflow-test
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci

# 默认关闭工作流，先验证核心功能
make start-single
make verify-single
make start-cloud
make verify-cloud
```

`make workflow-test` 在两个 Maven Profile 下执行真实引擎/请假领域集成、HTTP 契约、本地适配、权限、参数校验、菜单与装配测试（H2）。`make workflow-mysql-test` 在独立 MySQL 容器中依次执行两种 Profile 的审批事务、关闭历史回写和请假并发测试；不同历史配置之间重建专用测试库，避免引擎持久化配置互相影响。可用 `WORKFLOW_TEST_MYSQL_IMAGE` 指定已缓存的兼容镜像。测试库随容器清理，不要将直接运行集成测试时的 JDBC 参数指向业务库。额外引擎意外进入依赖树时的关闭测试可单独执行：

```bash
mvn -Pcloud,workflow-all-engines-test -pl bixi-common/bixi-common-workflow -am \
  -Dtest=WorkflowEngineConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### 个人电脑测试分层

本项目的完整 cloud 栈包含 Nacos、RabbitMQ、Gateway、多个 Spring Boot 应用和前端，
不适合在个人电脑上与 single、AI 或多副本项目并行启动。当前基准机的 Docker VM 约 7.82 GiB
内存，宿主数据盘仅约 39 GiB 可用；先执行静态/聚焦测试，再一次只启动一个 Compose 模式。

- 默认本机：`make architecture-check`、`make runtime-config-check`、`make workflow-test`、聚焦 Node/Maven 测试、`bash -n` 和 `make full-application-restart-static-test`。
- 本机可选：单独执行 `make start-single && make verify-single` 或 `make start-cloud && make verify-cloud`；保持 `AI_ENABLED=false`、`WORKFLOW_CLUSTER_ENABLED=false`，使用预构建镜像和低堆配置。
- CI/专用机器：四组矩阵重复跑、cloud 与 single 并行、Workflow/SBA 多副本故障注入、压力/容量/浸泡、数据库 HA/跨地域以及真实第三方 AI/通知送达。

开始任何 Docker 验收前先运行 `make local-test-preflight BIXI_LOCAL_TEST_MODE=static`；需要检查
Docker VM 时改用 `single`、`cloud` 或 `cluster`。直接调用脚本时，退出码 `0` 表示资源和运行时
可用，`1` 表示硬资源门槛不足，`2` 表示 Docker/探针等测试环境不可用；并行模式会保留已有
容器并输出 `WARN`，提示先停止其中一个模式。
GNU Make 对失败命令统一返回非零，因此需要区分等级时直接运行
`bash scripts/local-test-preflight.sh <mode>`。`cluster` 模式需要至少 8 GiB Docker VM 内存；
不足时转 CI 或专用机器。预检只读调用 Docker 信息和运行中容器标签，不会启动、停止、删除或
清理已有容器，并额外读取 `docker system df` 观察镜像、容器、卷和构建缓存的
`Size/Reclaimable`。可回收量达到 `BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB`（默认 20 GiB）
时只输出 `WARN`，不会把 Docker Desktop/OrbStack 的逻辑占用误当作统一的剩余容量；该命令
不可用时也只告警。临时调整本机门槛可设置 `BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB`、
`BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB` 或 `BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB`，这些覆盖值
不能替代 CI/专用机证据。

`make workflow-test` 默认不要求外部 MySQL；跨数据库可靠通知用例会在缺少
`OUTBOX_TEST_JDBC_URL` 时明确显示为 skipped，并由 `make workflow-mysql-test` 或可靠消息专用脚本
在一次性 MySQL 8 容器中执行。不要把这些用例改成 H2 默认路径：它们验证 InnoDB 的锁、隔离级别、
`FOR UPDATE` 和租约重放语义，H2 结果不能替代该证据。

会创建一次性 Docker 资源的可靠 Rabbit、Workflow/本地进程重启、一次性 MySQL、Quartz JDBC
和完整应用重启脚本都会在第一次 `docker run`/Compose `up` 前调用同一只读预检；发现已有
Bixi 容器时只终止本次测试，不会停止或清理已有容器。外部提供 `BIXI_QUARTZ_HA_JDBC_URL` 时 Quartz 脚本不拥有数据库
生命周期，因此跳过本机 Docker 预检。只有在 CI/专用机器已经有独立资源配额时，才可显式设置
`BIXI_LOCAL_PREFLIGHT_SKIP=true` 绕过这些脚本的门禁；绕过必须写入运行证据，不能把该次运行当作
个人电脑资源适配证据。

执行前确认 `docker info` 能响应并保留至少 10 GiB 磁盘空间；出现 OOM、`SIGBUS`、`no space left on device` 或 `Environment blocked` 时停止追加容器，并把该次运行记为环境阻塞。完整重启脚本默认只运行 `single`，避免个人 Docker VM 同时承载两套应用；在 CI 或专用机器上需要完整矩阵时显式设置 `FULL_RESTART_MODES=cloud,single`，也可单独设置 `FULL_RESTART_MODES=cloud`。Workflow 双副本故障演练启动前会强制调用 `scripts/local-test-preflight.sh cluster`，默认要求至少 8 GiB Docker VM 内存；仅在 CI/专用机器已有独立资源配额时才显式设置 `WORKFLOW_CLUSTER_PREFLIGHT_SKIP=true` 或通用的 `BIXI_LOCAL_PREFLIGHT_SKIP=true`，并把该绕过记录在运行证据中。证据目录位于 `.docs/workflow/runtime-evidence/`。

使用 Java 17。Makefile 在 macOS 自动选取 Java 17，直接调用 Maven 时需自行设置 JAVA_HOME。

开发库启用：在忽略的 `.env` 设置 `WORKFLOW_ENABLED=true`、`BIXI_RELIABLE_ENABLED=true`；cloud 另设 `BIXI_RELIABLE_RABBIT_ENABLED=true`，single 设为 `false`。新建可丢弃数据库可设置 `WORKFLOW_SCHEMA_UPDATE=true`，已有库必须先执行受控增量迁移。随后运行相应 `make start-single` / `make start-cloud` 和 `make verify-single` / `make verify-cloud`。相同验收脚本按开关选择完整请假审批或关闭检查；启用场景始终等待事件驱动的流程绑定并核对恢复视图。关闭改回 `WORKFLOW_ENABLED=false` 并重启相应应用；数据保留，菜单和接口消失。`make start-single` 会停止同项目的 cloud 应用及 Nacos。

请假菜单用于保存草稿和提交；审批人在“我的待办”查看申请、通过或拒绝结束。委派任务由受托人处理后交回原办理人。流程定义页支持上传不超过 1 MiB 的 BPMN，服务端在部署前校验 XML、可执行流程、候选身份、危险脚本/类表达式和已发布表单绑定；Flowable 生成的定义版本是最终版本来源。提交超时应使用浏览器保存的同一 `requestId` 重试；`SUBMITTING` 且尚未绑定流程时不得换新请求标识盲目重提。命令达到 `STARTED` 或 `REJECTED` 后，相同请求会返回已保存的终态。

### single 不运行 RabbitMQ 的验证

在可丢弃的本地项目中设置 `WORKFLOW_ENABLED=true`、`BIXI_RELIABLE_ENABLED=true`、`BIXI_RELIABLE_RABBIT_ENABLED=false`。标准 single 编排已经移除 Rabbit 依赖，不再需要覆盖 Compose 文件或额外关闭监听器：

```bash
make start-single
docker compose --profile single ps
make verify-single
```

`docker compose ps` 应只有 `mysql`、`redis`、`single`、`frontend-single`；本轮已在 Rabbit 停止时验证应用健康及完整工作流验收。重新创建后端容器可能改变 Docker IP；Nginx 重新加载可更新上游解析，标准 `make start-single` / `make start-cloud` 已在后端就绪后执行此操作。

## 数据库和流程升级

`WORKFLOW_SCHEMA_UPDATE=false` 为安全默认值。仅对可丢弃的开发库可显式改为 true 让 Flowable 初始化表；生产应使用与 Flowable 7.1.0 对应的受控迁移，先完成数据库变更再启用副本。`scripts/migrate-workflow-schema.sh` 负责停机确认、顺序执行和迁移登记；跨旧版本升级兼容、自动回滚和完整生产演练仍未完成，不能据此宣称第二阶段退出条件已满足。

关闭开关不会删除 ACT_* 或工作流扩展表。仓库初始化 SQL 含破坏性重建操作，已有库禁止整体重放。扩展表继承字段的升级使用[独立增量迁移](../../bixi-project-documents/sql/migrations/README.md)。旧 BPMN 的抽象监听器引用已修正；新增 `demo_leave_approval` 可在启用后的类路径自动部署中加载。重复部署及审批基础的证据见 [1B 记录](EVIDENCE-1B.md)，四组实际运行结果见 [阶段一报告](EVIDENCE-STAGE1.md)。

## 模式迁移与恢复边界

完成阶段二后，模式迁移仍需显式停机：停止新请求和业务提交，停止两侧投递器/消费者并确认在途事务结束，备份引擎/业务/Outbox/inbox；对账 MQ 已确认、未确认和待投递记录；迁移数据库并保留 eventId、请求标识、轮次和去重记录；切换模式后仅启动一种投递适配；重放积压、对账，再恢复流量。禁止本地和 Rabbit 适配同时领取同一待投递集合。

这是一套代码可双模部署的边界说明，尚不是已验证的在线迁移工具。失败事件管理、人工重试、补偿和脱敏对账已有实现；隔离消息重放结果与恢复审计仍不是单个原子事务，且性能、完整恢复时间、数据库高可用和跨地域容灾没有完成证据。
