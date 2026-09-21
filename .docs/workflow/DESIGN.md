# ADR-WF-001：Flowable 双模可选装配与可靠协作

日期：2026-09-21。状态：Accepted（任务已授权普通实现选择自行决策；实现和验收进度独立记录）。

## 目标与边界

维持 Java 17、Flowable 7.1.0、MySQL、Spring 本地事务及现有身份/权限体系。workflow-biz 保存唯一引擎业务实现；workflow-api 保存传输无关接口、DTO、事件；common-workflow 仅承载基础配置。请假示例放在 UPMS 的独立 demo/leave 域，只依赖 workflow-api。部署入口不复制业务代码。

## 选择及替代方案

采用运行时显式 `workflow.enabled=true` 启用，默认关闭；single 打包同一 workflow-biz，运行时决定是否创建组件。cloud 独立 Workflow 服务。与仅用 Maven profile 剔除依赖相比，这可在同一制品上验证四组启停；代价是 single 制品包含关闭时不用的类。与对每个调用点手写条件相比，集中引擎自动配置过滤和统一业务条件可避免关闭时误启动后台执行器。

同步契约与持久化异步投递分别适配，不造通用多引擎或消息平台。同步调用用 `api.service` 接口，cloud Feign、single 本地。可靠命令/事件使用同一持久化数据契约，single 本地处理器、cloud Rabbit 适配。纯内存事件和仅 AFTER_COMMIT 不能保证重启恢复，因此不作为可靠性实现。

## 装配与关闭

在 Spring Boot 自动配置筛选阶段屏蔽关闭状态下的 Flowable 自动配置；覆盖 starter 引入的 process/CMMN/DMN/event registry 等引擎。启用时仅装配任务需要的 BPMN 引擎，不引入第二种业务引擎。条件同时覆盖业务 Controller/Service/Mapper/监听器、Feign、本地适配、投递器及消费者。独立启动类限制 cloud，避免 single 扫描导入 Nacos/Feign/额外应用配置。

网关路由与 UPMS 菜单必须读取同一启用配置。关闭不删除数据、不拉取或确认事件。重启启用后由持久化领取器恢复。引擎 schema-update 默认 false，开发/集成测试显式 true；生产先单独迁移再启动所有副本。

## 状态与业务界限

请假单采用 DRAFT → SUBMITTING → IN_REVIEW → APPROVED / REJECTED / CANCELED；阶段一拒绝表示终止，不接受任意节点跳转作为退回。阶段二提交同时落业务状态与启动命令，最终回写允许暂态；不得依赖 single 大事务来保证 cloud 不具备的正确性。重新申请使用显式轮次，原轮次终态保持不变。

任务权限由服务器当前身份、指派/候选关系及数据参与关系决定；客户端提交的操作者、角色或租户不可作为授权依据。现有租户平台是否可用待身份链核查，单纯 entity.tenantId 不视为平台。异步命令保存可信身份快照和关联上下文，不依赖请求线程。

## 可靠性与事务

Flowable、扩展记录和事件在同一 datasource / transaction manager 的本地事务中提交；真实集成测试强制异常验证一起回滚。业务提交与业务命令同事务；消费者 inbox 唯一 eventId 与业务更新同事务。Outbox 支持有界领取、租约、退避、失败状态、人工重试与对账；成功执行但尚未标记的窗口靠业务幂等收敛。

事件包含 eventId/type/schemaVersion/processInstanceId/businessId/round/追踪上下文。以唯一请求键和规范化内容摘要识别同请求重试与内容冲突。使用数据库唯一约束及任务状态竞争控制并发；不能只靠 JVM 锁或 Rabbit 去重。自动任务请求/结果都持久化，超时/迟到/补偿有明确状态机和幂等键。

## 验证和迁移边界

先用实际 Flowable + Spring + H2 验证装配、事务及基本引擎行为，H2 不证明 MySQL 锁语义；随后实际 MySQL/Rabbit 及同一黑盒业务套件覆盖四组组合。多节点运行共享库，记录宕机后锁到期与恢复时间；不宣称数据库高可用或跨地域容灾。

模式迁移必须停流量和所有投递器、处理在途请求、备份业务/引擎/Outbox/inbox、明确 MQ 积压处理与投递所有权、切换配置后仅启动一种适配器、对账后开放流量。支持双模不是热切换承诺。旧定义与对应执行代码保持兼容；实例迁移必须显式执行。
