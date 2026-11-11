# 浏览器认证密钥边界切片

> 冻结日期：2026-10-14。工作分支：`codex/browser-auth-secret`。
> 本文只记录这个安全切片，不是 `v1.0.0` 稳定版验收结论。

## 冻结范围

| 项目 | 本切片退出条件 |
| --- | --- |
| 浏览器认证 | 密码、短信、刷新仅提交公开的 `client_id=bixi-browser`；无 Basic 密钥和公开 AES 密钥 |
| 服务端边界 | Auth 使用运行时密钥接入现有 OAuth 客户端认证链；浏览器客户端禁止 `client_credentials`；验证码和账号状态校验继续执行 |
| 双模式交付 | cloud Auth 与 single 读取相同密钥；SQL、Compose 和物理机环境变量使用同一契约；前端构建参数不携带服务端密钥 |
| 回归 | 原有机密客户端 Basic 认证和旧密码解密路径保留；统一验收包含浏览器登录、刷新、退出与拒绝用例 |

首次稳定版只支持空库安装，不承担开发阶段旧库升级兼容。公开过的旧 `bixi`、`app` 客户端密钥及 `BIXI_ENCODE_KEY` 在发行前须轮换。

## 已有证据与时效

前端构建、双模式运行门禁与完整后端 CI 已在本切片最后的代码改动后复验。single 真实浏览器已复验最终镜像；cloud 的最终镜像浏览器复验尚未完成。

| 门禁 | 结果 |
| --- | --- |
| Auth 共享安全链与模块测试 | 此前 Java 17 下 `mvn -q -pl bixi-auth -Pcloud test` 通过（71 项）；覆盖浏览器密码、短信、刷新、验证码拒绝和错误客户端/授权类型 |
| 后端聚焦回归 | 最终业务代码在 Java 17 下运行 11 类 55 项测试，零失败；覆盖用户名、短信状态及原子限流、浏览器客户端管理保护、角色/菜单缓存、三类菜单可见性及重启缓存 |
| 前端 | 最终代码的 `make frontend-ci` 退出 0，包含页面契约、ESLint 和生产构建；此前聚焦测试 5/5 通过，双模构建资产未检出旧公开密钥 |
| 配置与架构 | 当前工作树的 `make runtime-config-check`、`make architecture-check`、`node --check scripts/acceptance.mjs` 与 `git diff --check` 均退出 0 |
| 空库 SQL | 隔离 MySQL 8.4.3 临时库按 `01`→`02`→`03`→`04` 导入，四步均退出 0；建成 64 张表，用户名/租户非空、非空白和同租户唯一约束通过正反向插入验证；临时库已删除 |
| 双模运行验收 | 最终代码重建后的隔离项目 `bixi-browser-auth-accept` 中，`make verify-single`、`make verify-cloud` 均退出 0；覆盖验证码、公开客户端登录/刷新、退出撤销、共享 CRUD 与审计、空白用户名和更名拒绝、托管客户端不可改删及无网关时手机号响应一致 |
| 双模安全回归 | 最终业务代码镜像的 `make verify-security-single`、`make verify-security-cloud` 均返回 `status: passed`，覆盖权限、CSRF、原机密客户端授权、动态撤权、通知隔离与审计断言 |
| 完整后端 CI | 最终代码在 Java 17 下的 `make backend-cloud-ci`（28 个模块）和 `make backend-single-ci`（25 个模块）均 `BUILD SUCCESS`；部分需外部运行环境的测试按 Maven 配置跳过 |
| 最终镜像 single 浏览器 | 管理员图片验证码登录进入首页；将隔离会话的刷新时间调到过去后重载，浏览器实际请求 `/api/admin/oauth2/token` 返回 200，新的刷新时间写回；退出后回到登录页且 `sessionStorage` 中访问/刷新令牌均清除。浏览器无认证 Cookie 或 `localStorage` 令牌，控制台无警告/错误 |
| 双模租户隔离 | 最终镜像的 `scripts/tenant-isolation-acceptance.mjs` 在 single、cloud 均返回 `status: passed`；覆盖同名用户、跨租户详情/更新/删除、伪造租户头、跨租户只读范围与停用租户后的访问和刷新拒绝。夹具已清理 |
| 双模 Generator | 最终镜像的 `node scripts/generator-acceptance.mjs --mode cloud` 和 `--mode single` 均通过独立表和父子表预览、ZIP/XLSX、服务器端生成、旧版本拒绝及审计；双模生成目录逐文件一致，隔离副本中的 cloud/single 后端及两种前端构建通过，生成物尚未运行验收。脚本保留带 `generator-acceptance` 标识的元数据和忽略目录下的生成产物 |
| 本机恢复探针 | 隔离 MySQL 8.4.3 的逻辑导出恢复到新建临时库成功，原库与恢复库均为 64 张表，`sys_user`/`sys_menu`/`sys_role`/`biz_demo_task` 行数分别同为 25/186/13/20；临时库已删除，生产备份介质与切换回退未验收 |
| 移动端与环境文件 | 修复登录页在 320px 无法滚到表单及横向溢出；此前 cloud 与 single 镜像在 320x863、375x667、768x1024、1280x800 实测无横向溢出，375px 与 768px 页面可滚动到表单。`init-env` 对现有 `0644` 环境文件的权限测试先失败后通过，隔离 `.env` 已收紧为 `0600`；`make runtime-config-check` 通过 |

上述运行在 macOS 上通过 Docker Compose 串行验收两种模式，宿主 HTTP 端口为 `18080`；未进行压测。隔离项目容器在最终浏览器验收期间运行，结束后停止并保留数据卷。验收脚本用 `redis-cli` 写入已知的短期短信验证码，仅是自动化测试夹具，不是短信发送或真实送达证据；当前没有短信网关。生产服务没有测试入口。安全回归中的表单授权用例仍使用原机密客户端，不能替代新前端的真实浏览器登录。

## 尚未关闭的发行门禁

1. 接入实际短信网关并验证真实发送、送达和短信登录闭环；同时复核真实供应商的响应时间是否泄露号码状态。当前 Redis 夹具验证不能关闭这一硬门禁。
2. cloud 最终镜像仍需真实浏览器复验。访问令牌和刷新令牌只写入 `sessionStorage`，代码会清理旧版认证 Cookie；`sessionStorage` 仍可被页面 JavaScript 读取。两个前端 Nginx 模板现已设置 CSP、`X-Content-Type-Options`、`X-Frame-Options` 和 `Referrer-Policy`；single 临时自签 HTTPS 首页已检出安全头及 HSTS，但可信证书域名、cloud HTTPS 与渲染时 XSS 探针仍未验收。
3. 在 Ubuntu 24.04 LTS x86_64 上验收 cloud/single Docker Compose，在 Ubuntu 22.04/24.04 LTS x86_64 上验收 cloud/single 原生 JAR + systemd + Nginx，共 6 种组合；每种都需空库安装、健康检查、浏览器验收及回退演练。本机 macOS arm64 结果只是低负载预验证。
4. 完成全功能黑盒矩阵、生产备份恢复与回退和正式发布制品复核后，才可判定 `v1.0.0` 稳定版是否达到发行条件。本机逻辑恢复探针及完整后端 CI 已通过，不能替代生产环境演练；物理机空库安装的运行时密钥注入尚未实机验证。
5. 隔离工作树的 `.codegraph/` 没有可用索引，CLI 查询失败；在有索引的最终代码树重新执行 `codegraph sync .`。

个人电脑只执行串行、低负载验证，不进行高强度压测。未通过上述门禁前，不打稳定版标签、不推送正式发行制品。
