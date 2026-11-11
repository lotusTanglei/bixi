# Bixi 发行门禁证据（2026-11-11）

## 基线

- 仓库：`/Users/dundundebaba/sync/code/open/bixi`
- 分支：`main`
- 提交：`2b5649d`（`docs(release): align ledger with four environments`）
- 工作树：`git status --short --branch` 为 `## main...origin/main`，无未提交或未推送提交。

## 后端与前端门禁

| 命令 | 退出码 | 结果 |
| --- | ---: | --- |
| `make backend-cloud-ci` | 0 | 28/28 Maven reactor 模块 `BUILD SUCCESS`；测试无失败、无错误 |
| `make backend-single-ci` | 0 | 25/25 Maven reactor 模块 `BUILD SUCCESS`；测试无失败、无错误 |
| `npm run build:prod`（`bixi-ui`） | 0 | Vite production build 成功 |
| `node --test tests/browser-auth.test.mjs`（`bixi-ui`） | 0 | 6/6 通过 |
| `rg 'VITE_OAUTH2_|VITE_PWD_ENC_KEY|basicAuth' dist` | 1（无匹配） | 产物未包含浏览器密钥或 `basicAuth` |

## 双模运行验收

验收使用临时 Compose 项目 `bixi-accept-20261111` 和全新 MySQL/Redis/Rabbit/Nacos 卷，避免复用旧本地数据库。启动后执行 `make verify-*`，验收脚本对数据库执行临时 CRUD 和审计写入；验收完成后已停止并移除临时容器，未删除原 `bixi` 项目卷。

| 命令 | 退出码 | 结果 |
| --- | ---: | --- |
| `BIXI_ENV_FILE=/tmp/bixi-accept-20261111.env make start-cloud` | 0 | cloud 全部依赖和服务健康 |
| `BIXI_ENV_FILE=/tmp/bixi-accept-20261111.env make verify-cloud` | 0 | 登录/刷新、短信拒绝、双资源 CRUD、审计、工作流关闭态检查通过 |
| `BIXI_ENV_FILE=/tmp/bixi-accept-20261111.env make start-single` | 0 | single 与前端健康 |
| `BIXI_ENV_FILE=/tmp/bixi-accept-20261111.env make verify-single` | 0 | 与 cloud 相同业务断言和审计检查通过 |

第一次直接复用旧 `bixi` 卷执行 `make verify-cloud` 返回 401（`客户端查询异常，请检查数据库链接`），只读查询显示该旧卷缺少 `bixi-browser` 行；全新卷按当前 `bixi-project-documents/sql/02_data.sql:246` 初始化后通过。该失败记录为旧卷基线问题，不判定当前代码失败。

## 未完成与阻塞

- N24-C / N24-S 仍需目标机环境，未在本机伪造 PASS。
- 完整黑盒浏览器尺寸/键盘矩阵、跨租户负向矩阵、外部供应商送达、Quartz/SBA 故障接管和生产恢复目标不在本轮门禁。
- 原 `bixi` 本地卷保留旧数据；若要在该卷上复验，需要先执行受控数据迁移或明确重置策略。
