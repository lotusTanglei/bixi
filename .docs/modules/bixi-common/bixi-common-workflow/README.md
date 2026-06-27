# bixi-common-workflow

Flowable 工作流公共配置模块，提供工作流引擎的基础配置、监听器和工具类。

## 模块职责

- `WorkflowAutoConfiguration` 自动配置，初始化 Flowable 引擎相关 Bean
- `WorkflowProperties` 工作流属性配置
- 基础监听器：`BaseExecutionListener`（执行监听器）、`BaseTaskListener`（任务监听器）
- `WorkflowUtils` 工作流工具类，封装常用流程操作

## 关键文件

| 文件 | 说明 |
|------|------|
| `config/WorkflowAutoConfiguration.java` | 工作流自动配置 |
| `config/WorkflowProperties.java` | 工作流属性配置 |
| `listener/BaseExecutionListener.java` | 执行监听器基类 |
| `listener/BaseTaskListener.java` | 任务监听器基类 |
| `util/WorkflowUtils.java` | 工作流工具类 |

## 包路径

`com.lotus.bixi.common.workflow`
