# v1.0.0 发行验收总账

> 状态：本机候选预验证，未完成发行验收、未发行。以[发行规格](../docs/superpowers/specs/2026-10-14-stable-release-design.md)为范围依据。
> 每次代码或制品变化后，受影响的证据重新验收。个人电脑只做串行、低负载预验证。

## 状态与环境

`PASS` 只用于绑定候选提交、制品摘要、环境、用例、预期/实际结果、时间和可复现证据。`PLANNED` 表示尚待执行；`IMPLEMENTED_UNVERIFIED` 表示有入口但运行闭环不足；`BLOCKED` 表示缺外部条件或发现阻断缺陷；`NOT_APPLICABLE` 必须给出模式原因；`OUT_OF_SCOPE` 只用于未交付且未公开提供的项目。旧证据仅列入“本机预验证”，不能代替 Ubuntu 发行门禁。

| 环境 | 交付方式 | 模式 | 发行状态 |
| --- | --- | --- | --- |
| D-C | Ubuntu 24.04 x86_64 Docker Compose | cloud | PLANNED |
| D-S | Ubuntu 24.04 x86_64 Docker Compose | single | PLANNED |
| N24-C | Ubuntu 24.04 x86_64 JAR + systemd + Nginx | cloud | PLANNED |
| N24-S | Ubuntu 24.04 x86_64 JAR + systemd + Nginx | single | PLANNED |

每个四项适用环境都执行空库安装、健康检查、同一 HTTP 黑盒、浏览器操作、权限/租户/审计负例、备份恢复及同版本重部署。专用环境再以两个会话连续运行核心路径 15 分钟，记录 5xx、进程状态、响应时间和资源占用；本机不做此项。

## 已提供能力（本机预验证总览）

以下为待验能力清单；每项在四环境中的权限、租户、审计及外部依赖证据尚未填齐，不能从本表推断发行通过。

| 能力 | 启用条件与模式 | 本机预验证 | 发行状态与下一份证据 |
| --- | --- | --- | --- |
| 密码登录、验证码、刷新、退出、动态权限 | 两模式默认 | 双模 HTTP/安全回归；single 真实浏览器已通过，cloud 浏览器待复验 | IMPLEMENTED_UNVERIFIED；双模浏览器及四环境复验 |
| 手机短信登录 | 两模式，需真实短信供应商 | Redis 夹具验证验证码消费、限频及响应；页面使用中性受理提示；没有真实发送 | BLOCKED；供应商真实发送、送达、登录与异常恢复 |
| UPMS 用户、角色、菜单、部门、岗位、字典、参数、日志 | 两模式默认 | 双模基础 CRUD、权限和审计有历史/本切片证据；未逐页覆盖 | IMPLEMENTED_UNVERIFIED；能力逐页黑盒及四环境复验 |
| Demo Task 与 Acceptance 字典/公共参数 | 两模式默认 | 双模 CRUD、权限种子、非法输入、导出和审计通过；single 桌面浏览器仅完成部分 BB-15/16 操作 | IMPLEMENTED_UNVERIFIED；补齐双模浏览器 BB-15/16 与四环境 BB-09 至 BB-16 复验 |
| 站内通知与 SSE | 两模式默认 | 双模权限/发布/收件人隔离；本候选双模 SSE 断线重连后二次投递及收件人隔离通过 | IMPLEMENTED_UNVERIFIED；浏览器、恢复与四环境复验 |
| 邮件、Webhook、短信、微信通知 | 各渠道开关开启且需真实外部端点 | 当前切片未验证真实供应商 | BLOCKED；逐渠道配置、真实送达/失败/恢复；未启用时验关闭态 |
| Generator | 两模式默认 | 双模表/父子表预览、下载、同步、生成与审计通过；隔离副本双模后端及前端构建通过 | IMPLEMENTED_UNVERIFIED；生成物运行与四环境复验 |
| Quartz 调度 | 两模式部署入口 | 本候选双模路由、定时成功、手动失败记录通过，临时任务已清理 | IMPLEMENTED_UNVERIFIED；重启恢复、浏览器及四环境复验 |
| Workflow | `WORKFLOW_ENABLED=true`；两模式共用业务 | 既往审批、隔离与恢复专项证据；当前默认关闭 | IMPLEMENTED_UNVERIFIED；当前制品关闭态及开启态全流程/恢复复验 |
| AI 对话与知识库 | `AI_ENABLED=true`，需真实模型/Embedding 服务 | 既往专项证据；当前候选未验证真实供应商 | BLOCKED；真实模型、知识库引用、租户隔离及失败恢复 |
| Monitor | cloud 独立服务；single 不提供独立监控入口 | cloud 容器健康；尚无完整页面及告警验收 | IMPLEMENTED_UNVERIFIED；cloud 页面/权限/重启验收；single 记 NOT_APPLICABLE 并说明架构 |
| 对象文件能力 | 需 S3 兼容存储 | 当前候选无真实存储与对象恢复证据 | BLOCKED；上传、授权下载、租户隔离、备份恢复 |

以上认证与安全“本机预验证”的详细命令、模式与结果见[浏览器认证切片记录](security/BROWSER_AUTH_SECRET_2026-10-14.md)；其他结果见下方记录，浏览器 BB-15/16 仍只有部分探索性证据。它们是后续用例来源，不是四环境发行 `PASS`。

## 交付门禁

| 门禁 | 当前状态 | 退出证据 |
| --- | --- | --- |
| 前端密钥边界 | 本机预验证通过 | 冻结制品扫描无服务端密钥，密钥轮换记录 |
| HTTPS、安全头、XSS | single 临时自签 HTTPS 与首页响应头本机预验证；可信 TLS 未验收 | 两种前端入口的可信 HTTPS/响应头与浏览器探针 |
| 原生生产配置及服务管理 | 模板与静态测试通过；构建/测试统一要求 Node 22.18+；Ubuntu 实机未验收 | Ubuntu 24.04 的 cloud/single 安装、开机、停机、重启、日志与回退 |
| 非空库拒绝与空库安装 | SQL 空库预验证通过；安装门禁未通过 | 四环境非空拒绝无改动及空库部署记录 |
| MySQL/对象备份恢复及同版本重部署 | 本机 MySQL 逻辑恢复探针通过 | 专用环境带业务数据恢复、核对、耗时及制品一致性 |
| 不可变制品 | PLANNED | 同一候选提交的 `1.0.0` JAR、前端、镜像、SQL、配置 SHA-256/摘要清单 |
| 发行 | BLOCKED | 全部适用能力和四环境通过后才创建标签、推送并发布 GitHub Release |

缺陷记录须包含复现输入、实际响应、影响级别、修复提交和双模复验。未交付且未公开提供的路线图项目不在本版范围内；已有入口不能以此豁免。

## 2026-10-14 历史本机预验证

以下结果来自当日尚未冻结的 `codex/browser-auth-secret` 工作树及隔离 Compose 项目 `bixi-browser-auth-accept`。此后又修改了短信发送与 single 内部接口防护，故这些结果不是当前候选代码的最终证据，也不能填写上表四环境的 `PASS`：

| 检查 | 本机结果 | 尚缺的发行证据 |
| --- | --- | --- |
| 构建与静态门禁 | 当日工作树串行运行 `make backend-cloud-ci`（28 模块）、`make backend-single-ci`（25 模块）、`make frontend-ci`、`make runtime-config-check`、`make architecture-check`、`bash deploy/native/test.sh` 与 HTTPS 生命周期测试，均退出 0；前端 `dist` 未检出四项运行时密钥 | 冻结提交和同一制品摘要下复验 |
| cloud/single HTTP | 两模式统一功能、安全及负向脚本均通过；负例显式指定隔离 MySQL 容器，夹具已清理 | 四环境同一制品的 HTTP、权限、租户和审计证据 |
| BB-09 至 BB-12 | cloud/single 当日镜像经增强后的负向脚本通过：双向租户读写/导出、低权限、导入边界和失败前后数据库总数；cloud 的本机 MySQL CLI 夹具路径也通过，均已清理 | 四环境同制品证据 |
| 站内 SSE 与 Quartz | cloud/single 的 SSE 断开重连、两次目标通知投递及清理通过；Quartz 自动成功、手动失败持久化记录及临时任务清理通过 | Quartz 重启恢复、浏览器和四环境复验 |
| 原生夹具路径 | 双模租户隔离通过，cloud 的 `BIXI_FIXTURE_DRIVER=native` 负例通过，夹具清理完成 | 两版 Ubuntu 原生环境重复运行 |
| Generator | 双模 `generator-acceptance.mjs` 通过表/父子表预览、同步、ZIP/XLSX、服务端生成、版本拒绝及审计；生成目录逐文件一致，隔离副本的 cloud/single 后端与两种前端构建通过 | 生成物双模运行、四环境复验 |
| Compose 与原生生产配置 | Nacos 2.4.3 发布与受限账号 ACL 探针、发行覆盖层及初始化脚本静态回归通过；密钥经 `UNHEX` 写入，旧开发卷保留普通健康检查；原生远程 MySQL CLI 要求校验服务端身份，打包强制从当前源码构建 | 冻结摘要镜像的真实发行启动、原生安装及 Ubuntu 宿主验收 |
| HTTPS | single 临时自签证书下 `https://localhost:18443/` 返回首页安全头及 HSTS | 可信证书域名、cloud/single 浏览器与响应头验收 |

本机不做高强度压测；专用环境的 15 分钟双会话轻负载检查仍为 `PLANNED`。

## 2026-10-15 候选冻结前本机预验证

本节记录基于 `9d5d540` 的候选冻结前工作树验证；当时新增安全修复尚未提交或绑定不可变制品。以下仅证明该工作树的本机低负载行为；任何后续代码变更须重跑受影响门禁。

| 检查 | 当前结果 | 仍缺的证据 |
| --- | --- | --- |
| 短信公开入口 | 非法/超长号码在 Redis 和数据库操作前拒绝，聚焦测试先失败后通过；Compose HTTPS 与原生 cloud/single 的真实 Nginx 测试验证来源 IP 限流、伪造转发头和路径规范化 | 真实供应商送达、生产可信 TLS 和 Ubuntu 宿主实测 |
| 静态与构建 | `make runtime-config-check`、`make architecture-check`、`bash deploy/native/test.sh`、布局修复后的 `make frontend-ci`、`make backend-cloud-ci`（28 模块）、`make backend-single-ci`（25 模块）、`git diff --check` 均退出 0 | 冻结提交与制品摘要下复核 |
| cloud 运行 | 隔离项目当前镜像的统一功能、安全和 BB-09 至 BB-12 负例再次退出 0，夹具已清理；此前本候选租户隔离、SSE、Quartz、Generator 也通过 | cloud 真实浏览器、可信 HTTPS 与 Ubuntu D-C/N24-C |
| single 运行 | 隔离项目当前镜像的统一功能、安全、BB-09 至 BB-12 负例、租户隔离、SSE、Quartz、Generator 均退出 0，夹具已清理 | 冻结制品及 Ubuntu D-S/N24-S 复验 |
| single 真实浏览器（BB-15/16 部分预验证） | 1280x800 管理员图片验证码登录和登出；字典表筛选、重置、分页、必填提示、空结果；断网显示错误、恢复后重试、重复查询、两个 XHR 倒序返回后保留新结果。发现固定操作列透字及弹窗标签拥挤，修复后重建前端镜像并在同视口复验不重叠 | 补测权限按钮、加载态、导入导出、切页旧响应及公共参数页；cloud 浏览器、双模冻结制品及四环境 BB-15/16 复验 |
| 发行入口静态回归 | Compose 旧 MySQL 卷拒绝、同候选卷重部署、Nacos 已有用户与 ACL 重复发布的脚本回归通过；`make runtime-config-check` 当前工作树退出 0 | 冻结提交后在 Ubuntu 实测首次安装、同版本重部署与数据不变性 |
| 候选构建门禁 | Java 17 下 `make backend-cloud-ci`（28 模块）、`make backend-single-ci`（25 模块）均退出 0；Node 22.23.1 下 `make frontend-ci` 退出 0；`bash deploy/native/test.sh` 与 `git diff --check` 退出 0；前端 `dist` 未检出旧公开 OAuth 密钥及服务端密钥变量名 | 冻结提交后的制品摘要、目标环境运行与浏览器复验 |
| 前端构建工具链 | Node 20.20.2 的完整前端 CI 在直接加载 `.ts` 测试文件时失败；已统一最低 Node 22.18、CI/容器 Node 22.23.1，并在原生打包前校验；Node 22.23.1 完整前端 CI 通过 | 冻结提交的 Docker/原生制品构建及目标环境复验 |

本轮发现的发行缺陷（冻结前修复）：

| 缺陷 | 复现输入与实际结果 | 影响与本机复验 | 待补证据 |
| --- | --- | --- | --- |
| Compose 发行启动接受旧数据库卷 | 复用带开发初始化标记的 MySQL 卷执行 `start-single-release` 或 `start-cloud-release`；原流程继续启动 | P1：可能在不支持的开发库上运行首版；新增候选提交卷标签预检，脚本先红后绿且拒绝时未发出容器变更命令 | 冻结提交号、Ubuntu 上旧卷拒绝与同版本重部署 |
| Nacos 同版本发布不幂等 | 已有 `bixi-prod-client` 与 ACL 后再次发布；原 PUT 使用错误的 `password` 字段，重复 ACL 插入也失败 | P1：cloud 重部署无法完成；改用 `newPassword` 并在精确查询后只补缺失 ACL，脚本先红后绿 | 冻结提交号、Nacos 2.4.3 实例上重复发布与权限负例 |
| 冷启动制品版本读取失败 | 干净 Maven 仓库运行原生打包；根 POM 的 `help:evaluate` 在同仓库 BOM 尚未安装时因导入解析失败而退出，CI 制品清单使用同一入口 | P1：原生发行包及 CI 产物无法稳定生成；改从独立 BOM POM 读取版本，两条入口均以失败后通过的回归覆盖 | 冻结提交的 cloud/single 实包与 CI 新环境复验 |

375x667 探索性检查发现管理页沿用既有 760px 最小内容宽度，需要横向平移才能访问整张表及工具栏；移动端管理产品不属于本版承诺，不能把登录页的窄屏结果外推为管理页完整手机适配。

没有短信网关；真实发送、送达、登录及供应商异常恢复仍为 `BLOCKED`。当前也没有四环境安装、可信证书和独立目标恢复证据，不得创建 `v1.0.0` 正式标签。
