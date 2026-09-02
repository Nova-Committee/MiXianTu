---
title: KubeJS 综合示例
---

```js
StartupEvents.registry('item', event => {
  event.create('spirit_manual').displayName('无名功法书')
  event.create('spirit_stone').displayName('灵石')
})

ServerEvents.recipes(event => {
  event.shaped('example:spirit_manual', ['ABA', ' C ', 'ABA'], {
    A: 'minecraft:paper',
    B: 'minecraft:lapis_lazuli',
    C: 'minecraft:book'
  })
})
```

```js
// kubejs/server_scripts/mxt_events.js
MxtEvents.abilityUse(event => {
  if (event.isPre() && event.getAbility() === 'example:forbidden') event.cancel()
})

MxtEvents.resourceConsume(event => {
  if (event.isPre()) event.setAmount('example:spirit_power', event.getAmounts()['example:spirit_power'] || 0)
})

MxtEvents.cultivationBreak(event => {
  if (event.getPhase() === 'Pre') {
    // event.getEvent() is the native CultivationBreakEvent.Pre.
    event.getEvent().setCost('example:spirit_power', 20)
  }
})
```

对应的数据包可以把 `example:spirit_manual` 绑定到功法，把 `example:spirit_stone` 接入 `item_aura` 或 `currency`。这样脚本只负责内容注册，规则仍可热重载和同步。
