# 阶段 1A：可选引擎装配实施计划

> 使用 superpowers:subagent-driven-development 按任务执行并进行规格与代码审查。用户禁止自动提交；保留所有改动在工作区。

**目标：** 先证明同一 Flowable 7.1.0 实现能显式启用、完全关闭，然后聚合到 single；本批不宣称审批阶段完成。

**架构：** common-workflow 负责配置筛选与引擎参数；workflow-biz 的全部组件按统一开关注册，入口只做 cloud 装配；single 增加业务模块依赖。

**技术栈：** Java 17、Spring Boot 3.4.1、Flowable 7.1.0、JUnit 5、H2（本批装配测试），之后补 MySQL 运行验收。

## 任务一：真实上下文启停测试及引擎配置

文件：common-workflow 的 pom.xml、config/WorkflowAutoConfiguration.java、WorkflowProperties.java、新的自动配置筛选类与 META-INF/spring.factories；src/test 下新增 WorkflowEngineConfigurationTest。

- [x] 使用完整 Flowable 自动配置候选集和真实 DataSource/transaction manager 创建上下文；分别测试未指定、false、true。
- [x] 红灯：关闭时 `assertThat(context).doesNotHaveBean(ProcessEngine.class)`，不应有 AsyncExecutor 或其他 Flowable 引擎；启用应只有一个引擎。
- [x] 运行 `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Pcloud -pl bixi-common/bixi-common-workflow -am -Dtest=WorkflowEngineConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`，记录真实失败。
- [x] 用 AutoConfigurationImportFilter 在禁用时拒绝 `org.flowable.spring.boot.*`；配置默认关闭、默认不更新 schema；映射 history level 和 async 开关。
- [x] 真实 BPMN 发起、完成及事务回滚；禁止 mock 引擎和被测配置。
- [x] 相同命令绿灯；同时验证启用但未迁移引擎表时应明确失败，避免隐式更改表结构。

## 任务二：业务组件与 single 聚合

文件：workflow-biz 中各组件、WorkflowApplication.java、application.yml；bixi-single/pom.xml 与 application.yml；业务条件上下文测试。

- [x] 红灯：扫描 workflow-biz 且 `workflow.enabled=false` 时没有 Controller/Service/Mapper/监听器，不要求 Flowable bean。
- [x] 增加统一条件注解用于所有业务组件；启动入口限制 cloud；single 导入相同 jar。
- [x] single 配置默认关闭，独立 cloud 配置使用正确顶层 flowable 与 workflow 属性；避免 Nacos 配置泄漏。
- [x] 执行双模受影响模块编译/测试以及 `make architecture-check` 和 `make runtime-config-check`。
- [x] 更新架构/发布基线及本目录进度，标明仅组件测试或真实运行测试的区别。

## 下一批依赖

1B 开始前核对新增装配没有重复 bean / 循环依赖；以新契约连接请假示例。1C–1F 的实现计划根据已验证的装配和业务契约展开，不能跳过四组运行验收就宣称阶段一通过。后续阶段完整要求保留在 REQUEST.md / PROGRESS.md。

本批测试、双模核心回归及已知限制见 [验收记录](EVIDENCE-1A.md)。以上完成标记只覆盖本计划，不表示 REQUEST.md 的任一完整阶段已验收。
