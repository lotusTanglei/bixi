# 既有数据库增量升级

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

## 工作流表结构

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
3. `20260921_workflow_menus.sql`：添加请假及工作流入口，授予管理员角色。菜单保留在数据库，启停由服务端开关过滤。实际使用菜单 ID 为 5010–5014、6000–6004、6011–6012、6021–6023、6031–6032，并依赖已有的示例业务目录 5000。

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

这个批次只保证同一操作者、同一 requestId 的 START 幂等。不同 requestId 仍可关联相同业务，业务轮次唯一约束须等 2C 可信启动入口切换后再加入。`wf_command` 不使用逻辑删除，也不自动清理；删除命令会丢失对应请求的去重与结果恢复能力。流程写请求在升级后必须传标准小写 UUID，升级浏览器和服务调用方应与后端同步。

迁移安全回归使用独立临时 MySQL 容器，不运行 Maven：

```bash
python3 scripts/test-workflow-command-migration.py
```
