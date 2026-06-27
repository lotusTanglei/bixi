# bixi-common-oss

对象存储模块，支持 S3 兼容的云存储和本地文件存储两种模式。

## 模块职责

- S3 兼容对象存储：`OssAutoConfiguration` + `OssTemplate`，支持 MinIO、阿里云 OSS 等
- 本地文件存储：`LocalFileAutoConfiguration` + `LocalFileTemplate`，支持本地磁盘存储
- 统一文件操作接口（`FileTemplate`），屏蔽底层存储差异
- `OssEndpoint` 提供 REST 接口用于文件上传/下载/删除
- 属性配置：`OssProperties`（S3 配置）、`LocalFileProperties`（本地配置）、`FileProperties`（通用配置）

## 关键文件

| 文件 | 说明 |
|------|------|
| `core/FileTemplate.java` | 统一文件操作接口 |
| `oss/service/OssTemplate.java` | S3 兼容存储实现 |
| `local/LocalFileTemplate.java` | 本地文件存储实现 |
| `oss/http/OssEndpoint.java` | OSS REST 端点 |
| `FileAutoConfiguration.java` | 文件存储自动配置 |

## 包路径

`com.lotus.bixi.common.oss`
