# bixi-common-oss

对象存储模块，统一封装 S3 兼容存储和本地磁盘存储。模块同时注册两种自动配置，最终由条件属性和 Bean 优先级决定实际使用的 `FileTemplate`。

## 模块职责

- S3 兼容存储：`OssAutoConfiguration` + `OssTemplate`，使用 AWS S3 客户端，适配 MinIO 和其他 S3 服务。
- 本地文件存储：`LocalFileAutoConfiguration` + `LocalFileTemplate`，可将文件写入本地目录。
- 统一文件接口：`FileTemplate` 屏蔽上传、下载、删除和预签名 URL 等底层差异。
- 可选 REST 端点：`file.oss.info=true` 时由 `OssEndpoint` 暴露对象存储管理接口。

## 条件配置

- `file.local.enable` 默认按开启处理；本地路径属性位于 `local.*`。
- `file.oss.enable=true` 才会创建 S3 `OssTemplate`，其连接属性位于 `file.oss.*` 下（`endpoint`、`access-key`、`secret-key`、`region` 等）。
- S3 Bean 标记为 `@Primary`，因此同时启用本地和 S3 时，注入 `FileTemplate` 默认使用 S3 实现。
- 标准 Compose 不提供 MinIO 容器。single 的开发配置默认开启 S3 适配，使用文件能力前需要通过 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY` 和 `MINIO_SECRET_KEY` 连接外部服务；也可以关闭 S3 并使用本地存储。

## 关键文件

| 文件 | 说明 |
|------|------|
| `core/FileTemplate.java` | 统一文件存储接口 |
| `core/FileProperties.java` | `file.*` 通用配置入口 |
| `oss/service/OssTemplate.java` | S3 兼容存储实现 |
| `local/LocalFileTemplate.java` | 本地磁盘存储实现 |
| `oss/http/OssEndpoint.java` | 可选对象存储 REST 端点 |
| `FileAutoConfiguration.java` | 导入本地和 S3 两种自动配置 |

## 包路径

`com.lotus.bixi.common.oss`
