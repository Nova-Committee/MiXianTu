---
title: 数据包开发总览
sidebar_position: 1
---

## 文件路径

```text
data/<namespace>/mxt/<registry>/<path>.json
```

例如 `data/example/mxt/ability/fireball.json` 的定义 ID 是 `example:fireball`。

数据包加载使用 NeoForge 原生可写注册表，服务器加载后同步到客户端。数据包对象在加载后视为不可变对象；不要在运行时修改 Codec 返回的集合。

## 引用规则

- 固有注册表的单个引用使用 `Holder` Codec。
- 可选引用使用 `optionalFieldOf`。
- 列表和 Map 使用容错的 Holder/集合 Codec。
- 物品匹配使用 `ItemMatcher`，支持 ID、标签、通配符、正则和混合数组。
- 原版标签是唯一的标签系统；不要在 JSON 里重复定义 `tags` 字段。

## 停用一条定义

把 NeoForge 的资源条件写在定义文件里即可：条件不成立的条目**不会进注册表**，也就不会被任何服务读到。数据包注册表在加载期由 `ConditionalOps` 解码，所以所有表用的是同一套条件：

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:never" }
  ]
}
```

可用的条件有 `never` / `always`、`mod_loaded`（`modid`）、`registered`（`registry`、`value`）、`and` / `or`（`values`）、`not`（`value`）与 `feature_flags_enabled`（`flags`）；按标签判断的 `tag_empty` **不能**用在这里（这一层标签还没绑定，会直接抛异常）。条件成立时 `neoforge:conditions` 在解码前被剥掉，正常字段照常读。

代价是"不存在"就是不存在：指向它的 Holder 引用会一起解码失败，所以没有"留着这条定义但让它不生效"的中间状态。完整说明见 [`docs/数据包格式.md`](../../数据包格式) 的「文件位置」。

> 灵根与体质另有一个**开关**（`spirit_identity` 附件里的 `disabled_spirit_roots` / `disabled_physiques`）：关闭是"仍然持有但不生效"。操作用脚本的 `MxtSpiritRoots.setEnabled` / `MxtPhysiques.setEnabled`，或管理员命令 `/mxt spirit_root enable|disable`、`/mxt physique enable|disable`；本模组不为它提供玩家界面。品质的顺序由 `quality` 自己声明（每一档的 `quality` 与 `next`），不依赖标签顺序。

## 数值字段

数值可以写成常量、表达式字符串或 NumberProvider 对象：

```json
{
  "damage": 8.0,
  "speed": "2 + level * 0.1",
  "amount": {"type": "mxt:constant", "value": 10}
}
```

表达式使用 exp4j。变量由内置变量表从 `FormulaContext` 携带的对象（施法者、目标、资源、随机源）中按需读取，`params` 可以覆盖或追加变量。加载阶段可判定的公式问题（空表达式、语法错误、`params` 非法等）是解码错误：加载器收集全部失败条目后一并列出并使加载失败。上下文无法提供的变量名只能在求值时发现——开发环境打印完整 ERROR 日志，生产环境每个不同消息打印一行 WARN，两者都继续按 0 处理。

## 行为与条件

行为统一称为 `action`，按目标分为 entity、item、block、bi-entity 等。需要多个步骤时使用 sequence/choice/if_else 等元行为。`condition` 用于限制技能、绑定物品、修炼、境界和配方。

## 注册表索引

| 分类 | 注册表 |
| --- | --- |
| 资源与修炼 | `resource`、`aura`、`element`、`realm_stage`、`spirit_root`、`physique`、`technique`、`skill_stage`、`cultivation` |
| 技能与规则 | `ability`、`curse`、`formation`、`tribulation`、`trigger`、`talisman` |
| 灵气与世界 | `aura_zone`、`block_aura`、`item_aura`、`secret_realm` |
| 物品与品质 | `item_binding`、`weapon_binding`、`pill`、`pill_binding`、`technique_binding`、`tool_binding`、`blueprint_binding`、`artifact`、`quality` |
| 炼丹与灵植 | `medicinal_property`、`spirit_herb`、`alchemy_furnace`、`alchemy_wall_material`。丹方是原版配方 `mxt:alchemy`，不是这张表里的注册表。 |
| 经济与内容 | `currency`、`forging_method`、`forging_blueprint`、`creature_profile`、`contract_type` |

## 模块页面

- [resource：资源与资源条](resource)
- [修炼、境界与灵根](cultivation)
- [灵气环境与灵气物品](aura)
- [Ability、Cost 与 Condition](ability)
- [物品绑定、品质与经济](items)
- [阵法、锻造与炼丹](formation)
- [其他注册表](other)
- [数据包示例](examples)
