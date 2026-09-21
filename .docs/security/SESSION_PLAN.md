# A4b 会话存储与撤销

承接三阶段路线图第一阶段 A。用户已授权连续开发、允许直接修改契约，不兼容旧版本。此批先完成会话存储与管理；账号状态缓存一致性及 refresh 重新加载身份随后独立验收。

## 契约

- authorization.id 为随机会话 ID，与 access/refresh token 不同。管理列表和删除接口只传会话 ID，凭据只显示脱敏文本。
- 刷新默认复用 refresh token，保持其原始到期时间；此前签发的 access token 保持各自原始到期时间。
- 用户退出、管理员下线以及标准 OAuth revoke 撤销整个会话。撤销任一尚有效的 access 或 refresh 后，此会话的所有凭据不可再使用。
- 正常授权码兑换只会使 code 失效，不能因此撤销新 access/refresh；SAS 因重放而显式使 access/refresh 失效时，应撤销整个会话。
- 已加载的旧授权在并发退出后不能再次 save 复活；并发更新必须 CAS，冲突返回稳定 invalid_grant，不能默默覆盖。
- `findById` 和 `findByToken(token, null)` 实现 SAS 标准契约。存储不把所有 inactive token 提前隐藏，以便协议层检查失效元数据；自然到期索引可移除。
- Redis 使用 v2 命名空间，不读取旧 token 快照。升级后旧登录统一失效，需要重新登录。

## 存储与原子性

主记录键前缀公开为 `BixiRedisOAuth2AuthorizationService.SESSION_KEY_PREFIX = "token:v2:session:"`，后接会话 ID。管理端扫描此键，取 ID 后调用 `findById`，不解释内部序列化格式。

主记录保存最新授权 payload、版本号和绝对 deadline。Token 索引映射到 session ID，配套 token 快照和索引集合。旧 access 查询必须得到包含该 access 的授权视图，以满足 SAS revoke 的 `authorization.getToken(token)`；同时带当前版本用于并发校验。refresh 同值索引更新为最新快照。

Lua 通过原始 byte[] 操作，保留项目 JDK 序列化的授权对象；元数据用明确 UTF-8 编码，不把 JDK 序列化整数当 Lua 数字。读 token 必须在同一原子操作中确认主记录仍存在。写操作以绝对毫秒时间 `PEXPIREAT` 设置期限；到期时间小于等于现在不写永久键。state 首次保存后固定十分钟期限，后续保存不延长。

撤销原子写入有期限的 tombstone、删除主记录和其所有索引；tombstone 至少保留到原会话最大绝对到期时间。带版本的保存遇主记录缺失必须失败；无版本对象仅能首次创建，已存在记录或 tombstone 均拒绝，不能根据 Redis 当前版本自动补全后覆盖。

本批支持项目当前独立 Redis 部署。多键 Lua 不能据此宣称 Redis Cluster 跨 slot 支持。

## 实施与验收清单

- [ ] 存储红测：真实专属 Redis，绝对期限、短 code、state 不延长、过期不永久保存、null type、findById、旧 access 读取、SAS 失效 metadata。
- [ ] 存储实现：canonical、索引、快照、版本 CAS、撤销 tombstone；真实并发屏障控制 refresh 读取→退出→refresh 保存，断言无后代可用。
- [ ] 管理红测与修复：独立 ID、access 过期但 refresh 仍有效时可下线、列表筛选后分页、短 token 也不明文、失效 token 查询拒绝。
- [ ] 协议集成：密码和短信授权不覆盖随机 ID；标准 introspection 的 RegisteredClient.findById 可用；标准 revoke、logout、管理员下线均实际失效。
- [ ] 前端提交 `row.id` 与选中会话 ID；显示脱敏 token，统一 `sys_token_del`。
- [ ] 双模黑盒：登录→刷新→旧/新 access 可用→退出/下线→旧/新 access 与 refresh 拒绝；再次登录是独立会话，不受其他会话退出影响。
- [ ] 规格/质量复审、受影响模块测试、architecture-check、runtime-config-check、前端 lint/build、codegraph sync、diff 检查。

## 已有证据

管理红测已执行：`LocalTokenManagementServiceTest` 五项均失败，`AuthenticationSecurityChainTest` 新增独立 ID 用例失败，合计 22 项中 6 failures、0 errors。中断前临时日志丢失，源码仍在；恢复后应重新运行并保存日志到忽略的本机工作目录。

不会提交或推送；不会停止其他任务的容器或修改 `.docs/.chiwen.state.json`。
