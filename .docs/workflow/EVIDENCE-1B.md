# 阶段一批次 1B：契约与真实审批基础

日期：2026-09-21。完整目标仍见 [REQUEST.md](REQUEST.md)。本批次不是阶段一整体验收；请假业务、业务结果回写、共享页面、启用时运行编排及四组黑盒验收仍待完成。

## 实现

- `WorkflowService` 是传输无关的同步契约，使用 Jakarta Validation、`R` 和可反序列化的 `Page`；cloud Feign 与 single 本地适配提供相同方法。修正实例详情 URI、审批历史、分页和发现服务名 `bixi-workflow-biz`。
- 本地适配仅 enabled+single 创建，启用参数校验、权限与操作日志；无需 HTTP 回环。日志支持没有 Servlet 请求的本地调用，HTTP 元数据和参数脱敏仍保留。
- 共用审批服务执行操作权限、当前办理人、参与者数据范围和本人分页校验。发起时以登录身份覆盖申请人/发起人变量，办理时禁止改写身份与业务关联变量。
- 通过完成实例；拒绝结束保存 `rejected`，取消保存 `terminated`；拒绝不接受任意节点跳转。挂起、恢复和终态操作校验在服务端执行，数据库错误向外传播并回滚引擎事务。
- 同步完成的流程在启动返回时按实际引擎状态保存 `completed` 与结束时间，解决结束监听先于扩展记录插入的时序。
- 扩展状态由引擎的流程完成事件更新；兼容普通/terminate/error/escalation 完成事件。旧节点监听器保留兼容入口，不再把单个并行分支结束当作整个流程完成。无模型监听器和并行分支分别有真实回归。
- 委派后受托人通过 `/workflow/task/resolve` 交回原办理人，不能代替原办理人直接审批；新一轮委派以当前办理人为 owner，覆盖永久转办和重新认领后的情形。
- 旧请假/报销 BPMN 改用实际 Spring 监听器；新增独立 `demo_leave_approval` 及图形坐标，审批人由业务调用方传入。重复部署相同资源的集成用例启用 Flowable 去重。
- 规范 SQL 补齐九张表的 22 个继承字段；提供[可重复执行的增量迁移](../../bixi-project-documents/sql/migrations/README.md)。MyBatis 填充保留显式业务状态，创建/更新操作者和删除默认值的原有规则保留。
- 删除原先 mock 被测 Service 自身的 `WorkflowServiceTest`，由真实实现集成测试替代。`make workflow-test` 扩展到全部 Workflow 测试，新增 `make workflow-mysql-test` 和 GitLab MySQL 双 Profile 作业。
- 操作日志在请求线程快照当前认证用户 ID；真实异步日志监听器在清空请求/安全上下文后仍向保存契约传入原操作者。此测试只隔离下游日志存储，未 mock 日志生成或监听实现。

## 已取得的失败与成功证据

真实失败先于修复：Feign 请求发到错误的 `/instance`；single 缺少本地适配器；无参数校验；缺失 `data_status`；无服务层权限；旧 BPMN 尝试实例化抽象监听器；解决委派接口返回 404；转办或重新认领后的委派归还旧 owner；无 Servlet 的操作日志抛空指针。

审批测试使用实际 Spring 事务、MyBatis、Flowable 7.1.0 与规范 SQL。强制数据库约束失败，验证发起、完成、挂起和解决委派发生错误时，引擎与扩展记录/审批记录共同回滚；没有 mock 被测服务。

MySQL 增量迁移验证使用独立 MySQL 8.0.45：从基线提交 `f34c32f4d00d6bf8dc0f84d5cacfaabe96277772` 的旧表结构创建测试库，连续执行迁移两次。九张表由 130 列变为 152 列，列类型、默认值、可空性及额外属性与新建规范表一致。原有 completed 流程、已发布表单及角色权限关联行保留。

最终验证：`make workflow-test` 在 cloud 下 67 项、single 下 68 项全部通过（含各 25 项真实审批测试）；`WORKFLOW_TEST_MYSQL_IMAGE=mysql:8.0 make workflow-mysql-test` 在 MySQL 8.0.45 的两个 Profile 各 25 项通过，测试容器已自动清理。两次最终构建均为 BUILD SUCCESS。完成事件过滤与终止结束回归经过质量复审，无剩余发现；error/escalation 完成事件已注册，但未分别做故障实测。

`make architecture-check runtime-config-check backend-cloud-ci backend-single-ci` 通过，两次后端构建各 94 项测试通过；该完整后端门禁早于最后新增的 terminate-end 用例，新增变化随后由上述 H2/MySQL 套件验证。日志操作者与适配器专项共 33 项通过，MyBatis 填充 3 项亦包含在后端门禁中。CI 配置已增加，本地执行成功不代表远端 CI 作业已经运行。原始本地日志为 `/tmp/bixi-workflow-suite-final.log`、`/tmp/bixi-workflow-mysql-final.log`、`/tmp/bixi-workflow-1b-final-gates.log`。

## 运行方式与边界

```bash
make workflow-test
make workflow-mysql-test
# 可指定本地已缓存的兼容 MySQL 镜像；本次 mysql:8.0 实际版本为 8.0.45
WORKFLOW_TEST_MYSQL_IMAGE=mysql:8.0 make workflow-mysql-test
```

MySQL 命令创建独立容器、随机口令与回环监听端口，退出时删除测试容器及其匿名卷。脚本不访问运行环境业务库。直接设置 `WORKFLOW_TEST_JDBC_URL` 时务必使用可丢弃测试库：集成用例会重建工作流扩展表。

H2 采用 LEGACY 兼容 Flowable 7.1 的 IDENTITY DDL，仅移除规范 SQL 的 MySQL 引擎后缀和非唯一 inline KEY；MySQL 测试承担实际数据库方言与事务验证。这里的双 Profile 测试还不能证明真实 Gateway/Feign/本地适配到业务回写的全链路，后者由阶段一四组运行验收完成。

请求/任务幂等、并发竞争、Outbox/inbox、跨服务故障、持久化补偿、完整组织候选组、表单权限和并行/多实例审批仍在后续清单。日志事件发布也不等于可靠事件投递。不得据此宣称完整企业工作流或故障恢复已经交付。
