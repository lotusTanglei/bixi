# types TypeScript 类型定义

全局 TypeScript 类型声明目录，为项目提供类型安全支持。

## 文件清单

| 文件 | 说明 |
|------|------|
| `global.d.ts` | 全局类型声明（Window 扩展、环境变量等） |
| `views.d.ts` | 视图组件相关类型定义 |
| `layout.d.ts` | 布局组件相关类型定义 |
| `pinia.d.ts` | Pinia Store 类型扩展 |
| `axios.d.ts` | Axios 请求/响应类型扩展 |
| `mitt.d.ts` | Mitt 事件总线类型定义 |
| `func.ts` | 通用函数类型定义 |

## 使用说明

- `.d.ts` 文件为类型声明文件，TypeScript 编译器自动识别
- `func.ts` 为可导入的类型模块
- 在 `tsconfig.json` 中通过 `include` 配置自动加载
