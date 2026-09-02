---
title: 修炼、境界与灵根
---

`element` 定义独立灵气类型及颜色；所有灵气值使用按资源分组的 Map。`realm_stage` 只能绑定一种资源，并通过 `next_realm` 形成线性境界链；玩家可以同时持有不同资源的多条链。境界阶段的 `breakthrough_exp`、`max_experience` 与 `breakthrough` 定义从当前阶段前往下一阶段的限制，`auto_breakthrough` 可选控制修炼时是否自动尝试突破（默认关闭）。凡人使用资源的 `start_exp` 作为首次突破阈值和上限，`first_realm` 只确定首次突破目标；首次突破使用目标首境界的 `breakthrough` 条件，`start_cultivate_conditions` 只用于开始修炼。`use_condition` 只控制资源条与主动消耗，不会阻止修炼、环境吸收或突破。已学习的功法全部同时生效。

`spirit_root` 和 `physique` 是附件中的可叠加来源，授予方式由 action 决定；本框架不规定具体灵根名称和数值。

```json
{
  "resource": "mxt:spirit_power",
  "first_realm": "mxt:foundation",
  "start_exp": 100,
  "start_cultivate_conditions": { "conditions": [] },
  "use_condition": {
    "type": "mxt:has_realm",
    "resource": "mxt:spirit_power"
  },
  "cultivate_condition": {"type": "minecraft:always_true"},
  "aura_share_weight": 1.0,
  "breakthrough_exp": 100,
  "max_experience": 250,
  "auto_breakthrough": false,
  "breakthrough": { "conditions": [] }
}
```
