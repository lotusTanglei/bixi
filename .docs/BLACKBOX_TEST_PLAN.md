# 黑盒测试计划

> 计划冻结日期：2026-10-05
>
> 适用基线：`main` / `origin/main`，`925be02`
>
> 当前状态：Acceptance 切片的核心双模黑盒已通过；下表中的后续项是下一阶段计划，不是已完成证据。

## 目标与边界

黑盒测试只通过可访问的 HTTP、数据库迁移入口和用户界面观察行为，不以单元测试、注解存在或源码静态扫描替代运行结论。测试优先保护已经冻结的两个资源：

- `/admin/dictAggregate`
- `/admin/publicParam`

cloud 和 single 必须使用同一套业务断言。下一阶段可以补充权限、租户、迁移边界和浏览器行为，但不得借测试计划顺手扩展业务功能；发现缺陷后先记录用例、实际响应和影响范围，再决定是否开独立实现切片。

## 环境矩阵

| 环境 | 组成 | 用途 | 入口 |
| --- | --- | --- | --- |
| `BB-CLOUD` | Gateway、Auth、UPMS、Nacos、MySQL、Redis、RabbitMQ、前端 | 验证网关路由、Feign 适配、异步审计和共享业务 | `make start-cloud` + `make verify-cloud` |
| `BB-SINGLE` | single、MySQL、Redis、前端 | 验证本地适配和同一业务实现 | `make start-single` + `make verify-single` |
| `BB-MIGRATION` | 临时 MySQL 8.0+、新库或存量菜单数据 | 验证初始化、重复迁移和冲突拒绝 | 迁移脚本及对应回归脚本 |
| `BB-BROWSER` | 已启动的 cloud 或 single 前端 | 验证页面交互、权限按钮和状态呈现 | 浏览器会话，单独记录模式和视口 |

每次执行前记录模式、代码基线、数据库是否新建、`.env` 开关、开始/结束时间和失败响应。可丢弃数据库可以重置；存量迁移必须使用备份或临时副本，禁止在未知生产库直接试跑。

## 用例矩阵

状态只使用 `PASS`、`FAIL`、`PLANNED`。`PASS` 必须有命令输出或保存的 HTTP/数据库证据。

| 编号 | 场景与关键断言 | 模式 | 当前状态 | 入口/证据 |
| --- | --- | --- | --- | --- |
| BB-01 | 健康检查、前端可达、API 前缀和未登录请求边界 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-02 | 管理员登录、用户信息不泄露密码、角色和权限存在 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-03 | 两个菜单路径可见，Workflow 关闭时相关入口隐藏且接口不可用 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-04 | 字典主表创建、分页过滤、详情、更新明细、删除 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-05 | 公共参数创建、按 `key` 过滤、详情、更新、删除 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-06 | 非法字段、未知 ID、空删除、非法 Excel 导入返回结构化失败且不改变数据 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-07 | XLSX 导出返回可解析二进制且包含过滤标记 | cloud/single | PASS | `scripts/acceptance.mjs` |
| BB-08 | 两资源新增/修改/删除各有正确标题、方法、路径和参数审计日志 | cloud/single | PASS | `scripts/acceptance.mjs` + `/admin/log/page` |
| BB-09 | 低权限角色：菜单不越权，view/add/edit/del/import/export 分别拒绝 | cloud/single | PLANNED | 新增隔离角色黑盒脚本 |
| BB-10 | 两租户：分页、详情、修改、删除、导出和导入不能跨租户读写 | cloud/single | PLANNED | 双租户临时数据 + HTTP 断言 |
| BB-11 | 导入空文件、非 Excel、超过 5 MB、超过 1000 行、行级校验失败及回滚 | cloud/single | PLANNED | 受控 XLSX 样本 + 数据前后快照 |
| BB-12 | 重复 `type`/`key`、并发更新和删除后详情不可读 | cloud/single | PLANNED | 并发 HTTP 请求 + 数据库核对 |
| BB-13 | 新库按 `01_schema.sql`、`02_data.sql` 初始化后菜单、授权和业务入口可用 | cloud/single | PASS | 新库安装回归记录 |
| BB-14 | 存量菜单迁移首次执行、重复执行、展示字段保留、ID/身份冲突拒绝且原子 | `BB-MIGRATION` | PASS | `20261005_acceptance_menus.sql` 回归 |
| BB-15 | 前端真实操作：筛选、分页、表单校验、权限按钮、加载/错误/空状态、导入导出 | cloud/single | PLANNED | 浏览器记录，固定视口和账号 |
| BB-16 | 前端网络失败、重复点击、切页后旧响应不覆盖当前数据 | cloud/single | PLANNED | 浏览器网络控制/录屏或 HAR |

## 执行顺序

1. 运行 `make doctor`、确认容器健康和数据库模式；失败即停止，不执行业务断言。
2. 在 `BB-CLOUD` 运行统一 Acceptance，保存 JSON 输出和 `/admin/log/page` 证据。
3. 切换 `BB-SINGLE`，运行同一脚本；对比资源、断言和审计结果，不复制测试逻辑。
4. 在临时 MySQL 上运行新库安装和存量迁移回归；重复执行和冲突场景必须保留前后数据计数。
5. 选择一个浏览器切片执行 BB-15/16；记录账号、视口、网络条件和失败截图/HAR。
6. 只有当前一阶段通过，才进入下一阶段；P0/P1 失败立即停止并建立独立缺陷切片。

## 退出条件

- BB-01 至 BB-08、BB-13、BB-14 在目标模式全部 `PASS`。
- BB-09 至 BB-12、BB-15、BB-16 在被选入本轮的环境中全部 `PASS`；未选项保持 `PLANNED`，不得写成完成。
- 不存在未解释的 HTTP 5xx、权限绕过、跨租户数据、导入部分提交或审计缺失。
- 新增缺陷均有复现请求、实际响应、数据前后状态和影响级别；修复后重新跑 cloud 与 single 受影响用例。
- 证据包含命令、代码基线、运行模式、环境开关和时间；最终执行 `codegraph sync .`、`git diff --check`。

## 明确不纳入本计划

白盒单元/集成测试、源码覆盖率、第三方供应商真实送达、Quartz/SBA 故障接管、生产级压测、跨地域容灾和完整发布回滚演练另立计划；它们不能借用本计划的 `PASS` 结论。
