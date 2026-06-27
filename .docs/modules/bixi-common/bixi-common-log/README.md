# bixi-common-log

操作日志模块，基于 `@SysLog` 注解 + AOP 切面 + 异步事件监听实现操作日志自动采集。

## 模块职责

- `@SysLog` 注解，标记需要记录操作日志的方法
- `SysLogAspect` AOP 切面，拦截注解方法并采集日志信息
- 异步事件机制：`SysLogEvent` → `SysLogEventSource` → `SysLogListener`，解耦日志写入
- 日志配置属性（`BixiLogProperties`）
- 应用日志初始化（`ApplicationLoggerInitializer`）
- 日志类型枚举（`LogTypeEnum`）与工具类（`SysLogUtils`）

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/SysLog.java` | 操作日志注解 |
| `aspect/SysLogAspect.java` | AOP 日志切面 |
| `event/SysLogEvent.java` | 日志事件对象 |
| `event/SysLogListener.java` | 异步日志监听器 |
| `LogAutoConfiguration.java` | 日志自动配置 |

## 包路径

`com.lotus.bixi.common.log`
