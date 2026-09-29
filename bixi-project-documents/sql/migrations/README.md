# 既有数据库增量升级

## 受控迁移入口

迁移文件按文件名中的日期和名称排序执行，并在 `bixi_schema_migration` 中逐文件登记。先在维护窗口预览完整 Phase 2 批次：

```bash
make phase2-migration-list
```

确认已停止所有应用流量、Workflow/UPMS 消费者、通知消费者、Generator 写入和 Quartz 调度节点后，使用专用环境文件执行全量 Phase 2 批次：

```bash
BIXI_ENV_FILE=/path/to/maintenance.env \
BIXI_SCHEMA_MAINTENANCE=true \
BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS \
make phase2-schema-migrate
```

脚本只在每个 SQL 成功后写入迁移账本；任一文件失败立即停止，已完成的账本记录和数据库结构保留供修复后继续。`make workflow-schema-migrate` 保留原有仅 Workflow/Rabbit 批次语义，适合只维护工作流的窗口。全量批次包含安全权限、生成器、Quartz、AI/RAG、通知以及 Workflow 迁移，避免只执行 Workflow 批次而遗漏运行时所需字段。迁移脚本不会启动或重启应用，也不会自动清理 Redis 权限缓存；按各模块下文的缓存和回滚说明操作。

## 通知投递状态

启用通知实时投递状态、失败原因和人工重试前，已有库先执行
`20260926_notice_delivery.sql`。迁移为 `sys_user_notice` 增加收件人级状态、尝试次数、最近失败原因和时间字段，并建立通知维度的投递查询索引；已有收件人默认进入 `PENDING`。脚本会校验同名列和索引的结构，发现冲突时停止，不覆盖已有数据，重复执行安全。执行期间应停止通知管理写入和通知消费者。

投递领取使用固定 5 分钟 lease：新鲜 `IN_FLIGHT` 行不会被并发发送抢占，超过 lease 的行可在下一次通知重放或人工重试时回到发送队列。`delivery_attempts` 同时作为完成/失败的 CAS fence，旧进程恢复后不能覆盖新一轮结果。人工重试会重置 `FAILED` 和已过期的 `IN_FLIGHT` 行并保留尝试次数；当前没有 `next_attempt_at` 或指数退避，失败由消息重放/人工操作触发。此状态机只覆盖站内 SSE 刷新，不代表短信、邮件、微信、Webhook 或 SBA 监控告警的投递闭环。

通知选择外部渠道前，已有库再执行 `20260926_notice_channels.sql`。该迁移为 `sys_notice` 增加 `delivery_channel`，旧通知和空值固定为 `IN_APP`。`EMAIL`、`SMS`、`WECHAT` 和 `WEBHOOK` 使用配置的 provider-neutral HTTP JSON endpoint；未启用或缺少 endpoint 时明确返回 `DISABLED`/`NOT_CONFIGURED`，不会降级为站内发送。SMS 使用收件人手机号，WECHAT 使用 openid；每条通知选择一个渠道，收件人状态仍由上述 lease/CAS 状态机记录，`sys_notice_send` 权限和操作审计继续适用。迁移会校验结构冲突并可重复执行。真实厂商 SDK、送达回执和渠道 fan-out 仍由部署方配置和验收。

启用外部厂商异步回执前，已有库再执行 `20260928_notice_receipts.sql`，并在应用配置中设置
`NOTICE_RECEIPT_ENABLED=true` 与高熵 `NOTICE_RECEIPT_SECRET`。发送请求会携带
`userNoticeId`，厂商向 `/notice/delivery/receipt` POST JSON 回执，并以
`X-Bixi-Notice-Signature: <HMAC-SHA256(raw-body)>` 签名。回执只接受
`PENDING`、`DELIVERED`、`FAILED` 三种归一状态，重复回执幂等，晚到的失败不能覆盖已确认的
`DELIVERED`；数据库仅保存状态、时间和受限字符集的回执码，不保存原始厂商 payload。未配置密钥、签名错误、租户/收件人不匹配均拒绝处理。该协议为供应商适配边界，真实厂商送达仍需在 CI 或专用环境验收。

cloud 模式的 Rabbit 通知消息还必须携带发布租户的 `tenantId`。消费者会在处理期间设置该租户上下文并在结束时恢复；缺少租户的旧格式消息不会按默认租户猜测，升级期间如队列中仍有此类消息，应从生产端重新发布。

## Quartz 租户任务标识

启用租户级 Quartz 调度前，已有数据库先在暂停任务管理写入和调度节点的维护窗口执行 `20260925_quartz_tenant_scope.sql`。该脚本要求先完成 `20260921_phase1b_tenant_backfill.sql`，拒绝空租户、非法租户和租户内同名同组任务，然后将全局 `(name, group)` 唯一键替换为 `(tenant_id, name, group)`。结构冲突不会被覆盖，脚本可重复执行。

升级后的应用在首次启动对账时会将旧的全局 Quartz identity 删除并按租户重新创建。应用回退前必须停止调度节点并清理已创建的租户前缀 Quartz identity，避免旧版本无法管理这些任务。新建数据库已由 `01_init_all_tables.sql` 使用租户级唯一键，无需执行本迁移。

## Quartz 重试与执行历史

启用 Quartz 有界重试和节点恢复历史前，已有数据库应在停止调度节点及任务管理写入的维护窗口执行 `20260926_quartz_retry_history.sql`。脚本为任务增加重试次数和间隔，为每次执行记录补充共享执行标识、尝试序号、触发类型和节点恢复标记，并建立任务时间线与同次触发重试查询索引。

迁移会先核对所有已存在的同名列和索引；结构不兼容时以 `SQLSTATE 45000` 停止，不覆盖自定义结构。缺失项逐个补齐，脚本可在中断后重复执行。旧日志保留为空的执行标识，并使用 `attempt=1`、`max_attempts=1`、`trigger_type=LEGACY`、`recovered=0`，不会伪造历史触发来源。MySQL DDL 会自动提交，失败后应修复报告的结构冲突并重跑完整脚本，不要删除旧执行记录。

## 固定来源模板组并发唯一性

启用生成器固定来源在线更新前，已有数据库执行 `20260924_generator_template_group_uniqueness.sql`。迁移为 `gen_group` 增加只映射未删除记录的生成列和唯一索引，使相同 revision 的并发安装最多产生一个活动模板组，同时允许逻辑删除后重新安装。

在维护窗口暂停模板组写入并备份 `gen_group`、`gen_template`、`gen_template_group` 后执行。脚本会先拒绝重复的未删除组名，也会拒绝同名但不兼容的列或索引；不会删除、重命名或自动合并既有组。脚本可重复执行。应用回退时保留该约束即可。来源发布和应用配置步骤见 [固定来源模板更新](../../../.docs/generator/FIXED-SOURCE-UPDATES.md)。

## 第二阶段生成器与 Quartz 权限

已有数据库升级生成器和 Quartz 的方法级权限前，执行 `20260924_phase2_permissions.sql`。它补齐代码生成查看、同步、编辑、生成、导出，以及 Quartz 任务查看、执行记录查看和记录删除权限，并仅授权未删除的管理员角色 1；普通角色继续由管理员按需授权。脚本可重复执行，兼容记录的展示字段保持不变，菜单 ID、权限、父菜单或类型冲突会停止迁移而不会覆盖自定义数据。

迁移完成后按 `menu_details`、`role_details`、`user_details` 顺序清理对应权限缓存，再验证管理员入口与低权限拒绝行为。新建数据库已由 `04_init_data.sql` 提供相同菜单和授权，无需重复执行。

## 管理与通知安全权限

升级到新增管理查询、导入/导出及通知权限的版本前，已有库先执行 `20260921_security_permissions.sql`。它补齐 18 个权限菜单及管理员 `role_id=1` 的授权；新建库已由 `04_init_data.sql` 提供相同数据，无需重复迁移。普通角色不会自动扩权，应由管理员按业务需要显式分配新增权限。

在维护窗口停止该库对应的应用和菜单/授权写入，备份 `sys_menu`、`sys_role`、`sys_role_menu`，使用支持 `DELIMITER` 的独立 MySQL 8.0+ 连接执行：

```bash
mysql --default-character-set=utf8mb4 -u <username> -p <database> \
  < bixi-project-documents/sql/migrations/20260921_security_permissions.sql
```

前置条件是规范的 InnoDB 表、未删除的 `role_id=1/code=ROLE_ADMIN` 及九个现有管理父菜单。迁移在写入前核对父菜单和权限菜单的 ID、路由、权限、父 ID、类型；发现自定义 ID/权限占用会拒绝，须人工协调后重试。兼容菜单的名称、排序、图标、可见性、审计字段等保持原样；只补缺失菜单和授权。正常执行与 `mysql --force` 下均不能绕过冲突检查，授权写入失败回滚本次新增菜单。脚本可重复执行，不改变已有授权时间，不删除业务数据。账号需要表的 `SELECT/INSERT` 和 `CREATE TEMPORARY TABLES/CREATE ROUTINE/ALTER ROUTINE/EXECUTE` 权限；过程 DDL 会隐式提交，不要嵌入其他业务事务。

成功后，**在恢复应用流量之前**，于应用实际使用的 Redis 库中按 `menu_details` → `role_details` → `user_details` 顺序清理三个 Spring Cache 区域（默认键前缀分别为 `menu_details::`、`role_details::`、`user_details::`；有自定义前缀时按实际配置）。仅清这些权限缓存，保留 Token、会话和其他数据，不执行 `FLUSHDB`。直接 SQL 不会触发应用缓存驱逐，单纯重启或重新登录不能替代这一步。启动升级后的应用后，核对管理员权限及管理页面正常、低权限账号仍被拒绝，并运行相应的 `make verify-security-cloud` / `make verify-security-single`。应用回退可保留新增菜单，不重放全量初始化 SQL。

独立、无网络端口暴露的 MySQL 迁移回归（结束后仅清理本次创建的容器）：

```bash
python3 scripts/test-security-menu-migration.py
# 可用 SECURITY_TEST_MYSQL_IMAGE 指定已缓存的 MySQL 8.0+ 镜像。
```

回归覆盖旧库升级、18 项权限与管理员授权、重复执行、自定义数据/部分安装保留、ID/权限/父菜单/管理员角色冲突及事务回滚。安全权限与下述工作流迁移互不依赖；同时升级时先应用安全权限迁移，再按工作流顺序执行，最后统一失效权限缓存。

## AI 菜单与权限

已有数据库启用 AI 对话、RAG、文档和模型配置接口前，执行 `20260926_ai_menus.sql`。脚本新增 `/ai` 菜单树及 `ai_chat_add`、`ai_rag_add`、`ai_session_*`、`ai_message_*`、`ai_document_*`、`ai_config_*` 权限，并只授予未删除的管理员角色 1；新建数据库由 `04_init_data.sql` 提供相同菜单和授权。脚本会在写入前校验菜单 ID、路由、权限、父 ID、类型和管理员角色，兼容记录的展示字段保持不变，冲突时回滚且不会覆盖自定义菜单。迁移可重复执行；执行后按 `menu_details` → `role_details` → `user_details` 顺序清理权限缓存，再验证低权限账号无法调用 AI 接口。

启用 AI 模型配置的持久化前，已有数据库还要执行 `20260928_ai_model_config.sql`。迁移会创建或补齐按 `tenant_id` 唯一的 `ai_model_config` 表，并校验已有列和唯一索引的类型；发现结构冲突或租户重复数据时停止，不覆盖配置。模型默认值和系统提示词写入业务数据库，服务重启及 cloud 多副本从同一行读取；DashScope/API provider 密钥仍只从部署环境注入，不写入数据库或配置接口响应。迁移可重复执行，应用写入期间应暂停 AI 配置编辑，完成后用两个租户分别更新并重新启动一个实例验证恢复和隔离。

## 工作流表结构

### Flowable 7.1.0 引擎表

受控 Workflow 迁移还必须按以下顺序执行三个由项目锁定的 Flowable 7.1.0 MySQL schema 资源：

1. `20260922_flowable_common_7_1_0.sql`：Flowable common/runtime 基础表、作业表、任务表及通用索引。
2. `20260922_flowable_engine_7_1_0.sql`：流程定义、执行实例、活动实例及引擎外键/版本属性。
3. `20260922_flowable_history_7_1_0.sql`：流程、活动、变量、评论和附件历史表及索引。
4. `20260922_flowable_runtime_properties.sql`：在维护窗口幂等预置 Flowable 7.1.0 默认的执行、任务关系计数属性，避免两个首启副本同时向 `ACT_GE_PROPERTY` 插入同一主键。

资源来自 `org.flowable:flowable-engine-common:7.1.0` 与
`org.flowable:flowable-engine:7.1.0` 的 `org/flowable/**/db/create/flowable.mysql.*.sql`，文件头保留 SHA-256 校验值。`scripts/migrate-workflow-schema.sh` 会按文件名排序自动执行它们，并在每个文件成功后写入 `bixi_schema_migration`；已记录的文件会跳过。不要手工改写这些上游 SQL，也不要打开 `WORKFLOW_SCHEMA_UPDATE` 让应用启动时创建表。

前三段上游脚本是一次性的空库初始化资源，第四段是可用于既有库的 Bixi 补充迁移；它保留已经存在的属性值，运行配置仍固定使用 Flowable 7.1.0 默认值 `true`。这些脚本必须在没有 Workflow 副本连接数据库的维护窗口执行。若任一段在执行中失败，先保留并检查已经创建的 `ACT_*` 表和 `bixi_schema_migration` 记录，修复原因后再重试，不要并发启动 Workflow。

`20260921_workflow_base_entity_columns.sql` 用于已有 MySQL 8.0+ 数据库，补齐九张工作流、表单及表单权限表继承的 `BaseEntity` 字段。新建数据库使用更新后的 `01_init_all_tables.sql`。

升级时选中业务数据库并执行增量脚本：

```bash
mysql --default-character-set=utf8mb4 -u <username> -p <database> \
  < bixi-project-documents/sql/migrations/20260921_workflow_base_entity_columns.sql
```

脚本要求这九张表已经存在，并要求账号具备 `ALTER` 权限；逐列查询 `information_schema`，只添加缺失字段，可以重复执行。已有流程实例的 `running/completed/terminated` 状态、表单发布状态和业务数据保持原值。新添的 `status`、`data_status`、`del_flag` 默认值为 `0`；角色表单权限关联表还补齐审计字段、租户和备注字段，旧行的创建人、更新人保持空值。

MySQL DDL 会自动提交。应用回退时保留这些兼容的新增字段即可；不要为回退重放全量初始化脚本或删除业务表。Flowable 自身的 `ACT_*` 表不在本迁移范围内。

阶段一请假闭环还需要按顺序执行：

1. `20260921_workflow_business_round.sql`：增加独立业务轮次，通知不依赖 Flowable 历史配置。旧流程保持 NULL，不自动猜测或回填轮次；旧实例不会被当成新请假业务回调。
2. `20260921_demo_leave_request.sql`：新建请假表及查询索引，保留已有表和数据。
3. `20260921_workflow_menus.sql`：添加请假及工作流入口，授予管理员角色。菜单保留在数据库，启停由服务端开关过滤。实际使用菜单 ID 为 5010–5014、6000–6005、6011–6012、6021–6023、6031–6032、6041–6042，并依赖已有的示例业务目录 5000。

这些脚本可以重复执行。新增库直接运行规范初始化文件，不需要再执行上述增量迁移。

## 菜单迁移的冲突保护

菜单脚本在写入 `sys_menu` 和 `sys_role_menu` 前自动校验既有记录：同一 ID 的路由、权限、父 ID 和类型必须与规范菜单一致；路由和权限按大小写精确比较，NULL 也必须匹配。示例业务目录 5000 必须存在且身份兼容。规范路由或权限已被其他 ID 占用时也会拒绝迁移。错误会给出冲突 ID，不会覆盖自定义菜单或给冲突菜单追加管理员授权；先核对并协调自定义 ID/路由/权限映射，再重试完整脚本。

身份兼容的既有菜单保持原样，包括自定义名称、图标、排序、可见性、审计字段及其他元数据。脚本只补齐缺失的菜单和角色 1 授权；再次执行不会修改已有行或重复授权。写入使用同一事务，后续授权失败会同时回滚本次新增菜单；即使客户端使用 `--force` 继续处理错误，也不会绕过预检执行写入。

使用独立的数据库连接在维护窗口执行，期间暂停菜单和角色授权编辑。`sys_menu` 与 `sys_role_menu` 应保持规范的 InnoDB 引擎。除表的 `SELECT`、`INSERT` 权限外，账号还需要 `CREATE TEMPORARY TABLES`、`CREATE ROUTINE`、`ALTER ROUTINE` 和 `EXECUTE` 权限；客户端须支持 `DELIMITER`（例如上述 `mysql` 命令）。脚本建立专用过程并在成功后删除；失败导致客户端提前退出时，重试会先清理该专用过程。过程 DDL 会隐式提交，因此不要将此脚本拼入其他未提交的业务事务。

可在独立、无端口暴露的临时 MySQL 容器中运行回归检查（无需 Maven，结束后只删除本脚本创建的容器）：

```bash
python3 scripts/test-workflow-menu-migration.py
# 可使用已下载的兼容镜像，例如：
WORKFLOW_TEST_MYSQL_IMAGE=public.ecr.aws/docker/library/mysql:8.4.3 \
  python3 scripts/test-workflow-menu-migration.py
```

检查覆盖自定义菜单 ID/父目录/路由/权限冲突、大小写差异、普通及 `--force` 执行、已有自定义数据保留、部分安装补齐、重复执行和授权写入失败的事务回滚。

## START 请求幂等迁移（阶段二首批）

已有阶段一数据库还需执行 `20260921_workflow_stage2_start.sql`，新库直接使用规范初始化 SQL。该脚本新增 `wf_command`、`wf_process_instance.start_request_id`，将引擎实例 ID 改为唯一键，并添加命令查询索引；历史记录保持 NULL 请求 ID，不伪造历史幂等记录。请先暂停流程写入，再用支持 `DELIMITER` 的独立 MySQL 连接执行。账号需要表的 SELECT/CREATE/ALTER/INDEX 权限及 CREATE ROUTINE/ALTER ROUTINE/EXECUTE 权限。

脚本先输出重复 `process_instance_id` 清单并拒绝迁移，也拒绝同名却非唯一、不同列或前缀列的 `uk_wf_process_instance_id`。这些预检通过前不修改业务表，即使 `mysql --force` 继续处理错误也不绕过保护；保留原数据供人工协调，不自动删重。成功后可重复执行，保留实例和已提交命令。MySQL DDL 自动提交，非预检的运行失败仍需核对已执行的 DDL；修复失败原因后重跑完整脚本。

这个批次只保证同一操作者、同一 requestId 的 START 幂等。2C 可信启动入口切换后，还必须在暂停流程发起写入的维护窗口执行 `20260924_workflow_business_occurrence.sql`。脚本先校验 `business_owner` 列和旧索引结构，只为已知的 `demo_leave_approval` / `demo_leave_request` 历史关联回填可信 owner `upms`；未知关联、字段不完整或按 owner 归并后的重复业务轮次均在 DDL 前拒绝，必须人工对账。成功后唯一约束为 `(business_owner, business_table, business_id, business_round)`，旧的 tenant 维度索引会在同一次 ALTER 中替换，作为不同命令并发到达时的 Workflow 侧最终防线。`wf_command` 不使用逻辑删除，也不自动清理；删除命令会丢失对应请求的去重与结果恢复能力。流程写请求在升级后必须传标准小写 UUID，升级浏览器和服务调用方应与后端同步。

## 请假可信发起命令迁移

已有阶段一数据库在启用可靠请假提交前还必须执行 `20260923_demo_leave_command.sql`；新库已由规范初始化 SQL 建表。受控迁移工具会通过 `*demo_leave*.sql` 选择它，并按文件名顺序在 `20260921_demo_leave_request.sql`、可靠投递表和工作流命令迁移之后执行。脚本新增 `demo_leave_command`，用于保存业务提交的稳定 `requestId`、内容摘要、业务轮次及 `ACCEPTED/STARTED/REJECTED` 结果。

迁移必须在停止请假写入和可靠消费者的维护窗口执行。账号需要读取 `information_schema`，并具备目标库的 `CREATE` 以及 `CREATE ROUTINE/ALTER ROUTINE/EXECUTE` 权限；客户端必须支持 `DELIMITER`。如果同名表已经存在，脚本会核对 InnoDB 引擎、`utf8mb4_bin` 表排序规则、全部列、唯一索引和检查约束。任何差异都会拒绝迁移，不改写既有结构或数据；`mysql --force` 也不会绕过该检查。兼容表可重复执行，现有命令保持不变。

迁移成功后再启用 `WORKFLOW_ENABLED=true` 和可靠投递。若执行失败，保留原表，核对报错中的结构冲突并人工协调后重跑完整脚本，不要删除命令记录；这些记录是提交去重和结果恢复的依据。独立 MySQL 8.0+ 回归可执行：

```bash
python3 scripts/test-workflow-command-migration.py
```

## 隔离区 owner 边界迁移

已有数据库启用按 owner 的恢复入口前还需执行 `20260924_reliable_quarantine_owner.sql`。脚本为隔离证据增加 `target_owner`，并将主键调整为 `(target_owner, evidence_id)`，使 single 共库部署中的 Workflow 与 UPMS 即使收到相同证据 ID 也不会互相覆盖或读取。

旧表没有足够信息可靠推断消息归属，因此已有行原样保留在不可路由的 `legacy` owner 下，不会被错误暴露到任一管理入口；需要时由运维人员直接查询取证。迁移必须在可靠消费者停止的维护窗口执行，可重复运行，也可从增加列或主键变更后的中断点继续。结构冲突会拒绝迁移，不覆盖自定义定义或删除证据。

该回归同时覆盖 `wf_command` 与 `demo_leave_command` 的干净安装、重复执行、规范结构一致性、唯一约束、冲突拒绝和数据保留。

## 工作流表单版本绑定迁移

已有数据库启用第二阶段表单运行时前还需执行 `20260924_workflow_form_binding.sql`。脚本为流程定义增加发布时固定的 `form_version_id`，为流程实例增加不可变的 `form_id/form_version_id`，并为字段权限增加表单版本、流程定义和任务节点作用域；历史定义和实例保留为 NULL，不猜测其曾使用的表单版本。

迁移在写入前拒绝重复的 `(form_id, version)` 或 `process_definition_id`，也拒绝孤立的既有版本引用；随后补唯一索引、查询索引和 RESTRICT 外键。必须在停止工作流部署、发起、审批和表单版本写入的维护窗口执行。脚本可重复运行；结构或数据冲突需人工协调，不能通过删除历史实例、版本或表单快照绕过。

已有数据库还需执行 `20260924_workflow_form_menus.sql`，为管理员补充表单列表、设计器、版本和字段权限路由，以及 `workflow_form_view|add|edit|del` 按钮权限。脚本要求工作流父菜单 6000 和未删除的管理员角色 1 已存在；写入前会校验菜单 ID、路由、权限、父 ID 和类型，遇到自定义占用时拒绝覆盖。迁移可重复执行，已有兼容菜单的名称、图标、排序等展示字段保持不变。执行后按菜单、角色、用户顺序失效相应权限缓存，再验证管理员可访问且低权限账号仍被服务端拒绝。

## 可靠投递恢复审计迁移

启用 Workflow 或 UPMS owner 的恢复查询和人工重试前，已有数据库还需执行 `20260922_workflow_recovery_audit.sql`。该脚本只新增 `wf_recovery_audit`，不修改 Outbox/Inbox/隔离区历史记录；重复执行安全。两个 owner 的失败重试均使用状态 CAS，审计记录与该 CAS 在同一本地数据库事务中提交，保存操作者、owner、事件 ID、原因和是否实际变更，不保存消息正文；审计失败会回滚重试。共享恢复页通过 owner 切换访问两个入口。

启用带稳定 `requestId` 的事件重试、命令/业务任务对账前，已有数据库还需在上述审计表迁移之后执行 `20260926_workflow_recovery_request.sql`。脚本为两个 owner 的审计表增加请求身份、expected/current 状态、资源身份、结果和完成时间，并建立 `(owner, action, request_id)` 唯一约束；历史审计行保留为空请求身份，不伪造恢复结果。脚本可重复执行，必须在恢复写入暂停的维护窗口使用支持 `DELIMITER` 的独立 MySQL 连接执行，账号需要 `information_schema` 读取及 `ALTER/INDEX` 权限。

迁移安全回归使用独立临时 MySQL 容器，不运行 Maven：

```bash
python3 scripts/test-workflow-command-migration.py
```
