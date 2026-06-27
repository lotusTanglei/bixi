# components 公共组件

全局公共组件库，包含 27+ 个可复用组件，覆盖编辑器、上传、分页、字典标签等常用场景。

## 核心组件

- **表单类**：`Editor`（富文本）、`CodeEditor`（代码）、`form/`（表单组件）、`FormTable`、`Upload`
- **选择器类**：`IconSelector`、`ColorPicker`、`TreeSelect`、`ChinaArea`、`Crontab`
- **展示类**：`DictTag`（字典标签）、`SvgIcon`、`NameAvatar`、`Tip`、`StrengthMeter`
- **交互类**：`Pagination`（分页）、`Popup`、`PopoverInput`、`DelWrap`、`QueryTree`
- **通信类**：`Sse`（SSE 推送）、`Websocket`
- **布局类**：`RightToolbar`、`ShortcutCard`、`NoticeBar`
- **安全类**：`auth/`（权限控制）、`CheckToken`

## 注册方式

通过 `index.ts` 统一导出，支持按需引入。
