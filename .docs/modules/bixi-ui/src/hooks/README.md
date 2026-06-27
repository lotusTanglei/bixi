# hooks 组合式函数

Vue 3 组合式函数（Composables）目录，封装可复用的业务逻辑。

## 函数清单

| 文件 | 说明 |
|------|------|
| `dict.ts` | `useDict` — 字典数据加载与缓存，自动获取字典项列表 |
| `table.ts` | `useTable` — 表格通用逻辑，封装分页查询、搜索、重置等操作 |
| `message.ts` | `useMessage` — 消息提示封装，统一 ElMessage/ElMessageBox 调用 |

## 使用方式

```typescript
import { useDict } from '@/hooks/dict'
import { useTable } from '@/hooks/table'
import { useMessage } from '@/hooks/message'

// 加载字典
const { dict } = useDict('sys_normal_disable')

// 表格逻辑
const { tableData, pagination, handleQuery } = useTable(fetchApi)
```
