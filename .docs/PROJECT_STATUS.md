# Bixi 项目状态总账

> 冻结日期：2026-10-05
>
> 唯一代码基线：`main` / `origin/main`，`925be02 feat: add phase2 acceptance CRUD resources`

本文档是本轮开发范围和验收状态的唯一总账。路线图、专项计划和旧续接提示词继续保留为历史材料；它们不能覆盖这里记录的冻结范围和实际证据。本轮不处理暂存区，也不把工作树中的其他未提交改动纳入本切片判断。

## 状态口径

- **已验证**：有本轮可复现的静态、构建、迁移或运行证据。
- **已实现，未验收**：代码已经存在，但本轮没有完成所需运行闭环。
- **未完成**：尚未实现必要链路。
- **未验证/环境阻塞**：实现可能存在，但当前没有足够环境或运行证据确认。

不使用整体完成百分比。每个能力只进入一个状态，并绑定下一步证据。

## 冻结范围

本轮只冻结并验收以下两个已有资源及其共享运行链路：

1. HTTP：`/admin/dictAggregate`
2. HTTP：`/admin/publicParam`
3. 前端入口：`/acceptance/dictAggregate/index`、`/acceptance/publicParam/index`
4. 部署模式：cloud 和 single，共用 `bixi-upms-biz` 的一套 Controller、Service、Mapper 和 API DTO。
5. 数据与权限：新库菜单、管理员角色 1 授权、存量菜单迁移、按钮权限、写操作审计。

本轮不宣称完成其他路线图阶段，也不纳入外部短信/邮件送达、Quartz/SBA 故障接管、生产恢复目标、完整多租户负向矩阵或性能/高可用演练。

## 已验证

- Acceptance 菜单稳定 ID 和管理员授权：字典 `7200-7209`，公共参数 `7210-7216`；新库 SQL 与存量迁移契约检查通过。
- 存量菜单迁移支持重复执行、保留兼容记录展示字段，并在 ID/身份冲突时拒绝写入；迁移回归和静态契约检查通过。
- Acceptance 后端 DTO、Controller、Service、Mapper、权限注解、导入失败结果和审计注解已覆盖；`AcceptanceServiceBehaviorTest` 通过 `6/6`。
- 前端 API、页面表单校验、按钮权限、加载/错误/空状态和生产构建通过；前端契约测试通过。
- `make architecture-check` 通过。
- `make runtime-config-check` 通过。
- `make verify-cloud` 通过：登录、用户信息、菜单、管理员权限、示例任务 CRUD、两组 Acceptance 资源 CRUD、筛选、详情、非法请求、未知 ID、空删除、非法导入、XLSX 导出和 6 条 Acceptance 写审计日志均通过；Workflow 关闭态菜单隐藏和接口缺失检查通过。
- `make verify-single` 通过同一套 `scripts/acceptance.mjs`，覆盖与 cloud 相同的业务断言和审计检查。

## 当前切片结论

Acceptance 切片达到退出条件，可以进入黑盒测试计划阶段。两种部署模式的实测结果如下：

| 模式 | 入口 | 运行验收 | 结论 |
| --- | --- | --- | --- |
| cloud | `http://localhost:8080`，Gateway/Auth/UPMS | `make verify-cloud` | 通过 |
| single | `http://localhost:8080`，single/Auth/UPMS | `make verify-single` | 通过 |

`scripts/acceptance.mjs` 是当前切片的统一黑盒入口；它按运行模式读取 `.env`，不会把某一种部署实现复制到另一种模式。

## 已知限制与未验证项

- 本轮证明的是两个 Acceptance 资源和既有 Demo Task 的双模运行闭环，不代表整个 Bixi 路线图完成。
- 前端已完成静态契约和生产构建验证；真实浏览器尺寸、键盘操作、跨页面并发点击和网络抖动仍按 [黑盒测试计划](BLACKBOX_TEST_PLAN.md) 执行。
- 租户隔离的完整跨租户正负矩阵、低权限角色全量矩阵、导入大小/行数上限、重复业务键和真实存量迁移冲突环境仍需按计划补跑。
- 外部短信、邮件、微信、Webhook 的供应商送达，Quartz/SBA 故障接管和生产级恢复目标不在本轮退出条件内。

## 下一步门禁

下一次开发前先从 [黑盒测试计划](BLACKBOX_TEST_PLAN.md) 选择一个测试切片，明确环境、用例编号和退出条件；没有新的范围决策，不继续扩展 Acceptance 业务代码。任何新功能必须重新建立独立范围、契约、测试和双模证据。

## 收口检查

交接前仍需在最终工作树执行并记录：

```bash
codegraph sync .
git diff --check
```

不修改 `.docs/.chiwen.state.json`，不提交、不推送、不部署。
