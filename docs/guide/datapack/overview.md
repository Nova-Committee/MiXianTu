---
title: 数据包开发总览
sidebar_position: 1
---

## 文件路径

```text
data/<namespace>/mxt/<registry>/<id>.json
```

例如 `data/example/mxt/ability/fireball.json` 的定义 ID 是 `example:fireball`。

数据包加载使用 NeoForge 原生可写注册表，服务器加载后同步到客户端。数据包对象在加载后视为不可变对象；不要在运行时修改 Codec 返回的集合。

## 引用规则

- 固有注册表的单个引用使用 `Holder` Codec。
- 可选引用使用 `optionalFieldOf`。
- 列表和 Map 使用容错的 Holder/集合 Codec。
- 物品匹配使用 `ItemMatcher`，支持 ID、标签、通配符、正则和混合数组。
- 原版标签是唯一的标签系统；不要在 JSON 里重复定义 `tags` 字段。

## 禁用标签

每个动态注册表都支持：

```text
data/<namespace>/tags/mxt/<registry>/disabled.json
```

被列入 `mxt:disabled` 的条目不会参与运行时查询。标签值顺序不作为玩法顺序；品质顺序由品质读取接口根据原版标签顺序处理。

## 数值字段

数值可以写成常量、表达式字符串或 NumberProvider 对象：

```json
{
  "damage": 8.0,
  "speed": "2 + level * 0.1",
  "amount": {"type": "minecraft:constant", "value": 10}
}
```

表达式使用 exp4j。变量来自 `FormulaContext`，`params` 可以覆盖或追加变量。加载阶段的 NaN/Infinity 会使数据包失败；运行阶段会记录单行警告并按 0 处理。

## 行为与条件

行为统一称为 `action`，按目标分为 entity、item、block、bi-entity 等。需要多个步骤时使用 sequence/choice/if_else 等元行为。`condition` 用于限制技能、绑定物品、修炼、境界和配方。

## 注册表索引

| 分类 | 注册表 |
| --- | --- |
| 资源与修炼 | `resource`、`element`、`realm_stage`、`spirit_root`、`physique`、`cultivation_technique`、`cultivate_action` |
| 技能与规则 | `ability`、`curse`、`formation`、`tribulation`、`badge` |
| 灵气与世界 | `aura_zone`、`block_aura`、`item_aura`、`realm_instance` |
| 物品与品质 | `item_binding`、`weapon_binding`、`pill_binding`、`technique_binding`、`item_archetype`、`item_quality` |
| 经济与内容 | `currency`、`spirit_herb`、`forging_method`、`forging_blueprint`、`creature_profile`、`contract_type`、`sect`、`title` |

## 模块页面

- [resource：资源与资源条](resource)
- [修炼、境界与灵根](cultivation)
- [灵气环境与灵气物品](aura)
- [Ability、Cost 与 Condition](ability)
- [物品绑定、品质与经济](items)
- [阵法、锻造与炼丹](formation)
- [其他注册表](other)
- [数据包示例](examples)
