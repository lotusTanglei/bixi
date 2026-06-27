# bixi-common-swagger

SpringDoc OpenAPI 自动配置模块，为各微服务统一生成 API 文档。

## 模块职责

- `@EnableBixiDoc` 注解，一键启用 OpenAPI 文档
- `OpenAPIDefinition` + `OpenAPIDefinitionImportSelector` 实现自动导入配置
- `OpenAPIMetadataConfiguration` 配置 API 元数据（标题、版本、描述等）
- `SwaggerProperties` 属性配置，支持自定义文档信息

## 关键文件

| 文件 | 说明 |
|------|------|
| `annotation/EnableBixiDoc.java` | 启用 API 文档注解 |
| `config/OpenAPIDefinition.java` | OpenAPI 定义配置 |
| `config/OpenAPIMetadataConfiguration.java` | API 元数据配置 |
| `support/SwaggerProperties.java` | 文档属性配置 |

## 包路径

`com.lotus.bixi.common.swagger`
