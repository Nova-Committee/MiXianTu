# 数据包与 KubeJS 边界

## 三层职责

| 层 | 适合放置的内容 | 不适合放置的内容 |
| --- | --- | --- |
| Java 核心 | 附件、持久化、网络、碰撞、容器、安全校验、高频 tick、固有类型注册表及 Codec | 任意具体境界和技能数值。 |
| 数据包 JSON | 以已注册 `type` 实例化能力、动作、条件、配方、成长表、结构和引用关系 | 新建 Java 类型、每 tick 的复杂脚本、直接修改实体私有状态。 |
| KubeJS | 生成或修改数据定义、监听事件、内容组合、受限的低频效果 | 注册 `MapCodec` 类型、大范围每 tick 扫描、未校验网络写入、无法恢复的存档迁移。 |

## 建议的 KubeJS API 形状

```js
// 查询与受校验的状态修改；返回 Java 结果对象或布尔成功状态
Mxt.api().tryBreakthrough(entity, "example:foundation")
Mxt.api().addCultivation(entity, 'mxt:qi', 10)
Mxt.api().useAbility(entity, "example:fire_ball")
Mxt.api().applyCurse(entity, "example:heart_demon", 1, "quest:trial")
Mxt.api().removeCurse(entity, "example:heart_demon")

// 资源扣除仍使用 costs 数组，并由服务端 API 原子提交
Mxt.api().tryConsumeResources(entity, JSON.stringify([
  { id: "example:spirit_power", amount: 20 }
]))

// 注入 JSON 定义，在下一次 /reload 与数据包一并校验、原子发布。
// 同 ID 的数据包定义与脚本定义会使重载失败，不会静默覆盖。
Mxt.registries().registerJson("realm_stage", "example:foundation", JSON.stringify({ /* ... */ }))
Mxt.registries().registerJson("ability", "example:fire_ball", JSON.stringify({ /* ... */ }))

// 监听业务事件；context 只暴露经过包装的安全 API
MxtEvents.abilityUse(event => { /* pre event: event.cancel() */ })
MxtEvents.resourceConsume(event => { /* pre event: event.setAmount("example:spirit_power", 10) */ })
```

对外 API 必须保持稳定，内部附件类不能直接暴露。所有 `try...` 方法返回明确结果对象，例如失败原因、逐项实际消耗和是否已同步，避免脚本自行猜测状态。所有资源成本均为 `costs` 数组，数组项只允许 `id` 和正数 `amount`；实现必须先完整预检，再一次性提交或零扣除回滚。

## 固有类型、数据定义与事件的区别

- 固有类型是 Java 在加载期注册的 `MapCodec` 条目，例如 `mxt:active` 能力、`mxt:damage` 动作、`mxt:on_fire` 条件。它决定 JSON 能使用哪些字段和如何运行，不能由数据包或 KubeJS 动态新增。
- 数据定义是数据包中的具名实例，例如 `example:fire_ball`；其 `type` 选择固有类型，并填充该类型定义的参数。KubeJS 可创建这种定义，但不应传入任意可执行函数。
- Event 是运行时广播，适合拦截、修改参数、记录或附加效果。事件监听者不应依赖固定顺序；需要确定顺序时应放进固有类型的 Java 状态机。
- 事件取消后，Java 必须负责回滚尚未提交的资源扣除，脚本不得自行补偿库存或灵力。

## 性能与安全底线

1. 任何客户端请求都必须由服务端重新校验距离、目标、冷却、资源、境界和物品归属。
2. `Tick` 事件按实体/区块分片、可配置频率执行；默认不允许每 tick 遍历全世界实体或区块。
3. 范围效果使用 Java 的空间索引/AABB 查询，并限制半径、数量和持续时间。
4. 脚本异常不能终止服务器 tick。捕获后记录定义 ID 和上下文，并让当前动作安全失败。
5. 数据包重载必须先完整校验引用、循环依赖和固有 `type`，再一次性发布新快照；不可暴露半加载状态。
6. 外部联动通过可选加载和小型适配器实现。Curios 仅提供额外装备槽读取；救援模组仅订阅濒死/复活事件，不能让其成为核心依赖。
