---
title: 修炼、境界与灵根
---

`element` 定义独立灵气类型及颜色；所有灵气值使用按资源分组的 Map。`realm_stage` 只能绑定一种资源，并通过 `next_realm` 形成线性境界链；玩家可以同时持有不同资源的多条链。境界 JSON 可以声明升级条件、修炼条件和多人共享灵气分配权重。已学习的功法全部同时生效。

`spirit_root` 和 `physique` 是附件中的可叠加来源，授予方式由 action 决定；本框架不规定具体灵根名称和数值。

```json
{
  "resource": "mxt:spirit_power",
  "next": "mxt:foundation",
  "cultivate_condition": {"type": "minecraft:always_true"},
  "aura_share_weight": 1.0,
  "upgrade_conditions": []
}
```
