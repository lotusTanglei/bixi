# utils 工具函数集

前端通用工具函数目录，封装 HTTP 请求、本地存储、表单校验等常用功能。

## 文件清单

| 文件 | 说明 |
|------|------|
| `request.ts` | Axios 请求封装，统一拦截器、Token 注入、错误处理 |
| `storage.ts` | 本地存储工具（localStorage/sessionStorage 封装） |
| `validate.ts` | 表单校验规则（手机号、邮箱、身份证等） |
| `toolsValidate.ts` | 扩展校验工具函数 |
| `other.ts` | 其他通用工具（深拷贝、防抖、节流等） |
| `authFunction.ts` | 权限判断函数 |
| `commonFunction.ts` | 公共业务函数 |
| `formatTime.ts` | 时间格式化工具 |
| `errorCode.ts` | HTTP 错误码映射 |
| `loading.ts` | 全局 Loading 控制 |
| `mitt.ts` | Mitt 事件总线实例 |
| `theme.ts` | 主题切换工具 |
| `setIconfont.ts` | 图标字体加载 |
| `getStyleSheets.ts` | 样式表获取工具 |
| `arrayOperation.ts` | 数组操作工具 |
| `chinaArea.ts` | 中国行政区域数据 |
| `wartermark.ts` | 水印工具 |

## 核心模块

- **request.ts**：基于 Axios 二次封装，支持 Token 自动注入、响应拦截、错误码处理
- **storage.ts**：统一的本地存储 API，支持过期时间设置
- **validate.ts**：常用表单校验规则集合
