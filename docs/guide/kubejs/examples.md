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

对应的数据包可以把 `example:spirit_manual` 绑定到功法，把 `example:spirit_stone` 接入 `item_aura` 或 `currency`。这样脚本只负责内容注册，规则仍由数据包驱动并同步到各客户端。

回调还可以接收第三个参数——本次派发的公式上下文，用来读取只有触发事件才知道的数值：

```js
MxtActions.entity('example:knockback_on_hit', (entity, params, context) => {
  const damage = context.explicit('damage')
  if (Number.isNaN(damage)) return
  entity.push(0, params.strength * damage, 0)
})
```

## 自定义 Cost

Cost 先检查再支付，所以两半要一起注册；`id` 就是数据包 `costs` 数组里写的那个：

```js
MxtCosts.register('example:quest_token',
  (player, params, context) => player.persistentData.getInt('tokens') >= (params.count || 1),
  (player, params, context) => {
    player.persistentData.putInt('tokens', player.persistentData.getInt('tokens') - (params.count || 1))
  }
)
```

```json
{"type": "mxt:js", "id": "example:quest_token", "params": {"count": 3}}
```

脚本 Cost 需要玩家，而且它的上下文只由该玩家构建，所以读玩家自身状态（或该上下文的显式值），不要指望读到事件载荷。

脚本也可以自己发布并等待触发器信号。订阅只存在于运行时，因此要从运行时钩子挂载，并在重载后重新挂载：

```js
// kubejs/server_scripts/mxt_triggers.js
const KEY = 'example:toxicity_watch'

function onPillTaken(signal) {
  const toxicity = signal.context().formula().explicit('toxicity')
  if (!Number.isNaN(toxicity)) console.info(`${signal.type()} with toxicity ${toxicity}`)
}

function watch(entity) {
  if (!MxtTriggers.has(entity, KEY)) {
    MxtTriggers.subscribe(entity, 'example:pill_taken', KEY, onPillTaken)
  }
}

EntityEvents.spawned(event => watch(event.entity))

// /reload 会重新执行本脚本并丢掉它的订阅，这里为仍在线的玩家重新挂上。
ServerEvents.tick(event => event.server.players.forEach(watch))

// 在你自己的逻辑里发布：命令处理、任务钩子或物品使用。
function publishPillTaken(player, toxicity) {
  MxtTriggers.publish(player, 'example:pill_taken', {
    pill: 'example:returning_pill',
    toxicity
  })
}
```
