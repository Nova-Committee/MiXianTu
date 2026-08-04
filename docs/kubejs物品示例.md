# KubeJS 物品与 MXT 绑定

物品本体应由 KubeJS 在启动脚本阶段注册，MXT 数据包随后用真实物品 ID 绑定玩法规则。不要再创建 `mxt:item`、`mxt:pill` 或 `mxt:weapon` 文件。

```js
// kubejs/startup_scripts/mxt_items.js
StartupEvents.registry('item', event => {
  event.create('fire_root_pellet')
    .displayName('Fire Root Pellet')
    .food(food => food.hunger(2).saturation(0.2))

  event.create('returning_pill')
    .displayName('Returning Pill')
    .food(food => food.hunger(1).saturation(0.1))

  event.create('firebound_sword', 'sword')
    .displayName('Firebound Sword')
    .tier('diamond')
})
```

对应的数据包绑定：

```json
// kubejs/data/example/mxt/item_binding/fire_root_pellet.json
{
  "items": "kubejs:fire_root_pellet",
  "actions": [
    {
      "type": "mxt:grant_spirit_root",
      "spirit_root": "example:fire_root"
    }
  ]
}
```

```json
// kubejs/data/example/mxt/weapon_binding/firebound_sword.json
{
  "items": ["kubejs:firebound_sword", "#example:fire_weapons"],
  "attack_damage": 8,
  "attack_speed": -2.4
}
```

```json
// kubejs/data/example/mxt/pill_binding/returning_pill.json
{
  "items": "kubejs:returning_pill",
  "toxicity_gain": 10,
  "toxicity_threshold": 100,
  "toxicity_after_overdose": 25
}
```

KubeJS 重载物品注册表后需要重启游戏；MXT 的绑定数据可使用 `/reload` 重载。绑定的物品 ID 不存在时，数据包加载会失败，避免产生无法解析的物品规则。
