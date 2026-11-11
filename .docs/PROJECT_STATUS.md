# Bixi 项目状态总账

> 冻结范围日期：2026-10-05；本轮维护：2026-11-11
>
> 本轮起点（有证据）：本地 `main@c740b83`，领先 `origin/main@2b5649d` 1 个提交，工作区干净。未 reset、rebase 或覆盖已有提交。
>
> 历史运行验收基线（文档声明）：`2b5649d`，由 `c740b83` 记录；不等于本轮最终维护提交。

本文档是当前维护范围和验收状态的交接入口。路线图、专项计划和旧续接提示词继续保留为历史材料；它们不能覆盖用户确认的维护范围和当前证据。

## 2026-11-11 收敛维护

- **有证据**：首次修复 `scripts/verify-runtime-config.sh` 个人路径检查误扫被忽略 `.idea/workspace.xml` 的问题；当时的 YAML 负向探针仍被拒绝，前端密钥产物扫描没有放宽。
- **有证据**：随后对已跟踪运行配置做负向验证，发现 Nginx `.conf` 与 `.conf.template` 漏检个人路径；已补齐这两种后缀，并在既有脚本测试中验证 YAML/Nginx 拒绝与 `.idea` 排除。失败重现、修复后回归和本次门禁见[运行配置维护补充](../test-evidence/maintenance-20261111/runtime-path-followup/README.md)。
- **有证据**：`make architecture-check`、修复后 `make runtime-config-check`、`make backend-cloud-ci`（28 模块）、`make backend-single-ci`（25 模块）、`make frontend-ci` 和前端产物密钥扫描完成。后端环境条件跳过 79 项，原因和逐套件数据明确归档，不把这些项目写成通过。
- **有证据**：变更和命令、退出码、失败重现、负向探针、跳过项见[维护执行记录](../test-evidence/maintenance-20261111/README.md)。本轮没有运行应用发行、没有创建 tag 或推送。
- **文档声明（用户已确认）**：发行状态为“暂不处理”；不推进四环境发行门禁，不将历史本机运行结果绑定为当前发行 `PASS`。
- **文档声明（用户已确认）**：只维护现有能力，不新增业务模块、SPI、Webhook、模板市场、工程构建器或独立扩展运行时。未来方向见[路线记录](3_ROADMAP.md#2026-11-11-维护范围与未来建议)。

## 状态口径

- **已验证**：有本轮可复现的静态、构建、迁移或运行证据。
- **已实现，未验收**：代码已经存在，但本轮没有完成所需运行闭环。
- **未完成**：尚未实现必要链路。
- **未验证/环境阻塞**：实现可能存在，但当前没有足够环境或运行证据确认。

不使用整体完成百分比。每个能力只进入一个状态，并绑定下一步证据。

## 历史 Acceptance 冻结范围

历史切片只冻结并验收以下两个已有资源及其共享运行链路：

1. HTTP：`/admin/dictAggregate`
2. HTTP：`/admin/publicParam`
3. 前端入口：`/acceptance/dictAggregate/index`、`/acceptance/publicParam/index`
4. 部署模式：cloud 和 single，共用 `bixi-upms-biz` 的一套 Controller、Service、Mapper 和 API DTO。
5. 数据与权限：新库菜单、管理员角色 1 授权、存量菜单迁移、按钮权限、写操作审计。

本轮不宣称完成其他路线图阶段，也不纳入外部短信/邮件送达、Quartz/SBA 故障接管、生产恢复目标、完整多租户负向矩阵或性能/高可用演练。

## 历史 Acceptance 切片记录

以下是 `c740b83` 延续的 `2b5649d` 验收文档声明；本轮当前命令结果以上方维护执行记录为准，不能用历史切片声明替代当前完整运行或发行证据。

- Acceptance 菜单稳定 ID 和管理员授权：字典 `7200-7209`，公共参数 `7210-7216`；新库 SQL 与存量迁移契约检查通过。
- 存量菜单迁移支持重复执行、保留兼容记录展示字段，并在 ID/身份冲突时拒绝写入；迁移回归和静态契约检查通过。
- Acceptance 后端 DTO、Controller、Service、Mapper、权限注解、导入失败结果和审计注解已覆盖；`AcceptanceServiceBehaviorTest` 通过 `6/6`。
- 前端 API、页面表单校验、按钮权限、加载/错误/空状态和生产构建通过；前端契约测试通过。
- `make architecture-check` 通过。
- `make runtime-config-check` 通过。
- `make backend-cloud-ci` 与 `make backend-single-ci` 在历史 `2b5649d` 上均退出 0；分别完成 28/28、25/25 模块的完整 `clean verify`，未使用测试跳过参数。
- `bixi-ui` `npm run build:prod` 退出 0，`node --test tests/browser-auth.test.mjs` 为 6/6；产物扫描未命中 `VITE_OAUTH2_*`、`VITE_PWD_ENC_KEY` 或 `basicAuth`。
- 使用全新临时 Compose 项目和数据库卷，`make verify-cloud` 与 `make verify-single` 均退出 0，覆盖登录、刷新、短信拒绝、管理员权限、示例任务 CRUD、两组 Acceptance 资源 CRUD、筛选、详情、非法请求、未知 ID、空删除、非法导入、XLSX 导出、写审计日志和 Workflow 关闭态检查；详见 [`test-evidence/verify-20261111/README.md`](../test-evidence/verify-20261111/README.md)。

## 历史切片结论

历史文档声明 Acceptance 切片达到当时退出条件。两种部署模式当时的记录如下；本轮仅修复静态门禁，不重新声明应用运行验收：

| 模式 | 入口 | 运行验收 | 结论 |
| --- | --- | --- | --- |
| cloud | `http://localhost:8080`，Gateway/Auth/UPMS | 临时全新卷 `make verify-cloud` | 通过（历史文档声明） |
| single | `http://localhost:8080`，single/Auth/UPMS | 临时全新卷 `make verify-single` | 通过（历史文档声明） |

`scripts/acceptance.mjs` 是当前切片的统一黑盒入口；它按运行模式读取 `.env`，不会把某一种部署实现复制到另一种模式。

## 已知限制与未验证项

- 历史文档声明证明两个 Acceptance 资源和既有 Demo Task 的双模运行闭环；本轮未重跑该应用验收，不代表整个 Bixi 路线图完成。
- 前端已完成静态契约和生产构建验证；真实浏览器尺寸、键盘操作、跨页面并发点击和网络抖动仍按 [黑盒测试计划](BLACKBOX_TEST_PLAN.md) 执行。
- 租户隔离的完整跨租户正负矩阵、低权限角色全量矩阵、导入大小/行数上限、重复业务键和真实存量迁移冲突环境仍需按计划补跑。
- 外部短信、邮件、微信、Webhook 的供应商送达，Quartz/SBA 故障接管和生产级恢复目标不在本轮退出条件内。
- 直接复用旧 `bixi` 数据卷时，`make verify-cloud` 曾因缺少 `bixi-browser` 数据行返回 401；全新卷按当前 SQL 初始化后通过。旧卷迁移或重置策略待单独确认。

## 下一步门禁

下一次维护先核对实际分支、HEAD、工作区、领先提交和本文件的维护证据；只选择与既有缺陷直接相关的验证切片。发行继续暂不处理。没有新的范围决策，不扩展 Acceptance 或其他业务代码。

## 本轮收口检查

本轮已在最终工作树执行并记录：

```bash
make architecture-check
make runtime-config-check
git diff --check
```

不修改 `.docs/.chiwen.state.json`。本轮提交可在上述门禁通过后创建；推送仍需维护者确认。
