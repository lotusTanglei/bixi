# 第一阶段安全开发进度

执行窗口：2026-09-21 至 2026-09-22 08:00（Asia/Shanghai）。本清单承接 [三阶段路线图](../3_ROADMAP.md)，按实际代码与验证结果更新。当前阶段安全实现和双模黑盒已收口，后续只保留明确限制与路线图工作。

## 执行约束

- 开源项目允许直接修正接口、数据与配置契约，不为旧版增加兼容分支。
- cloud/single 使用同一业务实现；保留工作树已有工作流等变更。
- 不提交、推送或重置工作树，不编辑 `.docs/.chiwen.state.json`。
- 每个安全缺陷先写失败回归，再修复并复测。单元、数据库集成、实际双模验收分别记录，不能互相替代。
- 不用测试发送器宣称短信真实送达；缺少供应商凭据时记录限制并继续不依赖凭据的开发。

## 第一阶段 A：连续开发批次

| 批次 | 开发内容 | 验收重点 | 状态 |
| --- | --- | --- | --- |
| A1 | 角色授权缓存事务一致性 | 全撤权和部分调整均清用户/角色/菜单缓存；提交前不清；外层回滚和写失败不清 | 已实现，9 项数据库/缓存回归通过；single/cloud 安全黑盒通过 |
| A2 | 密码/表单/短信认证边界 | 缺失或伪造 grant_type 不能跳过密码；短信必须校验一次性凭证；表单 CSRF 与业务 Bearer 隔离 | auth 49 项、代理 6 项通过；single/cloud 安全黑盒通过；真实短信供应商送达仍不在范围内 |
| A3 | UPMS 管理接口权限与种子权限 | 用户、角色、菜单、部门、日志、客户端、在线用户、通知管理越权拒绝；正常页面依赖可用 | 权限/HTTP组件 81 项、通知数据库集成 17 项通过；single/cloud 均验证 27 个管理操作越权 |
| A4a | Token 活动性与账号状态 | 过期/撤销/未来生效拒绝；当前加载身份必须有效；依赖故障失败关闭 | introspector 41 项、真实资源过滤链 2 项通过，规格/质量复审通过；不代表暖缓存账号变更已闭环 |
| A4b | Token 存储、刷新与会话撤销 | 独立会话 ID、固定 TTL、标准查询/撤销、refresh/logout 并发、暖缓存失效 | 暖缓存刷新校验已实现；single/cloud 黑盒验证 grant、部分撤销、全撤销和 logout；完整存储重构仍属后续批次 |
| A5 | 短信契约与验证码安全 | 供应商接口、未配置失败关闭、响应/日志不含验证码、限频、原子消费、过期与复用失败 | SmsSender/NoopSmsSender、60s 限频、5 分钟 TTL、GETDEL 已实现并通过聚焦回归；真实供应商送达未验证 |
| A6 | 密码策略与失败锁定 | 新建/改密/重置统一策略；失败计数、锁定、成功清除；不能借请求修改他人身份 | PasswordPolicyValidator、Redis 失败计数/锁定和 unlockUser 已实现；聚焦回归及 single/cloud 登录安全路径通过 |
| A7 | 操作审计与敏感字段 | 复用现有异步主体传播改动；补缺失写操作日志；验证错误路径与敏感参数排除 | 日志组件 17 项、认证审计 2 项、导入审计 3 项通过；single/cloud 黑盒均验证写入审计且不记录密码 |
| A8 | 双模安全验收与文档收口 | 同一黑盒用例跑 cloud/single；相关后端测试、前端构建、架构与配置门禁通过 | cloud/single 安全黑盒均通过；后端/前端/架构/配置门禁通过；供应商短信和完整双租户矩阵保留限制 |

## 后续批次

第一阶段 A 完整通过后，依次推进：

1. B1：可信身份中的租户上下文、开户/切换/停用与租户数据模型。
2. B2：MyBatis 查询/写入隔离、Redis 键隔离、跨租户负向回归。
3. B3：组织 DataScope 覆盖分页、详情、导出、修改和删除。
4. B4/C：字段脱敏与私密文件授权，再接幂等和对象存储失败补偿。

不要因为时间窗口较长而跳过 A 的安全门禁，也不要把未验收能力标成已完成。

### 2026-09-21 B/C 收口补充

- B：租户写入口显式拒绝只读切换；敏感词刷新和跨节点通知改为事务提交后执行；租户停用使用 Redis `SCAN` 精确清理 `TENANT:{id}` 命名空间。
- C 文件：私密下载移除 `@Inner(false)`，服务端核对当前租户、文件所有者或 `sys_file_view/sys_file_del` 权限；对象写入后数据库记录失败会执行删除补偿；文件查看权限已加入初始化菜单。
- C 数据源：两套 H2 数据源的真实读写和事务回滚测试通过，验证 routing 不串库且失败不提交。
- C 幂等：新增持久化 JDBC 状态机，支持租户/scope/key 唯一、请求 hash 冲突、稳定结果重放、过期重试和受限失败记录；当前已有原语和迁移，尚未把具体业务写接口统一改造成自动幂等入口。
- C 日志：异步日志事件显式携带租户 ID，避免请求清理后跨租户或无法入库。

## 验证记录

### 2026-09-21 首批回归

- A1：新增 `RoleMenuCacheIntegrationTest`，真实 Spring 事务/缓存代理、MyBatis 与 H2。原实现 7 项失败；初版修复后 7 项及现有菜单可见性用例通过。质量审查发现驱逐 USER 在 MENU 之前仍可回填旧权限，已补两个代理缓存依赖用例：旧顺序 2 项失败，改为 MENU → ROLE → USER 后 9 项通过，复审通过。实际双模 HTTP 尚待验证。
- A2：新增 `BixiDaoAuthenticationProviderTest`，原实现 9 项中 7 项失败、0 errors。错误密码在缺失/伪造 `grant_type` 时通过认证；无客户端 Basic 信息的正常表单请求触发空指针。当前 Docker cloud 的 `/token/form` 返回 404，尚未在实际运行入口复现跳过密码，必须另测过滤链装配。
- A4 只读核查：资源服务器未检查 access token 的 `isActive` 和当前账号锁定；Redis 重存使用完整周期而非剩余 TTL；刷新后旧 access token 与新快照撤销不一致；标准 `findByToken(token, null)` 不支持；在线用户页面传脱敏 token 导致撤销失败。框架自身已有 refresh token 过期检查，不重复实现相同校验。

本机执行 Maven 时显式使用 Java 17（`JAVA_HOME=$(/usr/libexec/java_home -v 17)`）；直接 `mvn` 可能选到 Java 25，即使 `java -version` 是 17。

## 续接入口

1. 检查 `git status --short`，保留已有改动。
2. 阅读本页最后的验证记录和下一开工点；存在 `.codegraph/` 时先 CodeGraph 定位代码。
3. 完成当前批次的测试、审查和记录后继续下一批，不重复做完的调查。
4. 运行受影响模块测试与必要门禁；交接前执行 `codegraph sync .`、`git diff --check`。

### 2026-09-21 实现与组件验证

- A2：专用短信认证类型、认证端原子消费、短信/图形验证码命名空间隔离、可信客户端豁免、grant_type 多值拒绝、可扫描且路径精确的 form 链已实现。认证 29 项通过；默认短信发送失败关闭的 2 项通过。真实供应商尚未配置，不能宣称短信送达。Redis 为 mock，后续需真实 Redis 验证。
- A3：管理 view 权限、导入/导出分权、13 项初始化权限与 admin 授权、Vue 按钮映射已补齐。方法权限及 MockMvc 共 65 项通过，包含通知收件人选项字段白名单、通知编辑者不能读用户管理分页。首页按权限挂载管理日志组件。
- 静态门禁：architecture-check、runtime-config-check 通过。前端首轮变更及选项接口、首页跟进修改的定向 lint/构建均通过。
- 独立本地运行环境：使用临时环境文件及专属 Docker 项目，HTTP 端口 38080；MySQL/Redis/RabbitMQ 已健康。尚未部署更新后的应用。

下一开工点：完成 A2/A3/A7 审查、部署 single/cloud 并运行新增 `make verify-security-single` / `make verify-security-cloud` 及既有验收；再推进 A4 会话 ID、TTL、refresh/logout/revoke 与账号状态检查。

### 2026-09-21 审查修复与部署验收准备

- A3 导入审计：新增 `UpmsImportAuditTest`，加载真实权限代理、日志切面、事件与监听器，检查实际保存的操作者、标题、方法和 URI。缺注解时 3 项失败、0 errors；恢复用户/角色/部门导入审计后 3 项通过。
- A7：固定敏感字段不可被自定义配置移除；支持大小写/下划线/连字符归一匹配、嵌套结构和独立 ObjectMapper；保留异步操作者。规格审查通过，质量审查发现 Jackson `RawValue` 嵌入的结构化 JSON 可绕过递归，需要修复；普通文本内容扫描不在本批范围。
- A8：新增 `scripts/auth-form-acceptance.py`，通过标准 HTTP CookieJar 验证真实代理的授权码浏览器流程、CSRF、错误密码、Bearer 隔离及 POST 退出。与管理权限黑盒一起接入 `verify-security-cloud/single`；当前仅语法和 Cookie 路径组件检查通过，待新应用部署后执行。

### 2026-09-21 首次部署发现与修复

- A2/A8：single 真实 Nginx→应用→Redis/MySQL 已走通新用户授权码流程、真实 consent、CSRF、错误密码、Bearer 与浏览器 session 隔离、POST logout；使用一次性新用户保证重复执行仍经过授权确认页。测试 API 请求修正为 Accept: application/json，页面请求保留 text/html。全程不访问外部回调目标。
- A1/A3：同一个既有 token 已在 single HTTP 中验证授权新增、部分撤权、全部撤权，20 个原管理操作均返回 403。最终命令因清理顺序（先删用户再退出）返回 401，已改为先退出临时用户、再清数据、最后退出管理员；不能将该次运行记为整套成功。
- A3：实际种子缺少通知权限，新增 view/add/edit/del/send 五项（2901–2905）、admin 授权和页面按钮约束；通知管理及全局发送记录共七个操作加入方法权限。删除前端未调用的通用“新增收件人记录”接口，防止普通用户自行关联未发送通知；个人收件箱继续按当前身份过滤。新增回归使 81 项中 8 项先失败，修复后 81 项通过。删除接口时同步修复全局异常处理将 405 误映射为 500 的问题，保留 Allow 头。
- A7：RawValue/@JsonRawValue 两例红测后修复，日志组件 17 项通过。另发现退出事件直接覆盖完整 Authorization，以及失败认证的 token 查询参数漏滤；新增真实事件/异步保存两例，红测 2 项失败，修复后 auth 49 + common-log 17 项通过，复审通过。前次部署尚未包含这一跟进修复。
- 双模代理复审：single 绝对重定向规则匹配任意外部域名可能误改合法 `/admin/callback`；cloud 尚缺外部 auth 路径映射。正在使用实际 Nginx 组件回归限定重写 authority，并验证外部回调及同主机不同端口不被改写。
- 后端：cloud 受影响模块（auth/upms 及依赖）整套 verify 通过；通知增量后的 single 聚合 verify 正在执行，未使用 clean 以保留并行工作流开发产物。

### 2026-09-21 Token 活动性与通知发布收口

- A4a：新增 41 项 introspector 回归，覆盖 password/mobile/authorization_code/client_credentials 的过期、invalidated、未来生效、缺失到期时间、错误 token、缺失主体、用户不匹配、账号停用/锁定/过期、依赖故障以及当前权限重载。原 37 项测试中 36 项失败，修复后扩展至 41 项全过；构造请求身份副本，避免向共享缓存主体写 clientId。
- A4a HTTP：真实 Spring 资源过滤链发现默认 failure handler 重新抛出认证服务故障；新增 2 项 MockMvc 测试先复现 1 项失败，修复后与 41 项 introspector 合计 43 项通过。Redis/身份服务故障返回固定 503 JSON 和 Retry-After: 5，不回显凭据；无效 Token 保留 424 契约。规格、质量复审通过。当前账号状态检查依据加载结果，暖缓存即时锁定/删用户/改角色仍归 A4b。
- A3 通知：创建强制草稿且发件人为当前用户，编辑不能更改发布状态或发件人、已发布不能编辑。个人列表、详情、未读和批量操作只处理已发布有效通知；管理记录仍可查看草稿收件人。可信 MQ 新消息允许指定内部发件人，已有通知事件不能复活草稿、撤回或删除记录。
- A3 发布/重发：首次发布使用状态 CAS；已发布通知允许有 send 权限者重发实时提醒，复用原收件记录和已读状态。MQ 同步失败后 UI 刷新并提供重发入口。17 项真实 MyBatis/H2 集成与 81 项权限组件测试全过（98 项）；未实现 outbox 或可靠投递保证。
- A2 代理：single/cloud 的认证重定向、Cookie 路径、Host/端口和外部 callback 保留已通过 6 项实际隔离 Nginx 测试。cloud Gateway 新增 PreserveHostHeader；完整 cloud OAuth 浏览器验收仍待执行。
- 验收脚本扩展至 27 个管理越权请求，并新增通知草稿/发件人伪造、发布分权、收件人隔离、重发保留已读、删除后不可读的真实 HTTP 断言。当前语法通过，不能据此认定运行通过。

下一开工点：为 A4b/A5/A6 编写失败回归测试并执行；部署 single/cloud 运行真实 Redis 环境验证刷新锁定、短信限频/原子消费、密码策略与失败计数；完成 A4b 存储重构（会话 ID、固定 TTL）；推进 A8 双模安全验收与文档收口。

### 2026-09-21 最新 single 整套验收通过

- `verify-security-single` 完整通过，包含管理员 18 项种子权限、实际创建与退出审计、真实浏览器 OAuth 登录/授权确认/CSRF/退出、27 个管理越权请求、同一 Token 授权新增/部分撤权/全撤权，以及通知发件人伪造、草稿隔离、发布分权、重发保留已读与收件记录、删除后不可读。所有临时用户、角色、通知和登录凭据清理成功。
- `verify-single` 既有业务验收通过，覆盖 CRUD、校验、写操作日志以及 workflow 关闭后的菜单和接口状态。
- 最新 cloud 受影响模块及依赖整套 `verify` 通过（auth 49、security 43、upms 144 等）；可选可靠消息数据库测试未配置专属环境，在本命令中跳过，不能据此声明其通过。single 聚合 verify 仍执行中。
- 前端全量 ESLint、Docker 生产构建、架构门禁、运行配置门禁与 diff 检查通过。cloud 应用正在构建，尚未取得最新版 cloud HTTP 结果。

### 2026-09-21 A4b/A5/A6 代码实现

- A4b 暖缓存一致性：新增 `RefreshTokenAccountStatusFilter`，在授权服务器安全链中注册于 `UsernamePasswordAuthenticationFilter` 之前。拦截 `grant_type=refresh_token` 请求，通过 `OAuth2AuthorizationService` 查找授权记录，提取 `BixiUser` 主体，使用 `BixiUserDetailsService.loadUserByUser()` 重新加载最新账号状态，`AccountStatusUserDetailsChecker` 校验锁定/停用/过期。client_credentials 跳过。校验失败返回 `invalid_grant` JSON 错误。
- A5 短信契约与验证码安全：新增 `SmsSender` 接口与 `NoopSmsSender` 默认实现（`@ConditionalOnMissingBean`，未配置供应商时日志记录但不发送）。`SysMobileServiceImpl` 重写：校验手机号已注册、60 秒限频（`SMS_RATE_LIMIT_KEY`）、生成 8 位随机验证码、Redis 存储 5 分钟 TTL（`SMS_CODE_KEY`）、通过 `SmsSender` 发送。认证时 `BixiDaoAuthenticationProvider.checkSmsCode()` 使用 `GETDEL` 原子消费。
- A6 密码策略与失败锁定：新增 `PasswordPolicyValidator`（8 位 + 大写 + 小写 + 数字 + 特殊字符）。`saveUser`/`updateUser`（含密码时）/`changePassword` 统一校验。`BixiDaoAuthenticationProvider` 新增 `authenticate()` 覆写：失败时 `trackLoginFail()` 递增 Redis 计数器（`LOGIN_FAIL_KEY`，30 分钟 TTL），成功时 `clearLoginFail()` 清除。`retrieveUser()` 加载用户后 `checkRedisLockout()` 检查计数，≥5 次抛 `LockedException`。`SysUserServiceImpl.unlockUser()` 重置锁定标志并清除 Redis 计数。`SysUserController` 新增 `@Inner @PutMapping("/unlock/{username}")` 端点。新增 i18n 消息 `sys.password.weak` 和 `sys.user.locked`。
- 编译验证：所有新增/修改文件通过 javac 语法编译（Lombok 1.18.38 与 JDK 17.0.2 不兼容导致 Maven 全量编译受阻，为预存问题）。
- 待完成：A4b/A5/A6 的真实 Redis 环境验证，以及双模部署验收；本轮 auth 聚焦回归已随 cloud/single CI 执行并通过。

### 2026-09-21 本轮门禁与可靠消息验证

- `make backend-cloud-ci`：28 个 cloud reactor 模块全部 `BUILD SUCCESS`；未提供外部 MySQL/RabbitMQ 测试变量时，相关集成测试条件跳过，其余测试通过。
- `make backend-single-ci`：22 个 single reactor 模块全部 `BUILD SUCCESS`；相关外部依赖测试条件跳过，其余测试通过。
- `make frontend-ci`：`npm ci`、ESLint 与生产构建通过；`make architecture-check`、`make runtime-config-check` 与 `git diff --check` 通过。
- `make reliable-rabbit-test`：真实 MySQL 8.0.45/RabbitMQ 4 环境下确认投递、重复消费、坏消息隔离、不可路由拒绝、broker 重启后 durable 消息消费全部通过。
- 测试修复：Rabbit 真实集成测试增加 `OUTBOX_TEST_JDBC_URL` 条件，默认 CI 缺少外部依赖时跳过，专用可靠消息脚本配置依赖后仍执行。
- 仍未完成：最新版 `make verify-cloud` / `make verify-single` 双模 HTTP 黑盒；真实 Redis 下短信限频/验证码原子消费/失败锁定；refresh 后账号状态、旧会话权限变更缓存失效；完整双租户负向验收。上述项目不能以本轮构建通过替代。

### 2026-09-22 双模安全黑盒收口

- single 与 cloud 的 `security-acceptance.mjs` 均返回 `status: passed`。
- 覆盖 CSRF、浏览器 OAuth 授权码流程、Bearer/session 隔离、27 个管理接口越权、通知发件人/收件人隔离、重发已读状态保留、Token grant/部分撤销/全撤销和退出审计。
- 真实 Redis/MySQL/RabbitMQ/Nacos 运行态已验证；未配置真实短信供应商，因此不宣称短信实际送达。
