# bixi-generator

代码生成业务模块，读取数据源表结构，维护生成元数据和模板，并生成后端、API、前端及菜单 SQL 文件。模板使用 Velocity 渲染，模块同时提供单表和父子表模板。

## 模块职责

- 数据源配置和数据库文档：`GenDatasourceConfigController`、`GenDatasourceConfigService`
- 表结构导入、同步、字段配置和 DDL 查看：`GenTableController`、`GenTableService`、`GenTableColumnService`
- 字段类型和生成分组管理：`GenFieldTypeController`、`GenGroupController`
- 模板和模板组管理：`GenTemplateController`、`GenTemplateGroupController`
- 代码预览、写入和 ZIP 下载：`GeneratorController`、`GeneratorServiceImpl`
- Velocity 模板、命名转换、字典和表单配置：`VelocityKit`、`NamingCaseTool`、`DictTool`、`VFormConfigConsts`
- 内置 `default-v1` 模板目录，包含单表和父子表生成文件
- 可选固定来源在线模板更新：校验 revision、manifest 和 SHA-256 后安装模板组

## 关键文件

| 文件 | 作用 |
|---|---|
| `BixiGeneratorApplication.java` | cloud 独立服务入口；single 由 `bixi-single` 聚合 |
| `controller/GenDatasourceConfigController.java` | 数据源配置和数据库文档接口 |
| `controller/GenTableController.java` | 表导入、同步、配置、字段和 DDL 接口 |
| `controller/GenTemplateController.java` | 模板 CRUD、导出和在线更新接口 |
| `controller/GeneratorController.java` | 代码预览、生成和下载接口 |
| `entity/GenTable.java`、`entity/GenTableColumn.java` | 表及字段生成元数据 |
| `entity/GenTemplate.java`、`entity/GenGroup.java`、`entity/GenTemplateGroup.java` | 模板和分组数据 |
| `service/impl/GeneratorServiceImpl.java` | 模板渲染和输出协调 |
| `service/output/GeneratorOutputPolicy.java` | 生成目录和允许写入根目录校验 |
| `template/BuiltInTemplateCatalog.java` | 加载并校验内置模板目录 |
| `template/remote/TemplateUpdatePackageLoader.java` | 下载、校验和解析固定来源模板包 |
| `config/BixiGeneratorDefaultProperties.java` | 生成器和模板来源配置 |

## 运行与输出

生成器独立服务使用 `BixiGeneratorApplication`，cloud 默认监听 `5002`，Gateway 路由前缀为 `/gen`。single 包含同一实现，`generator.enabled` 默认值为 `true`，可通过 `GENERATOR_ENABLED=false` 关闭生成器控制器和服务。

生成写入由 `generator.project-root` 和 `generator.allowed-output-roots` 约束；Compose 将项目输出目录挂载到 `/data/generator-output`，对应环境变量为 `GENERATOR_PROJECT_ROOT` 和 `GENERATOR_OUTPUT_PATH`。生成文件可能写入 backend、API、frontend 或 `bixi-project-documents/sql` 目录，使用前应确认挂载路径和写入权限。

## 固定来源模板更新

在线更新默认关闭（`generator.auto-check-version=false`）。启用前必须配置固定 HTTPS 来源、完整 Git revision、允许主机和 manifest SHA-256；服务端会限制文件数量、manifest 大小和单文件大小，并在一个事务中安装模板组。更新接口需要 `codegen_template_edit` 权限并记录操作日志。配置示例、迁移前置条件和失败恢复见 [固定来源模板更新](../../../generator/FIXED-SOURCE-UPDATES.md)。

## 包路径

`com.lotus.bixi.generator`
