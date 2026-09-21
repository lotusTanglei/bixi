# bixi 架构决策记录（ADR）

> 本文件记录项目中的重大架构决策。每条 ADR 包含状态、背景、决策和后果四个章节。

## ADR 格式说明

每条 ADR 应遵循以下格式：

---

# ADR-{序号}：{决策标题}

## 状态

Accepted | Deprecated | Superseded by ADR-{XXX}

## 背景

{做这个决策时的上下文和问题陈述}

## 决策

{核心决策内容}

## 后果

### 正面

- ...

### 负面

- ...

---
日期：{YYYY-MM-DD}

---

> 请在下方添加新的 ADR 记录。

## ADR-WF-001：Flowable 双模可选装配与可靠协作

2026-09-21，Accepted。设计、替代方案、事务和迁移边界见 [详细 ADR](workflow/DESIGN.md)，实现与验收状态见 [工作流交付清单](workflow/PROGRESS.md)。该决策不代表各阶段已经验收。
