# stores Pinia 状态管理

基于 Pinia 的全局状态管理目录，包含 11 个 Store，管理应用核心状态。

## Store 清单

| 文件 | 说明 |
|------|------|
| `index.ts` | Pinia 实例创建 |
| `userInfo.ts` | 用户信息 Store（登录状态、用户数据、权限） |
| `routesList.ts` | 路由列表 Store（动态路由数据） |
| `tagsViewRoutes.ts` | 标签页 Store（已打开的页面标签） |
| `themeConfig.ts` | 主题配置 Store（布局、颜色、暗黑模式等） |
| `keepAliveNames.ts` | 缓存组件 Store（keep-alive 缓存列表） |
| `requestOldRoutes.ts` | 旧路由请求 Store |
| `dict.ts` | 字典缓存 Store |
| `ai.ts` | AI 模块状态 Store |
| `msg.ts` | 消息状态 Store |
| `noticeCenter.ts` | 通知中心 Store |

## 使用方式

```typescript
import { useUserInfo } from '@/stores/userInfo'

const userStore = useUserInfo()
userStore.userInfos  // 获取用户信息
```
