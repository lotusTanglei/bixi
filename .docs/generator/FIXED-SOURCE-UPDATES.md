# 固定来源模板更新

在线模板更新只用于安装经过固定 revision 和摘要校验的模板包，不是动态模板市场。功能默认关闭；未配置完整可信来源时，服务端拒绝检查和安装。

## 来源目录

配置 `online-url=https://templates.example/bixi`、revision 为完整 40 位小写 Git 提交哈希时，服务端只访问：

```text
https://templates.example/bixi/<revision>/<manifest-path>
https://templates.example/bixi/<revision>/<manifest sourcePath>
```

来源必须使用 HTTPS 标准端口，不能包含用户信息、查询参数或 fragment。主机名必须与 `online-allowed-hosts` 中的一项精确匹配。重定向、非 200 响应、目录穿越、绝对路径、重复条目、未知 manifest 字段、无效 UTF-8、大小或 SHA-256 不匹配都会使整个候选包失败，数据库不会写入部分模板。

## 制作 manifest

[template-update-manifest.json.template](examples/template-update-manifest.json.template) 是待替换模板，不包含可直接启用的伪造 revision 或摘要。每个文件的 `sha256` 是原始文件字节的小写 SHA-256，`size` 是同一文件的字节数。可使用：

```bash
git rev-parse HEAD
shasum -a 256 templates/entity.java.vm
wc -c < templates/entity.java.vm
```

manifest 本身定稿后再计算摘要：

```bash
shasum -a 256 manifest.json
```

发布目录和 manifest 必须不可变。相同 revision 下替换任何文件或 manifest 都会使已部署配置的摘要校验失败。

## 启用配置

仅在核对来源目录、完整 revision、文件摘要和 manifest 摘要后设置：

```yaml
generator:
  auto-check-version: true
  online-url: https://templates.example/bixi
  online-allowed-hosts:
    - templates.example
  online-revision: ${TEMPLATE_SOURCE_REVISION}
  online-manifest-path: manifest.json
  online-manifest-sha256: ${TEMPLATE_MANIFEST_SHA256}
  online-max-files: 64
  online-max-manifest-bytes: 65536
  online-max-file-bytes: 1048576
```

不要在仓库中写入真实生产来源或摘要。`online-max-*` 只能调低，不能超过 64 个文件、64 KiB manifest 和单文件 1 MiB 的服务端硬上限。

更新是 `POST /template/online` 写操作，需要 `codegen_template_edit`，并记录“在线更新模板”审计日志。返回值包含是否实际安装、固定 revision、manifest 摘要、最终组名和模板数量。前端只展示 revision 和摘要的短前缀；完整值保存在返回结果与模板组描述中。

## 失败与恢复

服务端先下载并校验完整候选包，再在一个事务中写入模板组、模板和关联。任一下载、解析、校验或数据库写入失败都会回滚，现有模板组保持不变。并发安装同一 revision 由 `uk_gen_group_active_name` 收敛为一个组，竞争请求返回“版本已存在”。

已有数据库先暂停模板组写入，备份 `gen_group`、`gen_template` 和 `gen_template_group`，再执行 `bixi-project-documents/sql/migrations/20260924_generator_template_group_uniqueness.sql`。迁移检测到重复未删除组名或同名不兼容列/索引时会停止，不会自动挑选或删除数据。完成后再启用固定来源更新。

回退应用版本时保留新增生成列和唯一索引；它们只约束未删除组名，不改变模板内容。若一次更新失败，修复来源或配置后重试同一 POST，不要手工补写部分模板行。
