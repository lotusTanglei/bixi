# i18n 国际化配置

基于 vue-i18n 的国际化配置目录，支持中文和英文两种语言。

## 目录结构

| 文件/目录 | 说明 |
|-----------|------|
| `index.ts` | i18n 实例初始化与配置 |
| `lang/` | 语言包目录，包含中文（zh-cn）和英文（en）翻译文件 |
| `pages/` | 页面级翻译文件，按页面模块组织 |

## 使用方式

```typescript
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 使用翻译
t('common.confirm')  // 确认 / Confirm
```

## 支持语言

- 🇨🇳 中文（zh-cn）— 默认语言
- 🇺🇸 英文（en）
