# bixi-single — 单体部署聚合模块

单体模式部署聚合器，通过 Maven Profile `-Psingle` 激活，将 Auth、UPMS、Generator 和 Quartz 打包为单个 Spring Boot 应用。同一 Workflow 业务模块也包含在制品中，默认关闭，通过 `workflow.enabled=true` 按需装配。`bixi-ai-biz` 业务实现同样包含在制品中，由默认值为 `false` 的 `AI_ENABLED` 控制；设置为 `true` 后按需装配。cloud 中 AI 作为独立服务运行，Monitor 始终独立运行。工作流基础审批已通过阶段一双模验收，可靠协作和恢复仍在实施，见 [交付清单](../../workflow/PROGRESS.md)。

## 核心职责

- 聚合 Auth、UPMS、Generator 和 Quartz，并提供可选的 `bixi-ai-biz` 与 Workflow 业务实现，为单一可执行 JAR 服务
- 无需 Spring Cloud Gateway 网关和 Nacos 注册中心
- 简化开发环境搭建和小规模部署场景

## 目录结构

```
bixi-single/
├── src/
│   └── main/
│       ├── java/        # 单体模式启动类
│       └── resources/   # 单体模式专用配置
├── pom.xml              # 声明当前单体聚合模块依赖
└── target/              # 构建产物
```

## 使用方式

```bash
# 构建单体模式
mvn -Psingle -pl bixi-single -am clean package

# 启动单体应用
java -jar bixi-single/target/bixi-single.jar
```

## 与微服务模式的区别

- 微服务模式（`-Pcloud`，默认）：各模块独立部署，依赖 Nacos + Gateway
- 单体模式（`-Psingle`）：Auth、UPMS、Generator、Quartz 合并为一个应用；AI 通过 `AI_ENABLED=true` 显式启用后直接使用，默认关闭
