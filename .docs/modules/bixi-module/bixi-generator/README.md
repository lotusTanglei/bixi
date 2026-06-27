# bixi-generator

代码生成器模块，支持多数据源、表结构解析、Velocity 模板引擎，快速生成 CRUD 代码。

## 模块职责

- 数据源管理：支持连接多种数据库，动态读取表结构信息
- 表结构解析：`GenTableColumn` 列信息、`GenGroup` 分组、`GenTemplate` 模板定义
- Velocity 模板引擎：`VelocityKit` 渲染代码模板
- 代码生成工具：`GenKit` 生成工具、`NamingCaseTool` 命名转换、`DictTool` 字典工具
- 表单配置：`VFormTypeEnum`、`VFormConfigConsts` 支持可视化表单生成
- 自动填充与公共字段：`AutoFillEnum`、`CommonColumnFiledEnum`、`BoolFillEnum`
- 生成风格：`GeneratorStyleEnum` 支持多种代码风格

## 关键文件

| 文件 | 说明 |
|------|------|
| `entity/GenTableColumn.java` | 表列信息实体 |
| `entity/GenTemplate.java` | 代码模板实体 |
| `entity/GenGroup.java` | 模板分组实体 |
| `util/VelocityKit.java` | Velocity 模板工具 |
| `util/GenKit.java` | 代码生成工具类 |
| `config/BixiGeneratorDefaultProperties.java` | 生成器默认配置 |

## 包路径

`com.lotus.bixi.generator`
