# Flowable 双模式工作流续接提示词

## 项目背景

Bixi 企业平台（Java 17 + Vue 3）正在实现 Flowable 7.1.0 工作流引擎的双模式部署：
- **cloud 模式**：Gateway、Auth、UPMS、Workflow 作为独立微服务，通过 Nacos + Feign 通信
- **single 模式**：Auth、UPMS、Generator、Quartz、Workflow 聚合在 `bixi-single` 单进程

当前分支：`codex/flowable-dual-mode`（基于 main）

## 当前开发进度：~36%

### 阶段完成情况

| 阶段 | 完成度 | 状态 |
|------|--------|------|
| 阶段一：装配与真实审批 | 100% | ✓ 完成，含 1A-1F 全部子任务 |
| 阶段二 2A：请求幂等 | 100% | ✓ 完成，任务命令幂等、并发审批、事务回滚 |
| 阶段二 2B：Outbox/Inbox 基础 | ~75% | 基础设施类完成，待业务配置和领域处理器 |
| 阶段二 2C：可靠协作切换 | ~20% | 代码部分存在，运行链路未激活 |
| 阶段二 2D：Rabbit 传输验证 | ~10% | 发送端通过 18 项测试，接收端刚完成 |
| 阶段二 2E-2F：故障注入/报告 | 0% | 未开始 |
| 阶段三：集群与恢复 | 0% | 未开始 |
| 阶段四：审批扩展 | 0% | 未开始 |

**整体估算**：阶段一 100% + 阶段二 ~35% = 整体 ~36%

## 已完成的关键工作

### 阶段一（100%）
- ✓ 条件装配引擎、执行器、业务组件（1A）
- ✓ 传输无关契约、cloud Feign / single 本地适配（1B）
- ✓ 独立请假示例、提交、列表、详情、状态转换（1C）
- ✓ 待办/已办/通过/拒绝结束/历史/业务回写（1D）
- ✓ 共享前端页面、表/索引/菜单、权限、缓存开关（1E）
- ✓ 统一黑盒验收、四组启停、真实事务/引擎集成（1F）

### 阶段二 2A（100%）
- ✓ 稳定请求标识、内容摘要、唯一约束
- ✓ 任务命令幂等、并发审批、真实事务回滚
- ✓ 同步完成时序、双模 CI 和浏览器恢复验证

### 阶段二 2B（~75%）
**已完成的基础设施类**（位于 `bixi-common/bixi-common-mq/src/main/java/com/lotus/bixi/common/mq/reliable/`）：

1. **DurableMessage.java** - 不可变消息信封，规范 JSON + SHA-256 哈希
2. **DurableTransport.java** - 传输抽象接口 `void deliver(DurableMessage)`
3. **LocalDurableTransport.java** - 同进程传输（single 模式）
4. **JdbcOutboxStore.java** - JDBC Outbox，事务内写入，租约领取
5. **JdbcInboxStore.java** - JDBC Inbox，持久化接收，租约处理
6. **JdbcQuarantineStore.java** - 隔离区，记录无法处理的消息
7. **OutboxDispatcher.java** - Outbox 轮询发送器，超时中断
8. **InboxExecutor.java** - Inbox 执行器，路由注册，处理/恢复
9. **DurableMessageHandler.java** - 业务处理器接口
10. **DurableMessageWireCodec.java** - 线格式编解码（JSON ↔ byte[]）
11. **RabbitDurableTransport.java** - Rabbit 路由描述符（Route 记录）
12. **RabbitOwnerEndpoint.java** - Rabbit 发送+接收端点，SmartLifecycle phase 100
13. **RabbitInboxListener.java** - MANUAL-ack 监听器，解码/路由/隔离区
14. **ReliableRabbitProperties.java** - Rabbit 连接配置，前缀 `bixi.reliable.rabbit`
15. **ReliableDeliveryProperties.java** - 投递参数（轮询间隔、租约时长、重试退避）
16. **InboxDeliveryException.java** - 投递异常（RETRYABLE/PERMANENT/CONFLICT）
17. **OutboxConflictException.java** - Outbox 冲突异常

**测试状态**：
- 两 Profile 各 55 项真实 MySQL 原语测试通过
- Rabbit 发送端在隔离副本通过 18 项实际传输及协议测试
- 接收端刚完成，待集成验证

**待完成**：
- 业务配置类（`WorkflowReliableConfiguration`、`LeaveReliableConfiguration`）
- 领域处理器（可靠提交、审批回写）
- 激活可靠运行链路（从 LocalDurableTransport 切换到 RabbitOwnerEndpoint）

## 关键约束与规则

### 禁止操作
- ✗ 自动提交、推送、外部部署
- ✗ 编辑 `.docs/.chiwen.state.json`
- ✗ 改动 Flowable 7.1.0 版本
- ✗ 使用 localhost Feign 环回
- ✗ 在 `bixi-common/*` 依赖 `*-biz` 模块
- ✗ 在 `*-api` 依赖对应的 `*-biz`

### 必须遵守
- ✓ cloud 和 single 使用同一业务实现
- ✓ 传输无关契约（`com.lotus.bixi.*.api.service`）
- ✓ 权限名 snake_case：`<domain>_<resource>_view|add|edit|del`
- ✓ 审计日志 `@SysLog` 覆盖所有写操作
- ✓ 权限检查 `@HasPermission` 覆盖所有读写
- ✓ 前端按钮权限 `v-auth` 与后端一致

### 验收门禁
```bash
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
make verify-cloud
make verify-single
codegraph sync .
git diff --check
```

## 已知问题

### Lombok 编译阻塞
**问题**：Lombok 1.18.36 + JDK 17.0.2 导致 `ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`

**影响范围**：所有使用 `@Slf4j`、`@Getter`、`@Setter` 等 Lombok 注解的模块

**临时解决方案**：
```bash
mvn compile -Dmaven.compiler.proc=none
```
这会跳过注解处理，但会导致 Lombok 生成的代码（`log`、`getData()` 等）缺失，仅适用于不依赖 Lombok 的模块（如 `bixi-common-mq` 的 reliable 包）。

**根本解决方案**（待实施）：
1. 升级 JDK 到 17.0.3+ 或切换到 Temurin 17
2. 或降级 Lombok 到 1.18.32（需验证与 Spring Boot 3.4.1 兼容性）
3. 或在 `pom.xml` 的 `<dependencyManagement>` 中强制指定 Lombok 版本

**当前状态**：`bixi-common-mq` 模块的 reliable 包（17 个文件）已全部编译通过（使用 `-proc=none`），不依赖 Lombok。

## 下一步工作优先级

### P0：完成阶段二 2B/2C
1. **创建业务配置类**
   - `bixi-module/bixi-workflow-biz/src/main/java/com/lotus/bixi/workflow/config/WorkflowReliableConfiguration.java`
   - `bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo/leave/config/LeaveReliableConfiguration.java`
   - 配置 Outbox/Inbox 存储、传输端点、领域处理器注册

2. **实现领域处理器**
   - 工作流可靠提交处理器（启动流程 + 写 Outbox）
   - 审批回写处理器（接收 Inbox 消息 + 更新业务状态）
   - 处理器需实现 `DurableMessageHandler` 接口

3. **激活可靠运行链路**
   - cloud 模式：从 `LocalDurableTransport` 切换到 `RabbitOwnerEndpoint`
   - single 模式：保持 `LocalDurableTransport`
   - 通过 Spring Profile 和条件配置控制

4. **集成测试**
   - 实际 RabbitMQ 传输测试（发送 + 接收 + 确认）
   - 故障注入测试（网络中断、Rabbit 重启、数据库不可用）
   - 恢复测试（进程重启后 Outbox/Inbox 续传）

### P1：解决 Lombok 编译问题
- 升级 JDK 或降级 Lombok，确保全项目可编译
- 验证所有模块（包括 workflow-biz、upms-biz）编译通过

### P2：阶段二 2D-2F
- Rabbit 传输完整验证（确认、不可路由、消费确认）
- 重复/乱序/过期/轮次/版本处理
- 故障注入及阶段报告

### P3：阶段三（集群与恢复）
- 双 Workflow 副本、统一数据库
- BPMN 异步边界、锁过期去重
- 迁移策略、新旧定义并存

### P4：阶段四（审批扩展）
- 候选人/组、转办/委派、退回/重新提交
- 会签/或签、超时提醒、表单版本绑定

## 关键文件位置

### 可靠交付基础设施
```
bixi-common/bixi-common-mq/src/main/java/com/lotus/bixi/common/mq/reliable/
├── DurableMessage.java              # 消息信封
├── DurableTransport.java            # 传输接口
├── LocalDurableTransport.java       # 同进程传输
├── JdbcOutboxStore.java             # Outbox 存储
├── JdbcInboxStore.java              # Inbox 存储
├── JdbcQuarantineStore.java         # 隔离区存储
├── OutboxDispatcher.java            # Outbox 发送器
├── InboxExecutor.java               # Inbox 执行器
├── DurableMessageHandler.java       # 处理器接口
├── DurableMessageWireCodec.java     # 线格式编解码
├── RabbitDurableTransport.java      # Rabbit 路由
├── RabbitOwnerEndpoint.java         # Rabbit 端点
├── RabbitInboxListener.java         # Rabbit 监听器
├── ReliableRabbitProperties.java    # Rabbit 配置
├── ReliableDeliveryProperties.java  # 投递参数
├── InboxDeliveryException.java      # 投递异常
└── OutboxConflictException.java     # 冲突异常
```

### 工作流业务实现
```
bixi-module/bixi-workflow-biz/src/main/java/com/lotus/bixi/workflow/
├── config/                          # 配置类（待创建 WorkflowReliableConfiguration）
├── controller/                      # REST 控制器
├── service/                         # 业务服务
├── listener/                        # Flowable 监听器
└── entity/                          # 实体类
```

### 请假示例
```
bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo/leave/
├── config/                          # 配置类（待创建 LeaveReliableConfiguration）
├── controller/                      # REST 控制器
├── service/                         # 业务服务
└── entity/                          # 实体类
```

### 文档与计划
```
.docs/workflow/
├── PROGRESS.md                      # 开发进度（本文档的权威来源）
├── REQUEST.md                       # 完整需求
├── DESIGN.md                        # 架构设计
├── PLAN-STAGE2.md                   # 阶段二计划
├── EVIDENCE-1A.md ~ EVIDENCE-1F.md  # 阶段一验收记录
├── EVIDENCE-2A.md                   # 阶段二 2A 验收记录
├── EVIDENCE-2B.md                   # 阶段二 2B 证据（待更新）
├── EVIDENCE-STAGE1.md               # 阶段一完整报告
└── LEAVE_STAGE1.md                  # 请假示例说明
```

## 数据库表结构

### 可靠交付表（待创建或已创建但未迁移）
```sql
-- reliable_outbox：业务事务内写入，OutboxDispatcher 轮询发送
CREATE TABLE reliable_outbox (
    source_owner VARCHAR(64) NOT NULL,
    event_id CHAR(36) NOT NULL,
    dedup_key VARCHAR(191) NOT NULL,
    target_owner VARCHAR(64) NOT NULL,
    type VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    aggregate_key VARCHAR(191),
    aggregate_sequence BIGINT,
    status ENUM('PENDING', 'IN_FLIGHT', 'DELIVERED', 'FAILED') NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36),
    lease_until DATETIME(6),
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(256),
    delivered_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_owner, event_id),
    UNIQUE KEY uk_dedup (source_owner, dedup_key),
    INDEX idx_reliable_outbox_due (source_owner, status, next_attempt_at),
    INDEX idx_reliable_outbox_lease (source_owner, status, lease_until)
);

-- reliable_inbox：接收后持久化，InboxExecutor 处理
CREATE TABLE reliable_inbox (
    target_owner VARCHAR(64) NOT NULL,
    event_id CHAR(36) NOT NULL,
    source_owner VARCHAR(64) NOT NULL,
    type VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status ENUM('RECEIVED', 'IN_FLIGHT', 'PROCESSED', 'IGNORED', 'FAILED') NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36),
    lease_until DATETIME(6),
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(256),
    processed_at DATETIME(6),
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (target_owner, event_id),
    INDEX idx_reliable_inbox_due (target_owner, status, next_attempt_at),
    INDEX idx_reliable_inbox_lease (target_owner, status, lease_until)
);

-- reliable_quarantine：无法处理的消息隔离
CREATE TABLE reliable_quarantine (
    evidence_id VARCHAR(128) NOT NULL,
    body_json JSON,
    reason VARCHAR(256) NOT NULL,
    quarantined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (evidence_id)
);
```

## 技术要点

### 消息信封设计
- **规范 JSON**：键排序、数字标准化、Unicode 校验
- **SHA-256 哈希**：覆盖 sourceOwner + targetOwner + type + schemaVersion + payloadJson
- **UUID eventId**：全局唯一，用于去重和幂等

### Outbox 模式
- 业务事务内写入 `reliable_outbox`（PENDING）
- `OutboxDispatcher` 轮询领取（IN_FLIGHT + 租约）
- 发送到 RabbitMQ 或本地 Inbox
- 发送成功标记 DELIVERED，失败回退 PENDING 或 FAILED
- 租约过期自动恢复

### Inbox 模式
- 接收消息后写入 `reliable_inbox`（RECEIVED）
- 领取租约（IN_FLIGHT）
- 调用业务处理器
- 成功标记 PROCESSED/IGNORED，失败回退 RECEIVED 或 FAILED
- 租约过期自动恢复

### Rabbit 传输
- 私有 `CachingConnectionFactory`，CORRELATED 确认
- `mandatory=true`，确保消息路由到队列
- Quorum 队列，`x-delivery-limit=-1`（无限重试）
- MANUAL-ack，解码/路由失败进隔离区

### 租约机制
- 租约时长 30 秒（可配置）
- 租约令牌防止重复处理
- 租约过期后其他 worker 可领取
- 处理前验证租约有效性

## 续接建议

1. **先解决 Lombok 编译问题**，确保全项目可编译
2. **创建业务配置类**，注册 Outbox/Inbox 存储和处理器
3. **实现领域处理器**，从简单场景开始（如请假审批回写）
4. **激活可靠链路**，先在 single 模式验证，再切换到 cloud 模式
5. **逐步故障注入**，验证恢复机制
6. **更新 PROGRESS.md**，记录每轮进展

## 参考命令

```bash
# 检查架构
make architecture-check

# 启动 cloud 模式
make start-cloud

# 启动 single 模式
make start-single

# 验收 cloud 模式
make verify-cloud

# 验收 single 模式
make verify-single

# 后端 CI（cloud）
make backend-cloud-ci

# 后端 CI（single）
make backend-single-ci

# 前端 CI
make frontend-ci

# 诊断问题
make diagnose

# 停止所有服务
make stop

# 仅编译 mq 模块（绕过 Lombok）
JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
mvn compile -pl bixi-common/bixi-common-mq -am -Dmaven.compiler.proc=none
```

---

**生成时间**：2026-09-21
**当前分支**：`codex/flowable-dual-mode`
**下次续接**：从阶段二 2B 业务配置和领域处理器开始
