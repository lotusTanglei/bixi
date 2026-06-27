# bixi-monitor

Spring Boot Admin 监控中心，提供微服务运行状态的可视化监控。

## 模块职责

- `BixiMonitorApplication` 监控中心启动类，集成 Spring Boot Admin Server
- `NacosServiceInstanceConverter` Nacos 服务实例转换器，适配 Nacos 注册中心
- `SecuritySecureConfig` 安全配置，保护监控端点
- `CustomCsrfFilter` 自定义 CSRF 过滤器

## 关键文件

| 文件 | 说明 |
|------|------|
| `BixiMonitorApplication.java` | 监控中心启动类 |
| `converter/NacosServiceInstanceConverter.java` | Nacos 服务实例转换 |
| `config/SecuritySecureConfig.java` | 安全配置 |
| `config/CustomCsrfFilter.java` | CSRF 过滤器 |

## 包路径

`com.lotus.bixi.monitor`
