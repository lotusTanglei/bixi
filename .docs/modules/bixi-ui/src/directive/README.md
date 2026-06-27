# directive 自定义指令

Vue 自定义指令目录，提供权限控制和交互效果等声明式功能扩展。

## 指令清单

| 文件 | 说明 |
|------|------|
| `authDirective.ts` | `v-auth` 权限指令，根据用户权限控制元素显示/隐藏 |
| `customDirective.ts` | `v-waves` 波浪效果指令，为按钮等元素添加点击波纹动画 |
| `index.ts` | 统一注册入口，全局注册所有自定义指令 |

## 使用示例

```vue
<!-- 权限控制：仅拥有 sys_user_add 权限时显示 -->
<el-button v-auth="'sys_user_add'">新增用户</el-button>

<!-- 波浪效果 -->
<el-button v-waves>点击波纹</el-button>
```
