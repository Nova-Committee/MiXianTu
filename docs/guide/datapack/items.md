---
title: 物品绑定、品质与经济
---

绑定表只匹配现有物品，不负责创建物品；`technique_binding` 是例外——它按**功法**匹配，手册的载体是堆上的 `mxt:technique` 组件（见[数据包格式](../../数据包格式.md)的该节，`carrier_item` 只决定本体替它生成哪件物品）。`weapon_binding`、`pill_binding` 的字段互不混用；武器拥有伤害、攻击速度、属性和攻击/使用/Tick 行为。

```json
{
  "items": ["minecraft:iron_sword", "#minecraft:swords"],
  "actions": [{"type": "mxt:grant_spirit_root", "spirit_root": "mxt:fire_root"}],
  "conditions": [
    {"type": "mxt:always"},
    {
      "condition": {"type": "mxt:realm", "realm": "example:foundation"},
      "description": "condition.example.foundation_required"
    }
  ]
}
```

绑定表的 `conditions` 中可以直接填写 `EntityCondition`，也可以填写带翻译键 `description` 的对象。所有条件都必须满足；带描述的条件会在物品 Tooltip 中显示绿色 `✓` 或红色 `✗`，描述文字保持普通样式。

灵根和体质的持有判定与增删都是数据包原语：`mxt:has_spirit_root` / `mxt:has_physique` 用于条件，`mxt:grant_spirit_root` / `mxt:remove_spirit_root` / `mxt:grant_physique` / `mxt:remove_physique` 用于行为。例如体质丹只对已有火灵根的玩家生效，并把先决灵根换成水灵根：

```json
{
  "items": "kubejs:root_switching_pill",
  "conditions": [
    {
      "condition": {"type": "mxt:has_spirit_root", "spirit_root": "mxt:fire_root"},
      "description": "condition.example.requires_fire_root"
    }
  ],
  "actions": [
    {"type": "mxt:remove_spirit_root", "spirit_root": "mxt:fire_root"},
    {"type": "mxt:grant_spirit_root", "spirit_root": "mxt:water_root"}
  ]
}
```

`quality` 定义品质内容；**品质顺序、默认档与升级路径由 `quality_chain` 决定**，物品通过绑定表的 `quality_chain` 引用一条链（解析出的档必须在链上，否则不能用）。品质自己的 `name` / `description` / `color` 可以省略（`name` / `description` 按 id 生成翻译键），三个修正对象里的 `description` 省略就不画那一行。`currency` 为物品定义货币价值，`unavailable_when` 是包含 `condition` 和 `reason` 的 ItemCondition 数组。
