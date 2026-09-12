---
title: Ability、Cost 与 Condition
---

Ability 的数值字段均可使用 NumberProvider/表达式。技能类型写在嵌套的 `ability` 对象里，行为写在 `entity_action` / `bi_entity_action` 中，并通过 `costs` 声明资源或物品消耗。

```json
{
  "ability": {
    "type": "mxt:active"
  },
  "costs": [{"type": "mxt:resource", "resource": "mxt:spirit_power", "amount": 10}],
  "entity_action": {"type": "mxt:damage", "amount": "8 + level"}
}
```

技能行为由服务端处理，客户端 Hotbar 只发送使用/取消请求。
