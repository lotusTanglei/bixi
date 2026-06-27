# assets 静态资源

前端静态资源目录，存放图片、图标和样式变量等需要经过构建工具处理的资源。

## 目录结构

| 文件/目录 | 说明 |
|-----------|------|
| `bixi-app.png` | 应用主图 |
| `logo.png` | 应用 Logo |
| `logo-mini.svg` | 迷你版 Logo（SVG） |
| `login-bg.svg` | 登录页背景图 |
| `lockScreen.png` | 锁屏页背景图 |
| `icons/` | 图标资源目录 |
| `login/` | 登录页相关图片 |
| `styles/` | 全局样式变量 |

## 使用方式

- 通过模块化导入引用：`import logo from '@/assets/logo.png'`
- 构建时会被 Vite 处理（哈希命名、压缩等）
