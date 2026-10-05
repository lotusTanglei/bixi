# bixi-quartz

Quartz 定时任务模块，支持任务 CRUD、集群调度、受控重试和可查询的执行历史。

## 模块职责

- 定时任务管理：任务创建、修改、删除、暂停、恢复
- 立即执行与 cron 调度使用同一同步执行语义；Quartz 只在真实任务结束后释放并发锁
- 每个任务可配置 0–5 次失败重试和 1–300 秒固定间隔
- 同次触发共享 `executionId`，每次尝试记录 `attempt/maxAttempts`、`CRON/MANUAL/RECOVERY` 和节点恢复标记
- REST 非 2xx、JAR 非零退出码和任务异常均记录失败，最终失败继续抛给 Quartz
- `@DisallowConcurrentExecution` 保证同一任务的长执行不会在节点内或 JDBC 集群中重叠
- 多种调用方式（`TaskInvokFactory` 工厂模式）：
  - `SpringBeanTaskInvok` — Spring Bean 方法调用
  - `JavaClassTaskInvok` — Java 类反射调用
- `RestTaskInvok` — REST 接口调用
  - `JarTaskInvok` — JAR 包调用
- Quartz 配置：`BixiQuartzConfig`、`BixiQuartzFactory`、`BixiQuartzInvokeFactory`
- 应用启动时自动初始化任务（`BixiInitQuartzJob`）

REST 任务默认只允许 `http`/`https`，拒绝用户凭据、环回/私网/链路本地/云元数据地址，
并关闭自动重定向。连接和读取使用有限超时，默认 5 秒，最大 60 秒。个人电脑上的本地
`HttpServer` 聚焦测试可通过 `BIXI_QUARTZ_REST_ALLOWED_HOSTS=127.0.0.1` 显式放行；该
allowlist 会绕过地址范围拒绝，只应填写经过审查的固定主机名，不能用通配符。超时时间可用
`BIXI_QUARTZ_REST_TIMEOUT_MILLIS` 设置；成功响应体默认限制为 1 MiB，可通过
`BIXI_QUARTZ_REST_MAX_RESPONSE_BYTES` 调整（实现上限 16 MiB）。连接使用策略校验时解析到的
固定地址，并保留原始主机名用于 HTTP Host/SNI；生产环境仍应通过出口网络策略限制任务目标。

## 关键文件

| 文件 | 说明 |
|------|------|
| `entity/SysJob.java` | 定时任务实体 |
| `entity/SysJobRecord.java` | 任务执行记录 |
| `dto/SysJobMutationDTO.java` | 可写字段白名单、cron/任务类型/重试边界校验 |
| `util/TaskInvokFactory.java` | 任务调用工厂 |
| `util/TaskInvokUtil.java` | 同步调用、重试、执行历史和失败传播 |
| `config/BixiQuartzConfig.java` | Quartz 配置 |
| `config/BixiInitQuartzJob.java` | 启动时初始化任务 |
| `constants/JobTypeQuartzEnum.java` | 任务类型枚举 |

## 包路径

`com.lotus.bixi.quartz`

## 数据库升级

新建数据库使用 `bixi-project-documents/sql/01_schema.sql` 和 `04_indexes.sql`。既有数据库先执行 `20260925_quartz_tenant_scope.sql`，再在停止调度节点和任务管理写入的维护窗口执行 `20260926_quartz_retry_history.sql`。增量脚本会拒绝不兼容的同名列或索引，不会覆盖自定义结构。

旧执行记录保留为空的 `executionId`，并标记为一次 `LEGACY` 尝试；新记录才具备完整的触发和恢复证据。应用回退时可以保留新增列与索引，但旧版本不会写入这些字段。

## 验证

聚焦 Java、UI、Schema 及前端构建证据记录在 [Quartz Stage 2 Evidence](../../../quartz/EVIDENCE-STAGE2.md)。真实 MySQL 迁移和 cloud/single HTTP 调度验收必须在 Docker/MySQL 可用时另行执行，不能用静态 SQL 检查代替。
