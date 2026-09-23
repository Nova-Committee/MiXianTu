---
title: 修炼、境界与灵根
---

`element` 定义独立灵气类型及颜色；所有灵气值使用按灵气分组的 Map。每个数的灵气身份与修炼行为由 `aura` 定义描述（一对一引用一个 `resource`）：境界入口 `first_realm`、凡人阈值 `start_exp`、开始修炼条件 `start_cultivate_conditions`、自然恢复 `regen`、灵气标记 `aura_type`、灵力射线 `burst_amount`、修为双向换算、`use_condition` 与 `show_cultivation_info` 都在这里；`resource` 本身只负责存储数值与资源条。境界链属于 `aura` 定义：每个 `realm_stage` 通过 `aura` 字段指向定义，定义再指向数值，`next_realm` 把阶段连成一条只能前进的线性链；每个定义一条链，玩家可以同时持有多条链。境界阶段的 `breakthrough_exp`、`max_experience` 与 `breakthrough` 定义从当前阶段前往下一阶段的限制，`auto_breakthrough` 可选控制修炼时是否自动尝试突破（默认关闭）。凡人使用定义的 `start_exp` 作为首次突破阈值和上限，`first_realm` 只确定首次突破目标；首次突破使用目标首境界的 `breakthrough` 条件，`start_cultivate_conditions` 只用于开始修炼。`use_condition` 只控制资源条与主动消耗，不会阻止修炼、环境吸收或突破。已学习的功法全部同时生效。

子境界（`minor_stages`）只是**显示与公式**：它把本境界的 `breakthrough_exp` 均匀切成若干段，信息面板在境界名后写出当前这一重的名字，公式里多一个从 `0` 起的 `minor_stage`。名字可以写成数组（字符串当翻译键、对象当完整组件），也可以直接写一个整数表示"这么多重"——那样名字按 id 自动生成为 `realm_stage.mxt.<命名空间>.<路径>.minor_stage.<下标>`（下标从 0 起）。它不参与突破判定、不改变任何阈值，进度超过 `breakthrough_exp`（上限是 `max_experience`）后停在最后一重；没有 `minor_stages`、或玩家还没有任何境界时该变量读出 `NaN`。

`spirit_root` 和 `physique` 是附件中的可叠加来源，授予方式由 action 决定；本框架不规定具体灵根名称和数值。灵根和体质的持有状态是数据包原语：条件侧提供 `mxt:has_spirit_root`、`mxt:has_physique`，行为侧提供 `mxt:grant_spirit_root`、`mxt:remove_spirit_root`、`mxt:grant_physique`、`mxt:remove_physique`。体质可用 `holder_condition` 要求持有指定灵根或另一体质：

```json
{
  "attribute_modifiers": [{"attribute": "minecraft:max_health", "id": "example:physique/blazing_body", "amount": 2, "operation": "add_value"}],
  "granted_abilities": [],
  "holder_condition": {"type": "mxt:has_spirit_root", "spirit_root": "example:fire_root"}
}
```

**两者各自接进伤害结算的一角**（见[数据包格式](../../数据包格式.md#伤害结算)的「伤害结算」一节）：灵根的 `element_ability_modifier` 是施放亲和元素技能时第一层的因子（匹配灵根按 `element_affinity_mode` 合并），体质的 `damage_dealt_multiplier` / `damage_taken_multiplier` 分别是第一层与第二层的因子，且体质那两条**与元素无关**——体质定义里出现任何元素或灵根字段，加载期就会拒绝并指名那个字段。灵根还提供 `cultivation_multiplier`（配合区块灵气浓度算修炼亲和）与 `conflicting_elements`（同体互斥，双向判定）；两者的 `rarity` 会显示在信息面板与该实体的 `/mxt spirit_root list` 与 `/mxt physique list` 里。

已持有的灵根和体质都可以**关闭而不失去**：脚本侧 `MxtSpiritRoots.setEnabled` / `MxtPhysiques.setEnabled`，管理员侧 `/mxt spirit_root enable|disable`、`/mxt physique enable|disable`，本模组不提供玩家界面。

```json
// data/example/mxt/aura/qi.json
{
  "resource": "example:qi",
  "first_realm": "example:foundation",
  "start_exp": 100,
  "start_cultivate_conditions": { "conditions": [] },
  "use_condition": {
    "type": "mxt:has_realm",
    "aura": "example:qi"
  }
}
```

```json
// data/example/mxt/realm_stage/foundation.json
{
  "aura": "example:qi",
  "cultivate_condition": {"type": "mxt:always_true"},
  "aura_share_weight": 1.0,
  "breakthrough_exp": 100,
  "max_experience": 250,
  "auto_breakthrough": false,
  "breakthrough": { "conditions": [] }
}
```
