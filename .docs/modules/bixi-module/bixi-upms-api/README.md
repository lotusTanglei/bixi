# bixi-upms-api

UPMS（统一权限管理系统）API 层，定义 Feign 远程调用接口、数据传输对象和实体类。

## 模块职责

- Feign 远程接口：`RemoteUserService`、`RemoteLogService`、`RemoteTokenService`、`RemoteClientDetailsService`、`RemoteFileService`、`RemoteDictService`、`RemoteParamService`
- DTO 数据传输对象：`UserDTO`、`UserInfo`、`SysLogDTO`、`NoticeMessageDTO`
- VO 视图对象：`UserVO`、`RoleVO`、`TokenVo`、`PreLogVO`、Excel 导出 VO 等
- Entity 实体类：`SysRole`、`SysDict`、`SysDictItem`、`SysFile`、`SysPublicParam`、`SysUserRole`、`SysRoleMenu`
- 工具类：`DictResolver`（字典解析）、`ParamResolver`（参数解析）

## 关键文件

| 文件 | 说明 |
|------|------|
| `feign/RemoteUserService.java` | 用户远程调用接口 |
| `feign/RemoteLogService.java` | 日志远程调用接口 |
| `feign/RemoteDictService.java` | 字典远程调用接口 |
| `dto/UserDTO.java` | 用户数据传输对象 |
| `dto/UserInfo.java` | 用户信息聚合对象 |
| `entity/SysRole.java` | 角色实体 |

## 包路径

`com.lotus.bixi.upms.api`
