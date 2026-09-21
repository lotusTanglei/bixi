# 阶段 1A 验收记录

日期：2026-09-21。结论：可选引擎和业务模块装配已实现并验证；不是阶段一审批闭环验收，也不是最终四组可靠性验收。

## 环境与修改

本地 Java 17.0.2 / Maven 3.9.9 / Docker 29.4.0；Compose 使用项目固定的 MySQL 8.4.3、Redis 7.4.2、RabbitMQ 4.0.5、Nacos 2.4.3。未升级 Flowable 7.1.0。配置测试使用真实 H2 数据库，不以其结果宣称 MySQL 并发或锁语义通过。

- common-workflow 在 Boot 自动配置导入阶段执行工作流开关；仅 BPMN starter 进入生产依赖。关闭/缺省时屏蔽全部 Flowable 自动配置，启用时屏蔽未使用的 app/CMMN/DMN/IDM/event registry/LDAP。
- schema-update 默认 false，历史和执行器参数实际应用；已验证应用 DataSource / transaction manager 与引擎一致。
- 30 个现有业务组件/Mapper、Workflow Feign 接口受统一显式开关控制；single 聚合同一 workflow-biz，独立入口仅 cloud 注册。
- 单体/独立工作流配置、Compose 环境变量、`make workflow-test` 和额外引擎 CI 作业补齐。业务 Controller、Service、Mapper 没有复制。

## 自动测试与门禁

| 命令/场景 | 实际结果 | 证明范围 |
| --- | --- | --- |
| `make architecture-check` | exit 0 | 现有依赖方向、双 reactor validate |
| `make runtime-config-check` | exit 0 | 现有运行资产/配置/敏感值静态规则 |
| `make backend-cloud-ci` | exit 0；43 个测试 | 全 reactor clean verify；包括 6 引擎 + 8 业务装配测试；随后仅新增历史 NONE 测试 |
| `make backend-single-ci` | exit 0；43 个测试 | single 依赖 reactor clean verify；包含真实 application.yml 默认值、入口和开关测试 |
| common 配置定向测试，生产 process-only classpath | 7/7，exit 0 | 启停、配置、引擎行为、本地事务 |
| common 配置定向测试，`-Pcloud,workflow-all-engines-test` | 同样 7/7，exit 0 | 所有额外 Flowable 引擎进入 classpath 仍被正确阻止 |
| `make workflow-test`（最终代码） | cloud 15 个 / single 16 个装配用例，两个构建均 exit 0 | 新增历史 NONE 测试后的双 Profile 共同断言 |
| `make frontend-ci` | exit 0 | npm ci、既有 ESLint 和生产构建；本批未修改前端业务 |
| `codegraph sync .` | exit 0，47 changed files | 本地索引更新，未提交索引 |
| `git diff --check` | exit 0 | 最终差异空白检查 |

规格审查及独立代码质量审查未发现本批必须修正的问题；历史非默认值覆盖建议已落实。

真实引擎测试断言：未配置/false/非 true 值不创建引擎、执行器、配置 Bean 或 ACT 表；显式启用只有一个 BPMN 引擎；FULL/NONE 历史可配置；执行器可开可关；schema 默认不创建；部署用户任务、发起、办理和历史完成；测试业务写入和流程写入在同一事务回滚。该事务用例尚未覆盖现有 wf_* 扩展表和未来 Outbox。

业务装配共 8 个用例：single/cloud 的 false/default，single 排除独立启动类，两种模式启用时真实引擎/业务服务/Controller/全部九个 Mapper 同时创建，cloud 禁用时原生 Feign 扫描不注册 Workflow 客户端。single 另有一项读取真实配置资源的测试，防止依赖 jar 的 application.yml 覆盖 single 默认模式。

## 红灯证据与调查

1. 原代码在 false/default 下仍装配业务 Bean：4 个上下文用例失败，工作流 Mapper 要求 SqlSessionFactory。统一条件后通过。
2. 原 starter 默认创建多个引擎/表，关闭 Bixi 配置器不能关闭 Flowable：真实上下文测试失败。导入过滤与 process-only 依赖后通过。
3. `Boolean` 转换接受 `yes`，与 `@ConditionalOnProperty(havingValue="true")` 不一致：专门用例复现仍创建引擎。筛选器改为相同的字符串比较语义后通过。
4. 最初业务测试直接以 Class 注册 Flowable 自动配置触发可选 App 配置类反射错误；采用真实 Boot 导入选择器的元数据路径后通过。没有为测试额外增加生产运行依赖。

## 运行级核心回归

| 组合/命令 | 实际结果 | 限制 |
| --- | --- | --- |
| `make init-env && make doctor` | exit 0 | 既有 .env 只补缺省配置；未输出或提交凭据 |
| `make start-single`，默认 Workflow disabled | exit 0，应用/前端健康 | 仅 MySQL/Redis/RabbitMQ/single/frontend 运行，无 Gateway/Nacos/独立 Workflow |
| `make verify-single` | exit 0 | 登录、用户、角色、菜单、CRUD、权限拒绝、非法请求、审计通过 |
| single 启动后查询 MySQL information_schema | ACT_* 表数量 0，exit 0 | 与上下文测试共同证明本轮默认关闭未初始化引擎库 |
| `make start-cloud`，默认 Workflow disabled | exit 0，Gateway/Auth/UPMS/前端健康 | 当前 Compose 核心编排不启动独立 Workflow |
| `make verify-cloud` | exit 0 | 同一黑盒脚本的核心断言通过 |

验收后执行 `make stop`，仅停止本次 Bixi 本地编排、保留数据卷；没有部署到外部环境。

这两次核心回归没有覆盖工作流启用业务、关闭时持久化事件积压、重新启用恢复或服务中途禁用。运行报告有权限/日志的实测结果，但不是工作流审批权限/日志实测。

## 后续必须完成

完整清单保留在 [PROGRESS.md](PROGRESS.md)，当前下一批为 1B 契约/适配，随后 1C–1F 请假审批/SQL/前端/权限/完整四组验证。继续阶段二可靠协作、阶段三集群故障与迁移、阶段四审批扩展。现有 mock 被测 Service 的旧测试仍存在，未把它们当成业务正确性证据。
