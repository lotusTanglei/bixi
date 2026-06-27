# theme 主题样式

全局主题样式目录，基于 SCSS 变量体系，支持暗黑模式和 Element Plus 样式覆盖。

## 目录结构

| 文件/目录 | 说明 |
|-----------|------|
| `index.scss` | 主题入口文件，汇总导入所有样式 |
| `app.scss` | 应用全局样式 |
| `element.scss` | Element Plus 组件样式覆盖 |
| `dark.scss` | 暗黑模式样式 |
| `login.scss` | 登录页样式 |
| `loading.scss` | 加载动画样式 |
| `other.scss` | 其他通用样式 |
| `waves.scss` | 波浪效果样式 |
| `tableTool.scss` | 表格工具栏样式 |
| `iconSelector.scss` | 图标选择器样式 |
| `tailwind.css` | Tailwind CSS 配置 |
| `common/` | 公共样式片段 |
| `mixins/` | SCSS Mixin 混入 |
| `media/` | 响应式媒体查询 |

## 主题切换

通过 `themeConfig` Store 控制主题变量，支持：
- 主题色切换
- 暗黑/明亮模式
- 布局样式切换
