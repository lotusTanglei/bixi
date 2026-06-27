# bixi-ui — Vue 3 前端应用

基于 Vue 3 + TypeScript + Vite 构建的前端应用，使用 Element Plus 组件库和 Tailwind CSS 样式框架。

## 核心目录结构

```
bixi-ui/src/
├── api/           # 后端 API 调用封装（按业务域分目录：admin、ai、gen、job、workflow）
├── views/         # 业务页面组件（用户管理、AI 对话、工作流、代码生成等）
├── components/    # 公共组件（富文本编辑器、图标选择器、上传、字典标签、分页等）
├── stores/        # Pinia 状态管理（用户信息、AI 会话、字典缓存、路由、主题配置）
├── router/        # 路由配置，支持前端静态路由和后端动态路由两种模式
├── hooks/         # 组合式函数（useDict、useTable 等可复用逻辑）
├── utils/         # 工具函数（request.ts 请求封装、auth.ts 令牌管理等）
├── directive/     # Vue 自定义指令（权限指令 v-hasPermi、v-hasRole）
├── i18n/          # 国际化配置（中文、英文语言包）
├── layout/        # 布局组件（经典布局、横向布局、分栏布局）
├── theme/         # 主题配置（主题色、暗黑模式、CSS 变量）
├── assets/        # 静态资源（图片、图标、样式）
└── types/         # TypeScript 类型定义
```

## 技术栈

- **框架**：Vue 3 + Composition API（`<script setup>`）
- **构建**：Vite
- **UI**：Element Plus + Tailwind CSS + SCSS
- **状态管理**：Pinia
- **路由**：Vue Router 4
- **国际化**：vue-i18n
- **HTTP**：Axios（封装于 `src/utils/request.ts`）
