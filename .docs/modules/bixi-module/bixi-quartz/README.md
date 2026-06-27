# bixi-quartz

Quartz 定时任务模块，支持任务 CRUD 管理和多种调用方式。

## 模块职责

- 定时任务管理：任务创建、修改、删除、暂停、恢复
- 任务执行记录：`SysJob` 任务实体、`SysJobRecord` 执行记录
- 多种调用方式（`TaskInvokFactory` 工厂模式）：
  - `SpringBeanTaskInvok` — Spring Bean 方法调用
  - `JavaClassTaskInvok` — Java 类反射调用
  - `RestTaskInvok` — REST 接口调用
  - `JarTaskInvok` — JAR 包调用
- Quartz 配置：`BixiQuartzConfig`、`BixiQuartzFactory`、`BixiQuartzInvokeFactory`
- 应用启动时自动初始化任务（`BixiInitQuartzJob`）

## 关键文件

| 文件 | 说明 |
|------|------|
| `entity/SysJob.java` | 定时任务实体 |
| `entity/SysJobRecord.java` | 任务执行记录 |
| `util/TaskInvokFactory.java` | 任务调用工厂 |
| `config/BixiQuartzConfig.java` | Quartz 配置 |
| `config/BixiInitQuartzJob.java` | 启动时初始化任务 |
| `constants/JobTypeQuartzEnum.java` | 任务类型枚举 |

## 包路径

`com.lotus.bixi.quartz`
