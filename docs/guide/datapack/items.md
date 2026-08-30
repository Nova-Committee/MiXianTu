---
title: 物品绑定、品质与经济
---

绑定表只匹配现有物品，不负责创建物品。`weapon_binding`、`pill_binding` 和 `technique_binding` 的字段互不混用；武器拥有伤害、攻击速度、属性和攻击/使用/Tick 行为。

```json
{
  "items": ["minecraft:iron_sword", "#minecraft:swords"],
  "actions": [{"type": "mxt:grant_spirit_root", "root": "mxt:fire"}],
  "conditions": [
    {"type": "mxt:always_true"},
    {
      "condition": {"type": "mxt:realm", "realm": "example:foundation"},
      "description": "condition.example.foundation_required"
    }
  ]
}
```

绑定表的 `conditions` 中可以直接填写 `EntityCondition`，也可以填写带翻译键 `description` 的对象。所有条件都必须满足；带描述的条件会在物品 Tooltip 中显示绿色 `✓` 或红色 `✗`，描述文字保持普通样式。

`item_quality` 定义品质内容，品质组使用原版标签组织顺序，物品通过 `quality_group` 引用品质序列。`currency` 为物品定义货币价值，`unavailable_when` 是包含 `condition` 和 `reason` 的 ItemCondition 数组。
