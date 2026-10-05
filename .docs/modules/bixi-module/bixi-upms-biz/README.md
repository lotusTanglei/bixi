# bixi-upms-biz

UPMS（统一权限管理）业务实现。Controller、Service、Mapper、实体和本地适配器都在本模块中，由 cloud 服务和 `bixi-single` 共同复用；`*-api` 只承载跨模块契约。

## 模块职责

- 用户、角色、菜单、部门、岗位、租户、字典、公共参数、日志和文件管理。
- OAuth 客户端、注册、手机登录、候选人身份/角色、敏感词和系统信息查询。
- 站内通知和 SSE 推送；通知消费者、回执接口以及 Email、Webhook、SMS、WeChat 通道适配器。
- Demo Task 和请假示例业务，以及与 Workflow 的启动、任务通知、结果回传和恢复接口。
- 可靠投递开启后提供 UPMS outbox/inbox/quarantine、幂等和恢复查询；未开启时这些恢复组件不装配。

## 验收资源

`acceptance` 包提供两组可独立验收的分页 CRUD 资源：

- `/dictAggregate`：字典聚合的分页、详情、新增、修改、删除、Excel 导入和导出。
- `/publicParam`：公共参数的分页、详情、新增、修改、删除、Excel 导入和导出。

两组资源都使用 `@HasPermission` 和 `@SysLog`。权限名分别使用 `acceptance_dict_aggregate_*` 与 `acceptance_public_param_*`；导入接口限制文件大小为 5 MB、数据行数为 1000 行，并返回逐行错误信息。

## 双模式适配

- cloud 使用 `RabbitNoticeDelivery` 和 `NoticeConsumer` 连接 RabbitMQ；single 使用进程内的 `LocalNoticeDelivery`，不需要 RabbitMQ。
- `bixi.reliable.enabled=true` 且 Workflow 已启用时，装配请假/Workflow 可靠消息的 outbox/inbox 和恢复组件。cloud 只有在 `bixi.reliable.rabbit.enabled=true` 时使用 Rabbit 传输；single 使用 `LocalDurableTransport`。
- Email、Webhook、SMS、WeChat 通道和第三方回执由 `bixi.notice.*` 配置控制，真实送达仍依赖外部供应商；回执入口使用配置的 HMAC 密钥校验。

## 关键文件

| 文件 | 说明 |
|------|------|
| `BixiUPMSApplication.java` | cloud 模式 UPMS 启动类 |
| `controller/SysUserController.java` | 用户管理控制器 |
| `controller/SysNoticeController.java` | 通知管理控制器 |
| `acceptance/controller/SysDictController.java` | 字典聚合 CRUD、导入导出 |
| `acceptance/controller/SysPublicParamController.java` | 公共参数 CRUD、导入导出 |
| `mq/RabbitNoticeDelivery.java` | cloud Rabbit 通知投递 |
| `mq/LocalNoticeDelivery.java` | single 进程内通知投递 |
| `service/UpmsRecoveryService.java` | 可靠投递恢复查询与对账 |
| `demo/leave/` | 请假 Workflow 集成和恢复示例 |

## 配置与数据库

- `BIXI_RELIABLE_ENABLED` 默认关闭；cloud 的 Rabbit 适配还需要 `BIXI_RELIABLE_RABBIT_ENABLED=true`。
- 模块表结构、菜单、权限和增量迁移位于 `bixi-project-documents/sql`，存量库按迁移说明执行，不要整体重放初始化脚本。

## 包路径

`com.lotus.bixi.upms`
