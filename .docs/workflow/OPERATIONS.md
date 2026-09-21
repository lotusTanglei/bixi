# 工作流启停与验证（开发中）

当前实现和未完成范围见 [PROGRESS.md](PROGRESS.md)。阶段一请假闭环和四组基础启停已通过，见 [阶段报告](EVIDENCE-STAGE1.md)；阶段二可靠协作正在实施。

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

single 在同一制品内包含 workflow-biz，无需另起 Workflow 服务。Compose 将 `.env` 中 `WORKFLOW_ENABLED`、`WORKFLOW_SCHEMA_UPDATE`、`WORKFLOW_ASYNC_EXECUTOR_ACTIVATE` 传递给应用。默认 single 编排启动 MySQL、Redis、RabbitMQ；现有 Rabbit 消费者和健康探针不是本次工作流引入。已在停用这些既有 Rabbit 入口、RabbitMQ 停止的情况下通过阶段一核心及审批验收；持久化异步重启恢复仍待阶段二实施。

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

使用 Java 17。Makefile 在 macOS 自动选取 Java 17，直接调用 Maven 时需自行设置 JAVA_HOME。

开发库启用：在忽略的 `.env` 设置 `WORKFLOW_ENABLED=true`；新建可丢弃数据库再设置 `WORKFLOW_SCHEMA_UPDATE=true`，已有库先执行增量迁移。随后运行相应 `make start-single` / `make start-cloud` 和 `make verify-single` / `make verify-cloud`。相同验收脚本按开关选择完整请假审批或关闭检查。关闭改回 `WORKFLOW_ENABLED=false` 并重启相应应用；数据保留，菜单和接口消失。`make start-single` 会停止同项目的 cloud 应用及 Nacos。

请假菜单用于保存草稿和提交；审批人在“我的待办”查看申请、通过或拒绝结束。委派任务由受托人处理后交回原办理人。流程定义页可重复部署内置请假示例；本阶段该入口不提供任意模型设计器。通知失败时，申请人可在已有流程绑定的单据上执行“同步状态”；SUBMITTING 且没有流程绑定的歧义状态不得盲目重提，可靠恢复由阶段二补齐。

### single 不运行 RabbitMQ 的验证

在可丢弃的本地项目中完成启用工作流的 `make start-single`，然后使用以下覆盖配置隔离原有 Rabbit 探针与消费者。它不会禁用工作流处理；仅用于核实工作流新增能力的依赖边界，其他需要 Rabbit 的功能在此配置下不可用。

```bash
docker compose --profile single stop rabbitmq
docker compose -f compose.yaml -f deploy/docker/compose.workflow-single-no-mq.yaml \
  --profile single up -d --no-deps --no-build --wait single
docker compose --profile single exec -T frontend-single nginx -s reload
make verify-single
```

重新创建后端容器可能改变 Docker IP；Nginx 重新加载可更新上游解析。标准 `make start-single` / `make start-cloud` 现已在后端就绪后执行此操作。恢复默认依赖使用 `make start-single`。

## 数据库和流程升级

`WORKFLOW_SCHEMA_UPDATE=false` 为安全默认值。仅对可丢弃的开发库可显式改为 true 让 Flowable 初始化表；生产应使用与 Flowable 7.1.0 对应的受控迁移，先完成数据库变更再启用副本。受控迁移脚本、旧版本兼容验收和部署去重仍在阶段三清单中，当前不能宣称已实现。

关闭开关不会删除 ACT_* 或工作流扩展表。仓库初始化 SQL 含破坏性重建操作，已有库禁止整体重放。扩展表继承字段的升级使用[独立增量迁移](../../bixi-project-documents/sql/migrations/README.md)。旧 BPMN 的抽象监听器引用已修正；新增 `demo_leave_approval` 可在启用后的类路径自动部署中加载。重复部署及审批基础的证据见 [1B 记录](EVIDENCE-1B.md)，四组实际运行结果见 [阶段一报告](EVIDENCE-STAGE1.md)。

## 模式迁移与恢复边界

完成阶段二后，模式迁移仍需显式停机：停止新请求和业务提交，停止两侧投递器/消费者并确认在途事务结束，备份引擎/业务/Outbox/inbox；对账 MQ 已确认、未确认和待投递记录；迁移数据库并保留 eventId、请求标识、轮次和去重记录；切换模式后仅启动一种投递适配；重放积压、对账，再恢复流量。禁止本地和 Rabbit 适配同时领取同一待投递集合。

这是一套代码可双模部署的边界说明，尚不是已验证的迁移工具。失败事件管理、人工重试、补偿、性能和实测恢复时间将在相应阶段补齐；当前没有数据库高可用或跨地域容灾证据。
