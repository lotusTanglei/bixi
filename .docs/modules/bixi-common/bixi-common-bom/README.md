# bixi-common-bom

Maven BOM（Bill of Materials）模块，统一管理项目所有依赖的版本号。

## 模块职责

- 集中声明所有 bixi 内部模块的版本号，确保各模块版本一致
- 管理第三方依赖版本，避免版本冲突
- 其他模块通过 `<dependencyManagement>` 引入本 BOM，无需重复指定版本

## 关键文件

| 文件 | 说明 |
|------|------|
| `pom.xml` | BOM 定义文件，包含所有依赖版本声明 |

## 使用方式

在 `<dependencyManagement>` 中以 `pom` + `import` 方式引入本 BOM 即可。
