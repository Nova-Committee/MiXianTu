---
title: 修炼、境界与灵根
---

`element` 定义独立灵气类型及颜色；所有灵气值使用按资源分组的 Map。每个数的修炼行为由 `cultivation` 档案描述（一对一引用一个 `resource`）：境界入口 `first_realm`、凡人阈值 `start_exp`、开始修炼条件 `start_cultivate_conditions`、自然恢复 `regen`、灵气标记 `aura_type`、灵力射线 `burst_amount`、修为双向换算、`use_condition` 与 `show_cultivation_info` 都在这里；`resource` 本身只负责存储数值与资源条。境界链属于档案：每个 `realm_stage` 通过 `cultivation` 字段指向档案，档案再指向数值，`next_realm` 把阶段连成一条只能前进的线性链；每个档案一条链，玩家可以同时持有多条链。境界阶段的 `breakthrough_exp`、`max_experience` 与 `breakthrough` 定义从当前阶段前往下一阶段的限制，`auto_breakthrough` 可选控制修炼时是否自动尝试突破（默认关闭）。凡人使用档案的 `start_exp` 作为首次突破阈值和上限，`first_realm` 只确定首次突破目标；首次突破使用目标首境界的 `breakthrough` 条件，`start_cultivate_conditions` 只用于开始修炼。`use_condition` 只控制资源条与主动消耗，不会阻止修炼、环境吸收或突破。已学习的功法全部同时生效。

`spirit_root` 和 `physique` 是附件中的可叠加来源，授予方式由 action 决定；本框架不规定具体灵根名称和数值。灵根和体质的持有状态是数据包原语：条件侧提供 `mxt:has_spirit_root`、`mxt:has_physique`，行为侧提供 `mxt:grant_spirit_root`、`mxt:remove_spirit_root`、`mxt:grant_physique`、`mxt:remove_physique`。体质可用 `holder_condition` 要求持有指定灵根或另一体质：

```json
{
  "attribute_modifiers": [{"attribute": "minecraft:max_health", "id": "example:physique/blazing_body", "amount": 2, "operation": "add_value"}],
  "granted_abilities": [],
  "holder_condition": {"type": "mxt:has_spirit_root", "spirit_root": "example:fire_root"}
}
```

```json
// data/example/mxt/cultivation/qi.json
{
  "resource": "example:qi",
  "first_realm": "example:foundation",
  "start_exp": 100,
  "start_cultivate_conditions": { "conditions": [] },
  "use_condition": {
    "type": "mxt:has_realm",
    "resource": "example:qi"
  }
}
```

```json
// data/example/mxt/realm_stage/foundation.json
{
  "cultivation": "example:qi",
  "cultivate_condition": {"type": "minecraft:always_true"},
  "aura_share_weight": 1.0,
  "breakthrough_exp": 100,
  "max_experience": 250,
  "auto_breakthrough": false,
  "breakthrough": { "conditions": [] }
}
```
