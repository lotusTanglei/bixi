# 5. AI 开发上下文

## 1. 使用顺序

1. 仓库存在 `.codegraph/` 时，先执行 `codegraph explore "问题或符号"` 获取源码与调用路径。
2. 涉及模块边界时先读 [1_ARCHITECTURE.md](1_ARCHITECTURE.md)。
3. 选择最接近的现有实现，控制改动范围，不复制双模业务代码。
4. 按本页“完成定义”运行验证，不能用“已编译”代替运行验收。

根目录 [AGENTS.md](../AGENTS.md) 是机器优先读取的精简契约，本页提供任务执行细节。

## 2. 架构地图与职责

| 区域 | 职责 | 禁止事项 |
| --- | --- | --- |
| `bixi-common/*` | 核心、安全、数据源、MyBatis、Feign、日志、MQ 等基础设施 | 依赖任意 `*-biz` |
| `bixi-module/*-api` | 跨模块契约、DTO、Feign 远程适配器 | 包含业务实现或依赖对应 `*-biz` |
| `bixi-module/*-biz` | 唯一业务实现、Controller、Service、Mapper | 为 single/cloud 复制实现 |
| `bixi-auth` | OAuth2 授权服务与 Token 管理 | 直接依赖 Feign 类型作为业务契约 |
| `bixi-gateway` | cloud 路由、过滤、限流 | 承载业务 CRUD |
| `bixi-single` | 单体组合根 | localhost Feign 回环、复制业务代码 |
| `bixi-ui` | 两种模式共享的 Vue 前端 | 按部署模式复制页面 |

依赖方向固定为：`部署入口 -> 业务实现 -> API 契约/公共模块`。跨模块能力使用 `*-api/service` 下的传输无关接口；cloud 选择 Feign 适配器，single 选择本地适配器。

## 3. 编码规范

**后端**

- Java 17，构造器注入使用 Lombok `@RequiredArgsConstructor`。
- 请求模型必须使用 Jakarta Validation；Controller 使用 `@Valid`。
- 分页使用 MyBatis-Plus `Page`，条件按需拼装，不能为可选条件生成恒真或恒假 SQL。
- 查询和详情使用 `@HasPermission("*_view")`，新增、修改、删除分别使用 `*_add/edit/del`。
- 所有写操作使用 `@SysLog`，标题应稳定且可被验收脚本断言。
- 只在确实消除重复或复杂度时增加抽象。

**前端**

- 使用 Vue 3 `<script setup>`、Composition API 和现有 `useTable`、`useMessage` 等工具。
- API 统一走 `/admin/...`；Nginx/Gateway 负责部署差异。
- 按钮权限使用 `v-auth`，值与后端、菜单 SQL 完全一致。
- 表单必须有校验、加载与失败状态；表格必须有分页、筛选、批量操作和稳定列宽。

**数据库**

- 表结构写入 `01_init_all_tables.sql`。
- 基础菜单、按钮权限、角色关联和必要示例数据写入 `04_init_data.sql`。
- 常用过滤、排序字段索引写入 `03_add_indexes.sql`。
- SQL 不写库名、个人路径、明文运行密码或环境地址。

## 4. 常用命令

```bash
# 运行环境
make init-env && make doctor
make start-cloud && make verify-cloud
make start-single && make verify-single
make diagnose
make stop

# 静态与构建门禁
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
```

本机 Maven 必须使用 Java 17；Makefile 会在 macOS 自动解析 Java 17。容器构建固定使用 Temurin 17。

## 5. 示例提示词

### 新增业务

```text
在 Bixi 新增 <业务名> 业务域。先用 CodeGraph 定位 demo/task 参考实现和依赖路径。
要求业务代码只维护一套，并同时支持 cloud 与 single；完成表结构、索引、CRUD、分页与详情、
Jakarta Validation、菜单、view/add/edit/del 按钮权限、所有写操作日志、Vue 页面与 API、基础测试。
将真实 CRUD 和日志检查加入统一验收，最后运行 architecture-check、双模后端门禁、frontend-ci，
并分别启动 cloud/single 执行同一验收脚本。报告实际通过项，不要把未执行项写成完成。
```

### 修复缺陷

```text
修复 <现象/复现步骤>。先用 CodeGraph 追踪入口、调用路径和影响面，写出可失败的最小回归测试后修复。
检查问题是否在 cloud 和 single 都存在，不增加部署模式特判或 localhost Feign 回环。
运行受影响模块测试、architecture-check，并用同一黑盒用例验证两种模式。保留无关工作树改动。
```

### 拆分服务

```text
评估并实施把 <业务域> 从现有模块拆为独立服务。先用 CodeGraph 输出调用方、数据所有权和动态调用路径。
保持业务契约位于 *-api，唯一实现位于 *-biz；cloud 使用 Feign 适配器，single 直接组合本地适配器，
消费者只依赖传输无关接口。补架构门禁防止反向依赖和回环调用，并运行完整双模构建与统一验收。
在改动说明中列出迁移、回滚和兼容性影响。
```

## 6. 完成定义

- 后端、前端、SQL、权限、审计和测试形成完整链路。
- cloud 与 single 复用同一业务实现并通过同一黑盒用例。
- `make architecture-check`、`make runtime-config-check` 与相关 CI 命令通过。
- 文档只声明实际已验证能力，明确已知限制。
- 最终执行 `codegraph sync .` 和 `git diff --check`。
