---
title: Ability、Cost 与 Condition
---

Ability 的数值字段均可使用 NumberProvider/表达式。技能可以配置右键使用、攻击时、Tick 和其他 action，并通过 `costs` 声明资源或物品消耗。

```json
{
  "type": "mxt:active",
  "costs": [{"type": "mxt:resource", "resource": "mxt:spirit_power", "amount": 10}],
  "actions": [{"type": "mxt:damage", "amount": "8 + level"}]
}
```

技能行为由服务端处理，客户端 Hotbar 只发送使用/取消请求。
