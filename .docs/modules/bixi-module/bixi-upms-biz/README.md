# bixi-upms-biz

UPMS（统一权限管理系统）业务模块，实现用户、角色、菜单、部门、岗位、字典、参数、日志、文件、通知等核心管理功能。

## 模块职责

- 用户/角色/菜单/部门/岗位管理（完整 CRUD + 权限分配）
- 字典管理（`SysDictController`）、参数管理（`SysPublicParamController`）
- 日志管理、文件管理、通知管理（支持 MQ 消费 `NoticeConsumer`）
- OAuth2 客户端管理、注册与手机登录

## 关键文件

| 文件 | 说明 |
|------|------|
| `BixiUPMSApplication.java` | UPMS 模块启动类 |
| `controller/SysUserController.java` | 用户管理控制器 |
| `controller/SysRoleController.java` | 角色管理控制器 |
| `controller/SysMenuController.java` | 菜单管理控制器 |
| `controller/SysDeptController.java` | 部门管理控制器 |
| `mq/NoticeConsumer.java` | 通知消息消费者 |

## 包路径

`com.lotus.bixi.upms`
