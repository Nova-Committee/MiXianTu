---
title: KubeJS API 参考
---

MiXianTu 的 KubeJS 桥接按领域提供独立对象，不提供承载全部方法的 `Mxt` 根对象。所有会改动游戏状态的 API 都必须从 `kubejs/server_scripts/` 调用，并会进入本体既有的服务端事务和事件流程。

下文的 `id`、`resource`、`ability`、`curse`、`zone` 等标识符均为带命名空间的字符串，例如 `mxt:spirit_power` 或 `example:fireball`。传入非法标识符或无法按 Codec 解析的 JSON 会直接抛出脚本错误，以便定位数据问题。

## 概览

| Global | Responsibility |
| --- | --- |
| `MxtActions` | 注册 `mxt:js` Action 回调，或运行内置 Action。 |
| `MxtConditions` | 注册 `mxt:js` Condition 回调，或测试内置 Condition。 |
| `MxtValues` | 注册/计算 NumberProvider 与 ResourceValueProvider。 |
| `MxtCosts` | 预检或支付一个完整的 `Cost`。 |
| `MxtResources` | 原子支付多个资源 Cost。 |
| `MxtAbilities` | 施放已授予实体的技能。 |
| `MxtCultivation` | 增加修为、尝试境界突破。 |
| `MxtCurses` | 施加或显式移除诅咒。 |
| `MxtAura` | 查询、添加、移除服务端灵气区域。 |
| `MxtSouls` | 回收实体可转移的魂魄。 |
| `MxtTriggers` | 发布自定义触发器信号，并让脚本订阅信号。 |
| `MxtLoot` | 注册脚本战利品条件与战利品函数。 |
| `MxtEvents` | 所有 MXT 服务端生命周期事件。 |

`Entity`、`LivingEntity`、`Player`、`Level`、`BlockPos`、`ItemStack`、`DamageSource` 均为 KubeJS 暴露的原版 Java 对象。`JsonObject`/`JsonElement` 参数可直接传普通 JavaScript 对象或数组。

## 通用数据规则

### NumberProvider

凡是参数名为 `amount`、`value` 等 NumberProvider 的字段都支持以下写法：

```js
10                                      // 常量
'level * 2 + 1'                         // 公式
{ type: 'mxt:uniform', min: 1, max: 3 } // 固有类型对象
```

公式上下文由调用 API 自动从实体或世界创建。脚本回调或 Provider 的计算结果必须是有限数字；`NaN`、`Infinity`、异常或未注册回调都会记录日志并作为 `0` 处理。

### `mxt:js` 回调定义

Action、Condition、NumberProvider、ResourceValueProvider、触发器匹配器、Cost、技能目标选择器、战利品条件与战利品函数均有一个预注册的 `mxt:js` 类型。先在服务器脚本中注册回调：

```js
MxtActions.entity('example:heal', (entity, params) => {
  entity.heal(params.amount || 1)
})
```

再在任意相应的数据包字段中使用：

```json
{
  "type": "mxt:js",
  "id": "example:heal",
  "params": { "amount": 4 }
}
```

`id` 在**同一种回调类别**内唯一。KubeJS 重载服务器脚本前会清空全部回调，随后重新执行脚本注册；不要把注册放入只执行一次的客户端脚本。没有找到回调时，Action 不执行、Condition 返回 `false`、数值返回 `0`，并输出警告日志。

## `MxtActions`

### 注册脚本 Action

| 方法 | 回调参数 | 作用 |
| --- | --- | --- |
| `entity(id, callback)` | `(entity: Entity, params: object, context: FormulaContext)` | 注册实体 Action。 |
| `biEntity(id, callback)` | `(actor: Entity, target: Entity, params: object, context: FormulaContext)` | 注册双实体 Action。 |
| `block(id, callback)` | `(level: Level, pos: BlockPos, params: object, context: FormulaContext)` | 注册方块位置 Action。 |
| `item(id, callback)` | `(holder: Entity, stack: ItemStack, params: object, context: FormulaContext)` | 注册物品 Action。 |

四种回调都对应数据包中的 `type: "mxt:js"`。回调抛出的异常会被捕获、写入错误日志，当前 Action 终止，但不会令服务器崩溃。

最后一个参数是本次派发所用的公式上下文，因此脚本能读到数据包 Action 能读到的同一份事件载荷。只有事件知道的数值（例如 `damage`）就是这样拿到的：

```js
MxtActions.entity('example:knockback_on_hit', (entity, params, context) => {
  const damage = context.explicit('damage')
  if (Number.isNaN(damage)) return
  entity.push(0, params.strength * damage, 0)
})
```

不需要上下文时少写几个参数即可，JavaScript 会忽略多余实参。可用的变量方法见 [`FormulaContext`](#formulacontext)；内置变量都能读，但不能注册新变量。

### 直接运行任意内置 Action

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `executeEntity(entity, definition)` | `Entity`、实体 Action JSON | `void` | 通过 `EntityAction.CODEC` 解析并执行。 |
| `executeBiEntity(actor, target, definition)` | 两个 `Entity`、双实体 Action JSON | `void` | 以 `actor` 作为公式上下文主体。 |
| `executeBlock(level, pos, definition)` | `Level`、`BlockPos`、方块 Action JSON | `void` | 以世界作为公式上下文。 |
| `executeItem(holder, stack, definition)` | `Entity`、`ItemStack`、物品 Action JSON | `void` | 以持有者作为公式上下文主体。 |

`definition` 是一个 Action 对象，格式与数据包内单个 Action 完全一致，`type` 会走现有固有注册表分派：

```js
MxtActions.executeEntity(player, {
  type: 'mxt:heal',
  amount: 4
})
```

## `MxtConditions`

### 注册脚本 Condition

| 方法 | 回调参数 | 返回值 |
| --- | --- | --- |
| `entity(id, callback)` | `(entity, params, context)` | `boolean` |
| `biEntity(id, callback)` | `(actor, target, params, context)` | `boolean` |
| `block(id, callback)` | `(level, pos, params, context)` | `boolean` |
| `item(id, callback)` | `(holder, stack, params, context)` | `boolean` |
| `damage(id, callback)` | `(source: DamageSource, amount: number, params, context)` | `boolean` |

它们分别对应 Entity、BiEntity、Block、Item、Damage Condition 的 `mxt:js` 固有类型。返回 `true` 表示满足；未注册或回调异常均视为 `false`。末尾的 `context` 是本次判定的公式上下文，与脚本 Action 完全一致。

### 直接测试任意内置 Condition

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `testEntity(entity, definition)` | `Entity`、实体 Condition JSON | `boolean` | 解析 `EntityCondition.CODEC`。 |
| `testBiEntity(actor, target, definition)` | 两个 `Entity`、双实体 Condition JSON | `boolean` | 公式上下文来自 `actor`。 |
| `testBlock(level, pos, definition)` | `Level`、`BlockPos`、方块 Condition JSON | `boolean` | 公式上下文来自世界。 |
| `testItem(holder, stack, definition)` | `Entity`、`ItemStack`、物品 Condition JSON | `boolean` | 公式上下文来自持有者。 |
| `testDamage(level, source, amount, definition)` | `Level`、`DamageSource`、伤害值、Damage Condition JSON | `boolean` | 有直接来源实体时使用其公式上下文，否则使用空上下文。 |

```js
const enoughQi = MxtConditions.testEntity(player, {
  type: 'mxt:resource_compare',
  resource: 'mxt:spirit_power',
  comparison: '>=',
  value: 10
})
```

## `MxtValues`

### 注册脚本 Provider

| 方法 | 回调参数 | 返回值 | 用途 |
| --- | --- | --- | --- |
| `number(id, callback)` | `(context: FormulaContext, params: object)` | 有限 `number` | `mxt:js` NumberProvider。 |
| `resourceValue(id, callback)` | `(holder: ResourceHolderAttachment, resource: Holder<Resource>, context: FormulaContext, params: object)` | 有限 `number` | `mxt:js` ResourceValueProvider。 |

#### `FormulaContext`

所有拿到公式上下文的回调用的都是同一个对象：`MxtValues.number`、`MxtValues.resourceValue`，以及 Action、Condition 的 `mxt:js` 回调。它提供：

| 方法 | 说明 |
| --- | --- |
| `context.value(name)` | 读取变量：先取显式上下文值，再查内置变量表。上下文无法提供的名字会被报告——开发环境直接抛异常，生产环境每个名字记录一次警告并返回 `0`。 |
| `context.explicit(name)` | 只取显式值；上下文没有该值时返回 `NaN`。用于判断当前事件是否提供了某个载荷，而不用去查变量表。 |
| `context.contains(name)` | 判断当前上下文能否提供该名字；不报告也不抛异常。 |
| `context.player()` | 返回当前玩家；不存在时为 `null`。 |
| `context.caster()` | 返回施法实体；上下文没有实体时为 `null`。 |
| `context.target()` | 返回双实体公式中的第二个实体；不存在时为 `null`。 |
| `context.resource()` | 返回本次计算绑定的资源修炼状态；公式不针对单个资源时为 `null`。 |
| `context.variables()` | 只返回显式值（事件载荷与调用方写入的值）；实体与资源变量按需从上下文对象读取，不在此表中。 |
| `context.random()` | 返回本次计算使用的权威随机源。 |

`resourceValue` 中的 `holder` 是资源附件，`resource` 是资源 Holder；通常只读使用，例如 `holder.get(resource)`。不要把该回调用于写入状态。

### 求值

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `evaluateNumber(entity, definition)` | `Entity`、NumberProvider 对象 | `number` | 计算任意注册 Provider。非有限结果返回 `0`。 |
| `evaluateResource(entity, resource, definition)` | `LivingEntity`、资源 ID、ResourceValueProvider JSON | `number` | 计算指定资源的值。实体感知 Provider 会读取当前位置环境/实际灵气。 |

同一个 JSON 文本只解析一次并复用（解析会构建整棵 Provider 树，包括其中的表达式），所以脚本每 tick 计算同一份定义也只付出一次解析代价。世界注册表变化时缓存会被丢弃；脚本改动定义后 JSON 文本不同，也会重新解析。

```js
const levelScaled = MxtValues.evaluateNumber(player, {
  type: 'mxt:expression',
  expression: 'level * 2 + 1'
})
const actualAura = MxtValues.evaluateResource(player, 'mxt:spirit_power', {
  type: 'mxt:actual_concentration'
})
```

## `MxtCosts` 与 `MxtResources`

### `MxtCosts`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `check(player, definition)` | `Player`、一个 `Cost` JSON | `boolean` | 只检查，不改变背包或资源。它是只读的，客户端脚本也可以调用。 |
| `consume(player, definition)` | `Player`、一个 `Cost` JSON | `boolean` | 先检查再支付；无法支付时不做改动。仅服务端：在客户端脚本中调用只记录一次警告并返回 `false`，不会改动玩家。 |
| `register(id, check, consume)` | 回调 ID、`(player, params, context) => boolean`、`(player, params, context) => void` | `void` | 注册脚本 Cost。数据包类型：`mxt:js`。 |

```js
MxtCosts.register('example:quest_token',
  (player, params, context) => {
    const needed = params.count || 1
    return player.persistentData.getInt('tokens') >= needed
  },
  (player, params, context) => {
    const needed = params.count || 1
    player.persistentData.putInt('tokens', player.persistentData.getInt('tokens') - needed)
  }
)
```

```json
{"type": "mxt:js", "id": "example:quest_token", "params": {"count": 3}}
```

脚本 Cost 需要玩家：只要 `costs` 里存在非 `resource` 费用，整个技能就要求付款者是玩家。`Cost` 只用玩家做检查，因此回调拿到的 `context` 由该玩家构建，不含事件载荷——`context.value('level')` 可用，`context.value('damage')` 不可用。

支持完整 Cost 注册表分派。当前内置类型：

```js
// 消耗资源。完整写法
{ type: 'mxt:resource', resource: 'mxt:spirit_power', amount: 10 }

// 消耗资源。兼容简写；仅 Cost API 可以使用 id 字段。
{ id: 'mxt:spirit_power', amount: 10 }

// 消耗物品。items 接受物品 ID、物品 tag，或 ItemMatcher 对象。
{ type: 'mxt:item', items: ['minecraft:emerald', '#c:mystic_gems'], amount: 2 }
```

单个 `Cost` 是安全入口；多项资源请使用下方的 `MxtResources.consume`，它具有原子事务语义。不要把多个 `MxtCosts.consume` 当成一个原子支付。

### `MxtResources`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `consume(entity, costs)` | `Entity`、`ResourceCost[]` | `ResourceTransactions.Result` | 原子支付一组资源；任一项不足时整组不扣除。 |

`costs` 的每个元素遵循 `ResourceCost` 格式，字段名是 `resource`，不是 `id`：

```js
const result = MxtResources.consume(player, [
  { resource: 'mxt:spirit_power', amount: 10 },
  { resource: 'mxt:fire_aura', amount: 'level + 2' }
])

if (result.committed()) {
  console.info(`已扣除: ${result.amounts()}`)
} else {
  console.warn(`资源不足: ${result.failedResource()}`)
}
```

返回 record 的访问器为 `committed()`、`failedResource()`、`amounts()`。在客户端、非法公式或未满足资源时 `committed()` 均为 `false`。

## 运行时领域 API

### `MxtAbilities`

| 方法 | 参数 | 返回值 |
| --- | --- | --- |
| `use(entity, ability)` | `Entity`、技能 ID | `AbilityService.UseResult` |
| `selector(id, callback)` | 回调 ID、`(actor, context, params) => Entity[]` | `void` |

只能施放实体已持有的技能，且仅在服务端生效。结果 record：`committed()` 表示立即完成，`casting()` 表示已开始吟唱，`failure()` 为失败枚举，`failedResource()` 为不足的资源 ID，`amounts()` 为实际支付资源。

```js
const result = MxtAbilities.use(player, 'example:fireball')
if (result.failure() !== null) console.warn(String(result.failure()))
```

`selector` 注册数据包类型 `mxt:js` 的技能目标选择器。回调返回要作用的实体数组，数组里的 `null` 会被丢弃：

```js
MxtAbilities.selector('example:nearest_three', (actor, context, params) => {
  const range = params.range || 8
  const found = []
  actor.level().getEntities(actor, actor.getBoundingBox().inflate(range))
    .forEach(entity => found.push(entity))
  found.sort((a, b) => a.distanceToSqr(actor) - b.distanceToSqr(actor))
  return found.slice(0, 3)
})
```

```json
{
  "target_selector": {"type": "mxt:js", "id": "example:nearest_three", "params": {"range": 12}},
  "bi_entity_action": {"type": "mxt:heal", "amount": 4}
}
```

### `MxtCultivation`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `add(entity, resource, amount)` | `LivingEntity`、资源 ID、有限非负数 | `boolean` | 向该资源对应的修为进度增加数值。客户端、未知资源、负数或非有限数返回 `false`。 |
| `tryBreakthrough(entity, resource)` | `LivingEntity`、资源 ID | `CultivationService.BreakthroughResult` | 按对应资源的境界链尝试突破。 |

突破结果的 record 访问器为 `advanced()`、`failure()`、`failedResource()`、`costs()`。它会正常触发 `cultivationBreak`、突破动作、粒子、天劫和关联技能流程。

### `MxtCurses`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `apply(entity, curse, stacks, source)` | `Entity`、诅咒 ID、正整数层数、非空来源字符串 | `CurseService.ApplyResult` | 走完整条件和合并逻辑。 |
| `remove(entity, curse)` | `Entity`、诅咒 ID | `boolean` | 以 `EXPLICIT` 原因移除；触发移除事件。 |

`ApplyResult` 可调用 `applied()`、`cancelled()`、`failure()`、`instance()`。`source` 建议写稳定来源，如 `example:quest_reward`，以便数据和事件追踪。

### `MxtAura`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `get(level, pos)` | `Level`、`BlockPos` | `AuraResult` | 读取该位置最终解析后的多资源灵气。只读，客户端可查询其本地可用状态。 |
| `addBox(level, zone, minX, minY, minZ, maxX, maxY, maxZ, priority)` | 服务端 `Level`、已加载 aura zone ID、两个方块坐标、整数优先级 | `string` | 添加持久化长方体区域，返回生成的区域 ID。 |
| `remove(level, area)` | 服务端 `Level`、`addBox` 返回的区域 ID | `boolean` | 删除对应持久化区域。 |

`addBox` 仅接受 `ServerLevel`，且 `zone` 必须是已加载的 `aura_zone` 数据包 ID；否则抛出异常。`AuraResult` 常用只读方法：`aura()`、`concentration()`、`maximum()`、`regenPerTick()`、`cultivationSpeed()`、`source()`、`sourceKind()`、`suppressCultivate()`。

### `MxtSouls`

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `reclaim(entity)` | `Entity` | `boolean` | 使用权威魂魄回收流程。仅适用于可转移魂魄；会触发 `soul` 的回收 pre/post 事件。 |

### `MxtTriggers`

触发器信号就是数据包技能在等待的通知（例如持有者命中目标）。`MxtTriggers` 让脚本发布同一种信号，也让脚本等待它；两侧都走运行时 `TriggerDispatcher`，因此脚本订阅和数据包技能触发器看到的是同一次派发。

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `matcher(id, callback)` | 回调 ID、`(signal, params, context) => boolean` | `void` | 注册数据包触发器匹配器（`mxt:js`）。 |
| `publish(entity, signal, values)` | `Entity`、带命名空间的信号 ID、`object` 或 `null` | `boolean` | 为该实体发布一次服务端权威信号；实体在客户端时返回 `false`。 |
| `subscribe(entity, signal, key, callback)` | `Entity`、信号 ID、稳定 key、`(signal: TriggerSignal) => void` | `boolean` | 为该实体注册一个运行时订阅。 |
| `subscribeOnce(entity, signal, key, callback)` | 同上 | `boolean` | 注册一次性订阅，首次匹配后自行移除。 |
| `unsubscribe(entity, key)` | `Entity`、注册时使用的 key | `boolean` | 移除一个脚本订阅。 |
| `has(entity, key)` | `Entity`、注册时使用的 key | `boolean` | 当前是否存在该订阅。 |
| `subscriptions(entity)` | `Entity` | `number` | 该实体当前的脚本订阅数量。 |

`matcher` 是同一套机制的数据包侧：`mxt:js` 触发器声明自己监听哪个信号，再由回调决定是否触发。这是数据包技能或突破条件等待**自定义**信号 ID 的唯一方式，内置的 `mxt:tick` 一类匹配器表达不了：

```js
MxtTriggers.matcher('example:on_pill', (signal, params, context) => {
  return context.explicit('toxicity') >= (params.minimum || 0)
})
```

```json
{
  "type": "mxt:triggered",
  "triggers": [{"type": "mxt:js", "signal": "example:pill_taken", "id": "example:on_pill", "params": {"minimum": 10}}]
}
```

回调收到 `TriggerSignal` 与该信号的公式上下文，因此能读到脚本用 `MxtTriggers.publish` 发布的载荷值。回调缺失或抛异常时该触发器永不匹配。

```js
// kubejs/server_scripts/mxt_triggers.js
const KEY = 'example:toxicity_watch'

function onPillTaken(signal) {
  const toxicity = signal.context().formula().explicit('toxicity')
  if (!Number.isNaN(toxicity)) console.info(`${signal.type()} with toxicity ${toxicity}`)
}

function watch(entity) {
  // 同一个 key 重复注册会替换旧订阅，所以这里可以安全地反复调用。
  if (!MxtTriggers.has(entity, KEY)) {
    MxtTriggers.subscribe(entity, 'example:pill_taken', KEY, onPillTaken)
  }
}

EntityEvents.spawned(event => watch(event.entity))

// /reload 会重新执行本脚本并丢掉它的订阅；这里为仍在线的玩家重新挂上。
// 发布信号时不要求存在订阅者。
ServerEvents.tick(event => event.server.players.forEach(watch))

// 在你自己的逻辑里发布：命令处理、任务钩子或物品使用。
function publishPillTaken(player, toxicity) {
  MxtTriggers.publish(player, 'example:pill_taken', { pill: 'example:returning_pill', toxicity })
}
```

`values` 会被复制进触发上下文作为扩展数据，其中每个有限数值还会写入触发器的公式上下文。因此脚本回调可以用 `signal.context().formula().explicit('toxicity')` 读到上面的 `toxicity`，与数据包信号携带 `damage` 的方式完全一致。

一个 key 在每个实体上只对应一个订阅，请为每个信号使用独立的 key：用同一个 key 再次注册会替换掉旧的订阅，无论它原本监听的是哪个信号。

回调收到一个 `TriggerSignal`，其访问器为 `type()`（信号 ID）、`gameTime()` 与 `context()`。上下文提供 `actor()`、`target()`、`level()`、`position()`、`item()`、`block()`、`damageSource()`、`formula()`，以及读取发布值的 `get(key)`（返回 `Optional`）与 `data()`（原始扩展表）。请把上下文当作只读对象：它会被同一信号的多个订阅者共享。

触发器订阅只存在于运行时，永不存档：实体离开世界、服务器关闭、数据包重载或服务器脚本重载都会丢掉它们——重载会替换订阅所引用的回调对象。请使用稳定的 key，并从运行时钩子重新注册，例如上面的写法。一次性订阅在首次匹配后自行移除，适合只能完成一次的任务步骤。

### `MxtLoot`

MiXianTu 给原版战利品表加了几个类型，其中两个可以由脚本提供，于是普通战利品表也能调用服务端脚本，而不需要依赖某个物品或方块。

| 方法 | 参数 | 返回值 | 说明 |
| --- | --- | --- | --- |
| `condition(id, callback)` | 回调 ID、`(lootContext, params) => boolean` | `void` | 注册原版战利品条件类型 `mxt:js`。 |
| `function(id, callback)` | 回调 ID、`(stack, lootContext, params) => ItemStack` | `void` | 注册原版战利品函数类型 `mxt:js`。 |

```js
// KubeJS 没有把原版战利品参数键绑定为全局，先加载一次类。
const LootParams = Java.loadClass('net.minecraft.world.level.storage.loot.parameters.LootContextParams')

MxtLoot.condition('example:first_clear', (loot, params) => {
  const player = loot.getParam(LootParams.THIS_ENTITY)
  return player != null && player.tags.contains(`cleared_${params.dungeon}`)
})

MxtLoot.function('example:bless', (stack, loot, params) => {
  stack.grow((params.multiplier || 1) - 1)
  return stack
})
```

```json
{
  "conditions": [{"condition": "mxt:js", "id": "example:first_clear", "params": {"dungeon": "example:fire_temple"}}],
  "functions": [{"function": "mxt:js", "id": "example:bless", "params": {"multiplier": 3}}]
}
```

两个回调都在服务端生成战利品时执行，且都不接收 `FormulaContext`：请直接读原版 `LootContext`，例如 `loot.getParam(LootParams.THIS_ENTITY)`。战利品函数返回要保留的 stack——原样返回表示不改动掉落，返回新 stack 表示替换，返回 `null` 表示保留原样。回调缺失时条件为 `false`、函数保留原 stack，并记录一条警告。

## `MxtEvents`

所有事件都注册在服务器事件组。订阅方式：

```js
MxtEvents.abilityUse(event => {
  if (event.isPre() && event.getAbility() === 'example:forbidden') {
    event.cancel()
  }
})
```

`event.cancel()` 会立即停止当前 KubeJS 监听链。只有底层是可取消 NeoForge 事件时才会取消 MXT 事务；非可取消阶段调用仅停止脚本监听，不会撤销已经发生的游戏行为。

### 专用事件包装

| 事件 | 阶段 | 可用方法 | 可修改内容 |
| --- | --- | --- | --- |
| `abilityUse` | `Pre`、`Post` | `getEntity()`、`getAbility()`、`isPre()`、`getPaidCosts()` | `Pre` 可取消。`getPaidCosts()` 在 `Pre` 返回空映射。 |
| `curseApply` | `Pre`、`Post` | `getCurse()`、`isPre()`、`getStacks()`、`setStacks(n)`、`getSource()`、`setSource(text)` | 仅 `Pre` 可取消和修改层数/来源；层数必须大于 0。对 `Post` 调用 setter 会抛异常。 |
| `resourceConsume` | `Pre`、`Post` | `isPre()`、`getAmounts()`、`setAmount(resource, amount)` | 仅 `Pre` 可取消和修改单项资源量；金额必须有限且大于 0。 |
| `auraZone` | `enter`、`leave`、`tick`、`override` | `getKind()`、`getSource()`、`getConcentration()`、`isCultivationSuppressed()`、`getOverrideZone()` | 仅 `override` 可取消。`getOverrideZone()` 非 override 时返回空字符串。 |

资源映射的键已转换为字符串 ID。例如：

```js
MxtEvents.resourceConsume(event => {
  if (!event.isPre()) return
  const amounts = event.getAmounts()
  if (amounts['mxt:spirit_power'] > 0) {
    event.setAmount('mxt:spirit_power', 5)
  }
})
```

### 通用生命周期事件

其余事件使用通用包装，方法为：

| 方法 | 返回/作用 |
| --- | --- |
| `getType()` | KubeJS 事件名，例如 `cultivationBreak`。 |
| `getPhase()` | 实际 Java 阶段类名，例如 `Pre`、`StrikePre`、`StartPost`。 |
| `isCancellable()` | 当前阶段是否可取消。 |
| `getEvent()` | 原生 MXT 事件实例，可调用下表列出的 Java accessor。 |
| `cancel()` | KubeJS 标准取消方法；仅在 `isCancellable()` 为 `true` 时会取消底层事务。 |

以下表中 `native` 代表 `const native = event.getEvent()`。Identifier、Holder、Attachment 等返回值均为 Java 对象；需要文本 ID 时使用 `String(value)`。

| KubeJS 事件 | 阶段类名 | `native` 的主要 accessor / 语义 |
| --- | --- | --- |
| `abilityTriggered` | `Pre`、`Post` | `getEntity()`、`getAbility()`、`signalType()`、`context()`；`Pre` 可取消触发的技能。原生事件的 `ability()` 返回 `Holder<Ability>`，需要 ID 时使用 `HolderHelper.id(...)` 或 KubeJS 包装器的 `getAbility()`。 |
| `curseRemove` | `Pre`、`Post` | `curse()`（`Holder<Curse>`）、`state()`、`reason()`、`gameTime()`、`holder()`；`Pre` 可取消移除。reason 为 `EXPLICIT`、`EXPIRED`、`CLEANSED`、`REPLACED`、`CONTENT_ACTION`、`ADMIN`。 |
| `cultivationBreak` | `Pre`、`Post` | `target()`（`Holder<RealmStage>`）、`threshold()`、`context()`、`spirit()`、`resources()`；`Pre` 另有 `originalCosts()`、`costs()`、`setCost(resource, amount)`，可取消；`Post` 有 `paidCosts()`。 |
| `techniqueLearn` | `Pre`、`Post` | `technique()`（`Holder<CultivationTechnique>`）、`spirit()`；`Pre` 可取消。 |
| `alchemyCraft` | `Pre`、`Post` | `recipe()`（`RecipeHolder<AlchemyRecipe>`）；`Pre.inputs()` 为输入 ID 列表且可取消；`Post.spoiled()`、`Post.outputs()` 为结果状态。 |
| `artifactRefine` | `Pre`、`Post` | `stack()`、`owner()`；`Pre` 可取消。 |
| `forging` | `Start`、`Started`、`StrikePre`、`StrikePost`、`CompletePre`、`CompletePost`、`Cancel` | 每个阶段都可读 `player()`（`ServerPlayer`）与 `pos()`（`BlockPos`，台子位置）。分阶段：`Start.blueprint()`；`Started/StrikePost/Cancel.session()`；`StrikePre.method()`（`Holder<ForgingMethod>`）、`resources()`、`context()`、`costs()`、`setCosts(costs)`；`CompletePre.blueprint()`、`session()`；`CompletePost.blueprint()`、`session()`、`result()`。`Start`、`StrikePre`、`CompletePre`、`Cancel` 可取消。 |
| `formation` | `Activate`、`Deactivate`、`Tick` | `level()`、`controller()`、`instance()`（阵法 ID 取 `instance().formation()`）；`Activate` 与 `Tick` 可取消。 |
| `lifespanEnd` | `Pre`、`Post` | `entity()`、`spirit()`；`Pre` 可取消结束，取消后寿元会被设为不受限。 |
| `realmInstance` | `EnterPre`、`EnterPost`、`Exit` | `level()`、`definition()`（`Holder<RealmInstance>`）、`member()`；只有 `EnterPre` 可取消。 |
| `sect` | `JoinPre`、`JoinPost`、`LeavePre`、`LeavePost`、`PromotePre`、`PromotePost` | `sect()`、`data()`；所有 `*Pre` 可取消。 |
| `soul` | `TransferPre`、`TransferPost`、`ReclaimPre`、`ReclaimPost` | `entity()`、`soul()`；所有 `*Pre` 可取消。 |
| `spiritContract` | `Pre`、`Post` | `contract()`、`contractType()`、`requester()`、`action()`；`contractType()` 是 `Optional<Holder<ContractType>>`，`action()` 为 `BIND`、`BREAK`、`RECALL`、`RELEASE`；`Pre` 可取消。 |
| `tribulation` | `StartPre`、`StartPost`、`PhasePre`、`PhasePost`、`Complete` | `tribulation()`（`Holder<Tribulation>`）、`phase()`、`data()`；`StartPre`、`PhasePre` 可取消。 |

### `forging` 的两条额外约定

**会话是只读的。** `session()` 返回 `ForgingSessionView`，可读 `value()`、`steps()`、`optimalSteps()`、
`history()`（不可变列表）、`canComplete()`，**不能**改写会话——原生的 `ForgingSession` 是可变对象，
已不再交给监听器，所以 `event.getEvent().session().strike(...)` 这类写法不存在。台子本身也不交给监听器，
只给位置 `pos()`；要读槽位就用 `player.level().getBlockEntity(pos)`。

**监听器不要抛异常，抛了也不会搞坏操作。** 四个决定型事件（`Start`、`StrikePre`、`CompletePre`、
`Cancel`）在事务中间派发，所以监听器抛出时会被服务端就地转成一次拒绝：日志记为 `LISTENER_ERROR`
（与脚本主动 `cancel()` 的 `CANCELLED` 区分），操作不发生、材料不消耗、会话保持原样。
三个通知型事件（`Started`、`StrikePost`、`CompletePost`）派发时操作已经生效，抛出只记录并忽略。

例如调整突破消耗：

```js
MxtEvents.cultivationBreak(event => {
  if (event.getPhase() !== 'Pre') return
  const native = event.getEvent()
  native.setCost('mxt:spirit_power', 20)
})
```

## 返回值与错误

服务 API 返回的 Java record 一律使用 Java accessor，例如 `result.committed()`，而非假设存在 JavaScript 字段。失败通常不会抛出：请检查 `failure()`、`committed()`、`advanced()`、`applied()` 等返回值。只有 API 参数非法、标识符非法、JSON 无法被对应 Codec 解码，或对错误事件阶段调用可变 setter 时才会抛异常。

只在服务端才有意义的操作遇到客户端脚本时不会抛异常，而是只记录一次警告并拒绝执行，避免脚本把客户端状态改坏：`MxtCosts.consume`，以及 `MxtAbilities`、`MxtCultivation`、`MxtCurses`、`MxtAura`、`MxtSouls`、`MxtTriggers` 中所有会改动状态的方法。

`MxtActions.execute*` 故意不设该保护，因为内置 Action 自己决定作用端：JSON 里声明了客户端执行的 Action（例如带 `client` 标志的速度 Action）本来就应当就地运行。

完整组合示例见 [KubeJS 综合示例](examples.md)。
