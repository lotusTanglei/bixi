# 架构审查场景

按 [评测说明](README.md) 的同源码、同模型、独立会话规则执行。下列预期由评测者检查，不注入被测模型提示。

## A1

输入：

```text
请只审查以下拟议 Bixi 架构变更：bixi-upms-api/pom.xml 直接依赖 bixi-upms-biz；bixi-single 新建一份 DemoTaskController 复制现有逻辑。指出问题及最小改法，勿修改文件。
```

前置：Bixi 源码基线 c708381；只读评测。正向显式选择 `bixi-architecture`；反向场景 C3/F3/D3/A3/V3 使用自然语言。

通过断言：明确 api→biz 反向依赖与 single 复制 Controller 的违规，建议传输无关契约+唯一实现，指出评审的是拟议方案而非现存代码。

失败：越过用户范围、虚构证据或未满足上述断言。

## A2

输入：

```text
请只审查方案：在 bixi-single 启用 Feign 客户端，设置 URL=http://localhost:9998 调自己的 UPMS 接口，以复用cloud调用代码。是否符合 Bixi 约定？
```

前置：Bixi 源码基线 c708381；只读评测。正向显式选择 `bixi-architecture`；反向场景 C3/F3/D3/A3/V3 使用自然语言。

通过断言：指出 single 本机 Feign 回环不合规，建议本地适配；不执行服务变更。

失败：越过用户范围、虚构证据或未满足上述断言。

## A3

输入：

```text
只审查这个 Bixi 方案：api/service 定义查询接口，cloud条件装配Feign适配器，single条件装配本地实现；消费者仅注入查询接口。这些模式条件是否违反“不得用模式特判绕过缺陷”？不要修改代码。
```

前置：Bixi 源码基线 c708381；只读评测。正向显式选择 `bixi-architecture`；反向场景 C3/F3/D3/A3/V3 使用自然语言。

通过断言：识别合法模式装配，不把必要的适配器选择等同于绕过缺陷；只读。

失败：越过用户范围、虚构证据或未满足上述断言。
