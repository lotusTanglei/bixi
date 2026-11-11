# Bixi 运行配置扫描维护补充（2026-11-11）

## 实际问题与修改

- **有证据**：起点 `main@a1ed48c`，工作区干净，领先 `origin/main` 2 个提交；未重写既有提交。
- **有证据**：对已跟踪的运行资源临时附加 `# maintenance-boundary-probe /Users/runtime-probe/private`，执行 `make runtime-config-check` 后在 `finally` 恢复原始字节。YAML 被拒绝（退出 2），但 `deploy/nginx/cloud.conf` 和 `deploy/nginx/https.conf.template` 都错误通过（退出 0）；见 `runtime-negative-summary.txt` 及 `runtime-negative-0.txt`、`runtime-negative-1.txt`、`runtime-negative-2.txt`。临时探针未提交。
- **有证据**：根因是 `scripts/verify-runtime-config.sh` 的个人路径扫描只选择 XML/YAML/properties/SQL，漏检它自身 `required_files` 中的 Nginx 配置。修复仅增加 `*.conf` 和 `*.conf.template`，保留原有 IDE、构建产物排除以及浏览器密钥检查。
- **有证据**：在现有 `scripts/bixi-runtime-config.test.sh` 中执行真实扫描块，隔离 fixture 验证 YAML、Nginx 配置和模板均拒绝个人路径，且 `.idea/workspace.xml` 仍被排除。修复前测试退出 1，明确报告漏检 `deploy/nginx/cloud.conf`；修复后退出 0，见 `runtime-regression-before.txt`、`runtime-regression-after.txt`。
- **有证据**：修改文件为上述两个脚本、项目交接文档及本目录证据。本次提交可用 `git log -1 --format=%H -- scripts/verify-runtime-config.sh` 定位；OPEN 索引在提交后绑定最终 hash。

## 验证命令、退出码及证据

| 命令 | 退出码 | 结果与证据 |
| --- | ---: | --- |
| `make runtime-config-check`（原 HEAD 基线） | 0 | `runtime-baseline.txt` |
| `node --test bixi-ui/tests/browser-auth.test.mjs` | 0 | 6 tests，0 失败/跳过；`browser-auth.txt` |
| `mvn -Pcloud -pl <4个目标模块> -Dtest=<9个既有测试类> surefire:test` | 1 | 无 reactor 的首次尝试未能解析未安装的内部 `bixi-common-core:1.0.0`，不是测试通过；`security-backend.txt` |
| `mvn -Pcloud -pl <4个目标模块> -am -Dtest=<9个既有测试类> -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | 69 tests，0 失败/错误/跳过；完整命令在 `security-reactor.txt.gz` 首行。选择器只允许依赖模块没有匹配测试类，不跳过匹配测试、不掩盖失败，无 `skipTests` 或 `maven.test.skip` |
| `sh scripts/bixi-runtime-config.test.sh`（修复前） | 1 | 有效失败回归，`runtime-regression-before.txt` |
| `sh scripts/bixi-runtime-config.test.sh`（修复后） | 0 | `runtime-regression-after.txt` |
| `make backend-cloud-ci`（含 architecture/runtime 静态门禁） | 0 | 28 个 reactor 模块成功，1218 tests，0 失败/错误，79 项条件跳过；`backend-cloud-ci.txt.gz`、`cloud-surefire-summary.json` |
| `make backend-single-ci`（含 architecture/runtime 静态门禁） | 0 | 25 个 reactor 模块成功，1197 tests，0 失败/错误，79 项条件跳过；`backend-single-ci.txt.gz`、`single-surefire-summary.json` |
| `codegraph sync .` | 0 | `codegraph-sync.txt` |
| `git diff --check` | 0 | `diff-check.txt` |
| `make frontend-ci` | 0 | 既有前端测试、eslint、生产构建通过；`frontend-ci.txt` |
| `rg -l 'VITE_OAUTH2_\|VITE_PWD_ENC_KEY\|basicAuth' bixi-ui/dist` | 1 | 预期无匹配；`frontend-secret-scan.txt` |

**有证据**：69 项定向测试覆盖 `TenantContextFilterTest`、`BixiTenantLineHandlerTest`、`TenantLineSqlMatrixTest`、`AllTenantsReadOnlyInterceptorTest`、`AuthenticationSecurityChainTest`、`RefreshTokenAccountStatusFilterTest`、`BixiDaoAuthenticationProviderTest`、`UpmsImportAuditTest`、`SysClientManagedBrowserTest`。其中导入测试验证真实方法权限链、审计 actor 和敏感字段排除；不能把这 3 项导入测试写成完整非法导入矩阵。

**有证据**：两次完整后端门禁各有 79 项既有环境条件跳过：78 项缺少 `OUTBOX_TEST_JDBC_URL`，另 1 项 Quartz 两节点故障接管缺少 `BIXI_QUARTZ_HA_JDBC_URL`。逐套件记录分别保存，未以另一模式的旧报告替代。完整门禁没有传入测试跳过参数；这些项目不计为通过。前端保留 `vform3-builds` 既有 eval 警告，构建成功。

**有证据**：提交前将证据与当前本地长密钥值比对，0 匹配，见 `evidence-secret-check.txt`。终端文本归档仅去除 ANSI 控制序列和行末空白，保留完整命令输出、退出码和失败内容。较大的完整日志以 gzip 归档，可用 `gzip -cd <文件>.txt.gz` 查看；测试和跳过项摘要保持可直接读取。

## 未完成项、边界和后续

- **文档声明（用户已确认）**：发行继续“暂不处理”；未启动四环境发行、外部账户、tag 或推送。
- **有证据**：本次只修复已有静态维护门禁，没有修改业务运行行为，没有新增功能或跨项目共享实现；未处理 ActBox、WebDeck、OpsPilot。
- **文档声明**：三项目仅统一证据字段。既有未验收的真实浏览器、完整跨租户和非法导入矩阵、外部供应商与高可用环境边界不因本次测试而自动通过。
- **文档声明：建议，待确认，当前不实施**：SPI → Webhook/SDK → 模板机制及签名模板/插件目录仍只保留未来路线，不建立目录或加载器。
- **文档声明**：OPEN 索引建议将 Bixi 当前维护提交更新为本次最终 hash，并绑定本目录；保留发行暂停和历史基线证据区别。
