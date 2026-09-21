# 阶段一：双模可选装配与真实审批验收

日期：2026-09-21。四组基础运行验收通过；这里只声明基础审批闭环，不声明阶段二可靠投递、重启恢复或阶段三集群能力。

## 运行结果

| 组合 | 实际结果 | 原始本地日志 |
| --- | --- | --- |
| single enabled | 无 Gateway、Nacos、独立 Workflow，RabbitMQ 已停止；同一请假业务完成草稿/提交/通过/拒绝/取消、历史、权限及自动回写 | `/tmp/bixi-workflow-stage1-single-enabled-no-mq.log` |
| single disabled | 核心登录、CRUD、权限和审计通过；工作流/请假菜单隐藏、接口 404；已有请假状态分布和流程历史数量保持不变 | `/tmp/bixi-workflow-stage1-single-disabled.log` |
| cloud enabled | 经正常 Nginx/Gateway/Auth/UPMS/Workflow/Feign 链路完成相同用例，越权查询得到客户端拒绝响应，业务结果自动回写 | `/tmp/bixi-workflow-stage1-cloud-enabled-final.log` |
| cloud disabled | 核心验收通过；独立 Workflow 容器停止、菜单隐藏、接口 404；已有流程/业务数据保留 | `/tmp/bixi-workflow-stage1-cloud-disabled.log` |

环境为隔离的本地 Compose 项目 `bixi-workflow-stage1`、Java 17、Flowable 7.1.0、MySQL 8.4.3、Redis 7.4.2、RabbitMQ 4.0.5（cloud）。single 的无 MQ 覆盖只关闭既有 Rabbit 健康探针和消费者，未关闭工作流处理；可复用配置及命令见 [OPERATIONS.md](OPERATIONS.md)。未操作外部环境，未删除原有 Bixi 数据卷。

## 构建、事务与界面验证

- 最终 `make backend-cloud-ci backend-single-ci`：cloud 149 项、single 147 项通过，零失败/错误/跳过；日志 `/tmp/bixi-workflow-stage1-final-backends.log`。
- 真实 MySQL 8.0.45 / REPEATABLE-READ 双模共 116 项引擎/审批/请假测试通过；不以 H2 替代 MySQL 锁和隔离验证。
- `make frontend-ci` 通过；16 项真实 Vue setup 脚本回归通过，覆盖草稿切换串单、旧响应/错误/结束回调、校验和保存期间重复操作、权限组合。
- 启停装配包含真实 DynamicTp 和 Flowable Web 上下文：9 项通过，验证原线程池复用、关闭所有权、禁用时无引擎/工作流执行器。
- 远程错误适配使用真实 Spring Feign HTTP：16 项通过，包含 Sentinel 开关；保留权限和客户端错误，保留真实 HTTP/连接故障。
- 菜单迁移在带规范唯一、CHECK、外键约束的 MySQL 上通过 12 项检查；轮次/请假迁移重复执行、旧数据保留及规范字段一致性通过。详细证据见 [EVIDENCE-1C.md](EVIDENCE-1C.md)。

Google Chrome 153.0.8010.50 真实浏览器补充检查通过：请假新建、编辑后数据库内容、详情，以及待办/已办/流程/定义动态路由正常；390×844 手机视口下表单完整可见，零浏览器运行异常。浏览器使用正常 OAuth 接口取得登录态，未伪造业务 API 响应。日志 `/tmp/bixi-workflow-stage1-browser.log`，截图位于 `/tmp/bixi-workflow-browser/`；临时草稿和测试审批人已清理。

## 本阶段修复的真实运行缺陷

Flowable 的 Spring 执行器要求与原 DynamicTp 类型不匹配；非法业务状态和远程权限拒绝误变成 HTTP 500；Workflow 单独 profile 缺少 Nacos 配置依赖；后端容器 IP 变化后 Nginx 保留旧解析；自定义菜单 ID 被错误授权；草稿快速切换时旧数据覆盖新编辑目标。每项均有实际失败证据与修复后验证，不只检查注解或 mock 被测业务。

## 使用与剩余边界

在可丢弃开发库显式设置 `WORKFLOW_ENABLED=true`、`WORKFLOW_SCHEMA_UPDATE=true`，使用 `make start-single/verify-single` 或 `make start-cloud/verify-cloud`。已有库按增量迁移说明升级，生产 schema-update 保持 false。关闭需要修改开关并重启；双模可部署不代表可热切换，模式迁移边界见操作文档。

当前 AFTER_COMMIT 通知只覆盖正常运行；提交响应丢失、跨服务宕机、进程重启与重复消息尚无持久化恢复保证。无绑定的 SUBMITTING 不盲目重新发起。下一步按 [PLAN-STAGE2.md](PLAN-STAGE2.md) 实施稳定请求标识、命令结果、Outbox/inbox、本地/Rabbit 适配及自动任务补偿，再继续阶段三、四。完整任务仍在进行，未提交或推送代码。
