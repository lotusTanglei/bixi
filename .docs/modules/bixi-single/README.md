# bixi-single — 单体部署聚合模块

单体模式部署聚合器，通过 Maven Profile `-Psingle` 激活，将所有业务模块打包为单个 Spring Boot 应用。

## 核心职责

- 聚合所有业务模块（upms、ai、workflow、generator、quartz、monitor）为单一可执行 JAR
- 无需 Spring Cloud Gateway 网关和 Nacos 注册中心
- 简化开发环境搭建和小规模部署场景

## 目录结构

```
bixi-single/
├── src/
│   └── main/
│       ├── java/        # 单体模式启动类
│       └── resources/   # 单体模式专用配置
├── pom.xml              # 聚合所有业务模块依赖
└── target/              # 构建产物
```

## 使用方式

```bash
# 构建单体模式
mvn clean package -Psingle

# 启动单体应用
java -jar bixi-single/target/bixi-single.jar
```

## 与微服务模式的区别

- 微服务模式（`-Pcloud`，默认）：各模块独立部署，依赖 Nacos + Gateway
- 单体模式（`-Psingle`）：所有模块合并为一个应用，直接启动即可
