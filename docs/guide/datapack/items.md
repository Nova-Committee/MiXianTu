---
title: 物品绑定、品质与经济
---

**"这堆物品属于哪个概念"现在有两种写法。** 注册表那一边（`pill_binding`、`spirit_herb`、`artifact`、`technique_binding`）用 `items`（`ItemMatcher`：物品 id、`#标签`、通配、正则、混合数组）认领物品；**物品数据表**那一边（`item_aura`、`currency`、`item_binding`、`weapon_binding`、`tool_binding`、`blueprint_binding`、`default_quality`）没有 `items` 字段——键就是物品 id 或 `#标签`，见[数据表](../../数据包格式.md#数据表data-map)。数据表按条目查，所以它**看不了堆**（写不出"任何带某组件的物品"，也没有通配/正则）；换来的是标签键、多包合并语义、以及客户端不必持有注册表视图就能读。`technique_binding` 多一条可选路——它的 `items` 可以认领物品，但手册的身份仍以堆上的 `mxt:technique` 组件为先。`pill_binding` 只负责把物品绑到一份 `pill` 上（`pill` 必填）并给出这族物品的服用上限与冷却，载体是 `mxt:pill`；内置丹药的作用直接写在堆上的组件里。各表的字段互不混用；武器拥有原版属性修正和攻击/使用/Tick 行为，工具与图纸给出锻打方式与蓝图。逐件附加（品质、元素、丹药数据、功法阅读、锻打方式、图纸）走物品组件，规则见[数据包格式](../../数据包格式.md)的「物品组件」。

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

`quality` 定义品质内容；**品质的顺序、入口档与升级路径都在 `quality` 自己身上**——`next` 指向更高一档，目标档的 `upgrade_costs` / `upgrade_condition` 决定升级代价与条件，链名写在入口档的 `quality` 字段上并沿 `next` 传播。绑定表**不声明链**，物品读哪条链完全由它解析出的档位决定；**物品的档有三层**（组件 `mxt:quality` → **这一堆携带的定义**自己的 `quality` → 数据表 `default_quality`），九种定义（`technique` / `alchemy_furnace` / `alchemy_wall_material` / `spirit_root` / `physique` / `pill` / `formation` / `secret_realm` / `contract_type`）可以声明 `quality`，由后端登记的载体组件读到。品质自己的 `name` / `description` / `color` 可以省略（`name` / `description` 按 id 生成翻译键），三个修正对象里的 `description` 省略就不画那一行。`currency` 为物品定义货币价值，`unavailable_when` 是包含 `condition` 和 `reason` 的 ItemCondition 数组。

**按品质档位筛物品**有三个现成的口子，共用同一份要求（`quality` 成员列表，和 `min_quality` "至少到这一档"——按这一档所在的链比位置，**跨链答否**）：① 物品条件 `mxt:item_quality`（任何跑物品条件的地方都能用，例如货币的 `unavailable_when` 与绑定表的 `conditions`）；② 匹配器条目 `mxt:quality`（`items` 必填，凡是用 `ItemMatcher` 的地方都能用：`Cost` 的 `mxt:item`、`artifact` / `pill_binding` / `technique_binding` / `spirit_herb` 的 `items`、物品条件、灵植的种子、拾取器）——另有一个 `mxt:ingredient` 条目能把**一整个原版材料**（含 `mxt:quality`）写进任何 `ItemMatcher` 位置；③ 自定义材料 `mxt:quality`（`neoforge:ingredient_type`，**凡是用原版 `Ingredient` 的地方都能用**：所有配方、**锻造蓝图的 `input`**（sized ingredient）与**画符符方的 `paper`**，所以"材料至少某档"直接写这一条）。两个字段都不写会在加载期被拒。**另有一处不是筛物品**：丹方用 `furnace_quality` 要求**丹炉核心**的档（筛的是机器；炉壁与多方块结构不看品质），核心的档位还能作为变量 `furnace_rank` 进丹方公式，见[数据包格式](../../数据包格式.md#mxtalchemy)。写法与"哪些槽位今天还读不到品质"（丹炉输入仓、符笔颜料；符纸格本身只认 `mxt:talisman_paper` 标签，档位要求写在符方的 `paper` 字段上）见[数据包格式](../../数据包格式.md#quality)的「`quality`」一节。
