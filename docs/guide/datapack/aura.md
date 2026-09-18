---
title: 灵气环境与灵气物品
---

`aura_zone` 提供世界环境灵气，按群系、维度、自定义区域和阵法覆写叠加。环境噪声支持 seed、柏林噪声和基础上限；自然环境通常保持在较低范围，方块和物品贡献独立计算。

`block_aura` 定义方块提供的灵气值；`item_aura` 定义物品携带的灵气种类（`type`）、容量、释放速度、耗尽行为和可选结果物品。方块释放的灵气不计入环境上限，多名玩家按区域分配策略共享。

```json
// data/example/mxt/block_aura/aura_source.json
{
  "blocks": ["#example:aura_source", "minecraft:amethyst_block"],
  "aura": {"mxt:common": {"amount": 12}}
}
```

```json
// data/example/mxt/item_aura/spirit_stone.json
{
  "items": ["mxt:spirit_stone"],
  "type": "mxt:common",
  "aura": 100,
  "consume_speed": 0.5,
  "release_speed": 1.0,
  "result_stack": {"id": "mxt:empty_spirit_stone", "count": 1}
}
```
