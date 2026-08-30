---
title: KubeJS API 与边界
---

根对象为 `Mxt`，API 通过 `Mxt.api()` 暴露；回调注册通过 `Mxt.actions()`、`Mxt.conditions()` 和 `Mxt.values()` 暴露。修炼条件使用通用实体条件的 `mxt:js` 类型，不再提供独立的 `cultivation_condition` 注册表或 KubeJS 入口。

可使用的扩展方向：

- 注册物品、方块、配方和标签。
- 监听服务器加载、玩家事件和自定义事件总线。
- 通过脚本创建复杂的条件/行为组合，或调用公开查询接口。
- 读取当前资源、灵气和物品匹配结果用于自定义显示。

常用 API：

```js
Mxt.api().addCultivation(entity, 'mxt:qi', 10)
Mxt.api().useAbility(entity, 'example:fireball')
Mxt.api().tryBreakthrough(entity, 'example:spirit_power')
Mxt.api().applyCurse(entity, 'example:weakness', 1, 'example:source')
Mxt.api().removeCurse(entity, 'example:weakness')
Mxt.api().getAura(level, entity.blockPosition())
Mxt.api().tryConsumeResources(entity, '[{"id":"example:spirit_power","amount":10}]')
```

固有脚本类型注册：

```js
Mxt.actions().entity('example:heal', (entity, data) => entity.heal(data.amount || 1))
Mxt.conditions().entity('example:is_sneaking', (entity, data) => entity.isCrouching())
Mxt.values().number('example:level_scaled', (context, data) => context.level + (data.offset || 0))
```

对应数据包类型均为 `mxt:js`，脚本 ID 必须唯一。

服务端事件包括 `MxtEvents.abilityUse`、`curseApply`、`resourceConsume` 和 `auraZone`。其中 ability、curse、resource 的 pre 事件可以取消或修改允许修改的字段。

服务端权威边界：资源扣除、技能 Cost、修炼推进、境界突破、阵法维护和交易结算必须由服务端运行时完成。脚本应调用公开服务或发送合法事件，不应直接改写客户端显示来伪造结果。

不同版本 KubeJS 的 Java 包装名称可能变化，脚本示例应以当前 KubeJS/NeoForge 运行日志暴露的对象为准。
