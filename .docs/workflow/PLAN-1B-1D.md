# 阶段一业务契约与真实审批实施计划

> 延续已授权的 subagent-driven-development、TDD 和独立审查流程；不提交、不推送。完整任务范围仍以 REQUEST.md 为准。

目标：建立供请假业务使用的唯一工作流契约，并将既有流程/任务实现接入真实数据库和引擎测试，修复会阻止审批闭环的问题。前端/运行编排与完整阶段验收继续在 1E/1F 完成。

## 1B 契约和部署适配

文件：workflow-api/api/service/WorkflowService.java、api/feign/RemoteWorkflowService.java；workflow-biz/service/local/LocalWorkflowService.java；契约装配/HTTP 映射测试与架构脚本。

- [ ] 契约不包含 Spring MVC/Feign/Flowable 注解：发起、实例详情、审批历史、当前用户待办/已办、通过/拒绝/转办；结果沿用 R，分页使用可反序列化的 Page。
- [ ] 远程客户端继承同一契约，映射到实际 Controller URI/返回值；仅 workflow enabled + cloud 注册，传播既有登录身份。
- [ ] single 本地适配调用同一 Service，按相同权限与日志规则处理；不创建 Feign、不 HTTP 回环、不吞服务异常。
- [ ] 正向/禁用/两种模式的 Bean 选择测试；真实 HTTP 编解码验证当前用户分页与响应类型，依赖可替代而被测适配不可 mock。
- [ ] 更新架构门禁，消费者禁止导入 Workflow Remote* 类型。

## 1C/1D 真引擎审批基础

文件：workflow-biz 的 ProcessInstanceServiceImpl、WfTaskServiceImpl、监听器、processes；workflow-api DTO/状态常量；规范 SQL 和非破坏性迁移；真实引擎 Service 集成测试。

- [ ] 先用真实 Spring、MyBatis、Flowable 和规范表结构写回归：启动、人工完成/拒绝、同步完成及事务回滚。
- [ ] 修复抽象监听器引用、扩展表映射不一致和流程结束记录时序；不要吞异常提交部分状态。
- [ ] 使用可信当前用户校验任务指派/参与者/本人查询，非参与者拒绝；任务请求不能通过变量覆盖身份。
- [ ] 所有读写权限使用 workflow_process_view/add/edit 与 workflow_task_view/edit，写入审计；本地和远程路径保持一致。
- [ ] 第一阶段拒绝只允许结束流程，非空 targetActivityId 明确拒绝；终态不可继续审批。
- [ ] 独立 demo_leave_approval BPMN 使用服务器传入的审批人 ID、单一人工审批任务及结束监听器；版本/部署使用引擎已有机制。

## 验证

定向命令使用 Java 17：

```bash
mvn -Pcloud -pl bixi-module/bixi-workflow-biz -am \
  -Dtest='Workflow*Test' -Dsurefire.failIfNoSpecifiedTests=false test
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
```

H2 用于快速真实业务集成测试；MySQL 锁/事务、网关鉴权及最终业务回写须在后续共同黑盒验证，不以适配 mock 或窄测试替代。请假独立业务、SQL 菜单、前端和四组运行验收仍是阶段一必须完成部分。
