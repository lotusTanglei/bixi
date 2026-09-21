# Rabbit 消费确认与失败证据细化

本批细化阶段二 2D，不改变已接受的 Outbox/Inbox 事务设计。公共代码仍无领域依赖；运行装配另行完成，不自动注册消费者。

- [ ] 在 common-mq/reliable 新增 `RabbitInboxListener`，固定来源/目标/exchange/routingKey，使用原始 Message 与严格 wire codec；禁止由消息选择 Java 类型或目标处理器。
- [ ] 正常提交后才 manual ack。处理失败只有 `InboxDeliveryException` 且重新核验同一不可变持久 Inbox 才可表示已接管；任意数据库或失败记录写入异常保持未确认，交给有退避的监听容器恢复。业务失败不能伪称成功。
- [ ] 无法解码、错误路由、未知 schema/source 或同 ID 冲突无法放进合法 Inbox，新增最小 `reliable_quarantine` / `JdbcQuarantineStore` 保存 FAILED 证据。键绑定目标、可信来源、实际运输元数据、body 和安全原因码；原 body 最多保存 256 KiB，超长保存前缀/完整 SHA256/长度及截断标记。无自动重放或自动删除。
- [ ] 隔离真实 Rabbit + MySQL 测试先 RED 后 GREEN：提交后重复、事务回滚后持久重试、确认前断链重投、无法保存失败证据、协议隔离和重复、未知 schema、错误来源、同 ID 内容冲突、超长/损坏字节。单次失去 ack 用真实通道关闭验证；进程 SIGKILL 故障仍归最终 2D 运行验收。
- [ ] 接收器只在所属 owner 的恢复 worker 已装配时创建。后续专用容器配置 MANUAL/prefetch 20，数据库不可用时有界停止/恢复间隔，禁止无限即时 requeue。`workflow.enabled=false` 不创建容器、worker 或专用连接。
- [ ] 独立规格/质量审查后才集成迁移/主 SQL/永久测试入口，并补证据；发送端已有独立规格复跑 18 项通过，质量复审与运行集成仍待完成。

实现副本、测试凭据和构建产物留在本地临时目录，不纳入仓库。不得改动另一安全任务的文件、进程或容器，不提交、不推送。
