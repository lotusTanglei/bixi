# 阶段三第一切片：锁 owner 与运行诊断

更新时间：2026-09-22。本记录只覆盖阶段三第一切片，不代表阶段三或路线图整体完成。

## 已交付

- `WorkflowProperties` 增加进程级随机默认 `lockOwner`，并增加异步/定时锁时长、领取等待、领取批量、过期扫描页大小、reset/unlock 开关。
- `WorkflowAutoConfiguration` 将这些值绑定到实际 Flowable `SpringProcessEngineConfiguration`，并对正值、溢出和 owner 长度执行校验。
- Workflow 恢复服务新增脱敏运行诊断：锁 owner、Bixi/Flowable 执行器 active/auto-activate、锁/领取/过期恢复参数、Flowable 可执行/定时/死信任务计数、Workflow Outbox/Inbox pending/failed 计数、两类最早等待时间和最近失败摘要。
- 诊断 SQL 只读取状态、时间、错误摘要和 eventId，不读取或返回消息 payload；Inbox pending 正确按 `RECEIVED/IN_FLIGHT` 统计，Outbox pending 按 `PENDING/IN_FLIGHT` 统计。
- 新增 `GET /workflow/recovery/diagnostics`，复用 `workflow_recovery_view` 权限；共享恢复页增加 Workflow owner 的“运行诊断”页签，UPMS owner 切换时不会调用不存在的诊断接口。

## 实际验证

- `WorkflowRecoveryServiceTest`：5/5；覆盖实际 Flowable 执行器配置读取、Flowable 三类任务计数、Outbox/Inbox 状态统计、Inbox `RECEIVED` 积压、最近失败摘要和 payload 不出现在返回对象中。
- `WorkflowRecoveryControllerTest`：1/1；确认诊断接口委托恢复服务并返回只读结果。
- 定向 Maven reactor：Workflow recovery/controller **6/6**，`BUILD SUCCESS`。
- `npm run build:prod`：`BUILD SUCCESS`。
- `npx eslint src/api/workflow/recovery.ts src/views/workflow/recovery/index.vue`：通过。

## 明确未完成

- 尚未启动两个独立 Workflow 应用副本验证同库领取、锁过期、优雅停机和进程 SIGKILL 恢复；当前配置测试是 H2/真实 Flowable 引擎读取证据，不是双副本故障证据。
- 尚未完成受控 Flowable schema 迁移窗口、新旧 BPMN definition 并存/显式迁移、异步边界故障脚本和四组启停报告。
- 当前诊断页未替代真实 MySQL/Rabbit 运行态恢复报告；最近失败摘要和积压时间需要在后续故障演练中记录实测值。
