# 根 README 全量更新设计

## 目标

重写根目录 `README.md`，使其与当前仓库的 Maven 配置、运行配置和模块边界一致，成为面向开发者的准确项目入口文档。本次只更新中文根 README，不修改 `README.en.md` 或 `.docs/modules/**/README.md`。

## 内容设计

README 按以下顺序组织：

1. 项目定位、核心能力和适用场景。
2. 微服务模式与单体模式对比，明确启动方式、模块边界和基础设施差异。
3. 后端、前端技术栈，版本以当前 POM/package.json 为准。
4. 中间件清单，区分数据库、缓存、消息、对象存储、网关、注册配置、任务调度、认证授权、监控和 DynamicTp。
5. 当前目录结构，反映实际模块名称。
6. 环境准备、数据库初始化、单体启动、微服务启动和前端启动。
7. 配置说明，包括环境变量、单体本地配置、微服务 Nacos 配置和 DynamicTp 配置策略。
8. 功能模块、文档入口、安全注意事项和常见问题。

## 事实约束

- 单体入口 `bixi-single/pom.xml` 当前聚合认证、UPMS、代码生成和 Quartz 模块；README 不宣称它默认聚合 AI、工作流和监控服务。
- 单体模式关闭 Nacos 配置与服务发现，不引入 Gateway；保留 Redis、MySQL、RabbitMQ、MinIO、Druid、MyBatis-Plus、OAuth2、Quartz、Undertow 和 DynamicTp 等实际依赖。
- DynamicTp 两种模式都启用。单体模式读取公共本地配置和环境变量，修改后重启；微服务模式额外支持 Nacos 动态刷新。
- 根 POM 的 `cloud` Profile 默认激活，单体构建使用 `-Psingle`；启动命令必须与该 Profile 事实一致。
- 不把仅存在于 BOM 或版本属性、但当前未实际引入的 Seata、ShardingSphere 等组件列为已使用中间件。
- 删除或替换仓库中不存在的 README 链接，避免文档入口失效。

## 验收标准

- README 中的命令、模块名、版本号和模式差异可在当前仓库文件中找到依据。
- 单体和微服务启动步骤均明确前置依赖及配置位置。
- DynamicTp 的“单体本地配置、微服务 Nacos 刷新”语义清晰且无歧义。
- README 内部目录锚点和仓库内相对链接有效。
- 本次变更只包含根 `README.md` 及本设计记录。
