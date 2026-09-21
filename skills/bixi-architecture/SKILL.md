---
name: "bixi-architecture"
description: "当用户明确要求审查 Bixi 架构、模块依赖、服务拆分、模块迁移、Feign 变更或双模边界是否合规时使用。普通功能实现不自动扩展为全仓重构。"
---

# Bixi 架构审查

审查指定变更和影响路径，提供最小修复建议。只请求审查时不修改代码；拟议方案与已存在的源码分开报告，不能为方案编造文件行号。

## 项目入口与约定

从用户当前目录或已指定项目运行 `git rev-parse --show-toplevel`，只检查 `AGENTS.md`、`pom.xml`、`bixi-module/`、`bixi-ui/` 是否存在，此时不读取 POM 或业务源码。下述路径相对此 Bixi 根目录解析，不能从 Skill 安装目录找业务代码。先检查工作区状态并保留已有改动。

随后检查 `.codegraph/`；存在时，在读取 POM/Makefile/业务源码或用 Glob/Grep/rg 定位代码之前，先用 `codegraph explore "问题或符号"`（或同等 MCP 工具）；失败则记录错误后回退 `rg` 和定向读取。不存在则说明并直接跳过，不自动创建索引。已经返回的当前源码不重复读取。

读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md` 及相关模块 README；以当前源码核实文档，不固化某版本的部署模块列表。用户指令与适用仓库约定优先；默认中文，跟随用户语言。单独安装即可工作，无需其他 Skill 或插件，不递归调用、不自动派生 Agent。

不得修改 `.docs/.chiwen.state.json`，不输出 `.env` 或凭据，不交付秘密、构建产物、个人绝对路径。已有授权有效，普通可逆工作不重复确认；Skill 调用不额外授权提交、合并、发布或破坏性操作。

<!-- bixi-ref: AGENTS.md -->
<!-- bixi-ref: .docs/1_ARCHITECTURE.md -->
<!-- bixi-ref: .docs/5_AI_DEVELOPMENT.md -->

## 审查矩阵

| 检查 | 合规边界 |
| --- | --- |
| 依赖方向 | `deployment → biz → api/common`；common 不依赖 biz，API 不含业务实现、不反向依赖 biz |
| 跨模块契约 | 消费者注入 `com.lotus.bixi.*.api.service` 传输无关接口；DTO/契约位于 API，唯一实现位于 biz |
| 适配选择 | cloud 选择 Feign 适配器，single 选择本地适配器；消费者不直接依赖 `Remote*Service` |
| 组合根 | single 组合业务模块，不复制 Controller/Service/Mapper/页面，不用 localhost Feign 调自己 |
| 业务一致性 | 同一业务模型、SQL、前端、权限与审计契约供双模使用；读写权限与写日志齐全 |

条件装配 cloud Feign / single local 是合法部署适配，不属于“用模式特判绕过缺陷”。审查业务是否分叉、契约是否一致，而不是看到模式条件就报错。框架升级或模块支持范围以当前 POM、装配代码与权威文档核实。

通过 CodeGraph 检查真实调用方、依赖与动态派发。依赖倒置、业务复制等结论按已知契约判断；具体 Bean 冲突、覆盖行为或异常类型须有当前框架实现/运行证据，不能把一般经验写成已证实结果。涉及拆分时补充数据所有权、事务边界、兼容与迁移/回滚影响；不顺带实施未经要求的重构。

## 输出与验证

按严重程度列问题，每项包含已核实文件/行号（或拟议方案条目）、违反规则、影响、最小改法。拟议方案未运行时，影响写契约破坏和重复维护风险即可，不附加未经验证的 Bean 注册/覆盖或具体报错推断；加上“推断”标签也不能替代依据。无问题也说明已检查范围和未检查边界。

默认运行 `make architecture-check` 并记录命令、目录、退出码；配置/适配变化可加 `make runtime-config-check`。这些检查只证明当前工作树，不证明尚未实施的方案；用户要求不执行命令时只列建议并标“未执行”。如获授权实施，则相应双模 CI 与共享运行验收仍是交付条件。

<!-- bixi-make: architecture-check -->
<!-- bixi-make: runtime-config-check -->

## 停止与交接

非 Bixi 项目、必需资料缺失或目标项目无法确定时，说明具体缺项；只询问会影响结果且无法推断的信息，不遍历整个用户目录。可继续的独立工作照常进行。交接包含目标、项目根目录、涉及文件、事实与假设、已执行命令和未完成项；复用仍有效的证据。
