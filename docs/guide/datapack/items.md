---
title: 物品绑定、品质与经济
---

绑定表只匹配现有物品，不负责创建物品；五张 binding 都是这样（`item_binding`、`weapon_binding`、`pill_binding`、`tool_binding`、`blueprint_binding`），`technique_binding` 多一条可选路——它的 `items` 可以认领物品，但手册的身份仍以堆上的 `mxt:technique` 组件为先。`pill_binding` 只负责把物品绑到一份 `pill` 上（`pill` 必填）并给出这族物品的服用上限与冷却，载体是 `mxt:pill`；内置丹药的作用直接写在堆上的组件里。各表的字段互不混用；武器拥有原版属性修正和攻击/使用/Tick 行为，工具与图纸给出锻打方式与蓝图。逐件附加（品质、元素、丹药数据、功法阅读、锻打方式、图纸）走物品组件，规则见[数据包格式](../../数据包格式.md)的「物品组件」。

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

`quality` 定义品质内容；**品质的顺序、入口档与升级路径都在 `quality` 自己身上**——`next` 指向更高一档，目标档的 `upgrade_costs` / `upgrade_condition` 决定升级代价与条件，链名写在入口档的 `quality` 字段上并沿 `next` 传播。绑定表**不声明链**，物品读哪条链完全由它解析出的档位决定。品质自己的 `name` / `description` / `color` 可以省略（`name` / `description` 按 id 生成翻译键），三个修正对象里的 `description` 省略就不画那一行。`currency` 为物品定义货币价值，`unavailable_when` 是包含 `condition` 和 `reason` 的 ItemCondition 数组。
