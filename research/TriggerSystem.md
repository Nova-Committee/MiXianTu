# Trigger 系统设计

## 目标

历史版本中 AbilityTrigger 同时承担事件类型标识和数据包配置职责，运行时主要依靠字符串事件名分派。本文件定义并记录当前已实现的、可扩展且支持多个上下文参数、多个监听者及阶段性受理的通用 Trigger 系统。

核心原则：

- Dispatcher 只负责发布运行时信号，不判断业务归属。
- Trigger 负责匹配信号类型和事件参数。
- Subscription 负责表示谁在当前时刻监听，以及是否受理。
- 业务模块负责创建、激活和销毁自己的 Subscription。
- Subscription 是运行时派生状态，不直接写入存档。
- 存档只保存可验证、可迁移的业务状态；服务端加载后重建订阅。
- 服务端是唯一权威的发布和执行端。

## 分层模型

    NeoForge/原版事件
            |
            v
    TriggerSignal(type, context)
            |
            v
    TriggerDispatcher
            |
            +--> Trigger.matches(signal)
            +--> Subscription.gate
            +--> Subscription.listener

### TriggerSignal

TriggerSignal 表示一次已经发生的运行时事件，不包含某个技能或修炼流程的业务所有权。

| 字段        | 说明                                   |
|-----------|--------------------------------------|
| type      | 带命名空间的信号类型，例如 mxt:hurt、mxt:item_use。 |
| context   | 本次事件的完整上下文。                          |
| source    | 可选的事件来源，用于调试和审计。                     |
| game_time | 事件发生的服务端时间。                          |

信号默认采用广播语义。同一个 item_use 可以同时被多个技能、多个资源的突破条件和外部模组接收。一次性消费只能影响创建它的订阅，不能截断全局事件。

### Trigger

Trigger 是数据包中的匹配规则，通过类型注册表和 MapCodec 扩展。它至少需要判断信号类型，并可读取上下文中的固定字段或扩展字段。

示例：

    {
      "type": "mxt:hurt",
      "minimum_damage": 5
    }

建议接口语义：

    matches(TriggerSignal signal)
    description()
    codec()

Trigger 只负责匹配，不应直接修改实体或修炼数据。

### TriggerContext

上下文沿用现有 Context 的“固定字段 + 可扩展 Map”模型。对象创建后通过 setter 填充，不使用包含所有可能参数的超大构造函数。

| 字段            | 类型                  | 说明                     |
|---------------|---------------------|------------------------|
| actor         | Entity/LivingEntity | 事件主体。                  |
| target        | Entity，可为空          | 被攻击、被影响或被选择的目标。        |
| level         | Level               | 事件所在世界。                |
| position      | BlockPos，可为空        | 相关方块或事件位置。             |
| item          | ItemStack，可为空       | 相关物品。                  |
| block         | BlockState，可为空      | 相关方块。                  |
| damage_source | DamageSource，可为空    | 伤害来源。                  |
| formula       | FormulaContext      | NumberProvider 的计算上下文。 |

事件专属参数放入扩展字段，例如 damage、use_duration、equipment_slot、target_health、victim_health 和 distance。

后续可以增加 ContextKey<T>，把字符串键升级为类型安全的键，同时保留命名空间扩展能力，例如 mxt:damage 和 example:pill_id。

### TriggerSubscription

Subscription 表示一个实际监听者，至少包含：

| 字段        | 说明                 |
|-----------|--------------------|
| owner     | 所属实体、技能、资源或其他业务对象。 |
| trigger   | 数据包定义的 Trigger。    |
| gate      | 当前是否处于可受理状态。       |
| listener  | 匹配成功后的业务回调。        |
| lifecycle | 常驻、一次性或阶段性订阅。      |
| identity  | 稳定的业务标识，用于去重和重建。   |

建议状态：

- DISABLED：不接受事件。
- ARMED：正在等待并可以受理事件。
- CONSUMED：已触发，等待清理。

Dispatcher 只向 ARMED 订阅调用 listener。gate 可以检查修炼模式、当前境界、资源类型、冷却和其他业务状态。

## Dispatcher 的职责边界

Dispatcher 不需要知道“事件应该发送给技能还是修炼”。它只维护按信号类型索引的运行时订阅：

1. 接收 TriggerSignal。
2. 按 signal.type 找到候选订阅。
3. 调用 Trigger.matches(signal)。
4. 检查订阅的 gate 和生命周期状态。
5. 调用 listener。
6. 根据一次性策略标记或移除当前订阅。

业务模块负责订阅的存在：

- 技能被授予或装备时注册技能订阅，来源移除时撤销。
- 修炼达到突破阈值后注册突破订阅，突破成功、失败、退出修炼或切换目标时撤销。
- 外部模组可以注册自己的订阅，但不能绕过服务端权限和上下文校验。

## 技能触发

TriggeredAbilityType 可以从单个 trigger 扩展为 triggers 数组：

    {
      "type": "mxt:triggered",
      "triggers": [
        { "type": "mxt:item_use" },
        { "type": "mxt:breakthrough" }
      ],
      "chance": 0.5
    }

技能订阅通常是常驻订阅，但必须绑定技能来源。装备变化、Curios 槽位变化或能力撤销后，相关订阅必须同步移除。

现有技能事件可转换为：

    mxt:tick
    mxt:hurt
    mxt:attack
    mxt:kill
    mxt:death
    mxt:block_use
    mxt:block_break
    mxt:item_use
    mxt:equip
    mxt:breakthrough

## Cultivate 的阶段性触发

修炼突破不应让 Dispatcher 特殊处理，而应由 CultivationService 控制订阅生命周期：

    尚未达到突破经验
        -> 没有突破订阅

    达到突破最小经验
        -> 检查静态 conditions
        -> 条件满足后创建 ARMED 订阅

    收到 item_use 或自定义信号
        -> 仅当前资源和当前突破目标的订阅匹配
        -> 匹配成功后执行突破流程

    突破成功或失败、退出修炼、目标变化
        -> 删除突破订阅

同一个玩家可以同时拥有多个资源的等待状态。订阅身份至少包含 entity_uuid、module、resource_id 和 transition_id。

CultivateConditions 可以表达：

- conditions：立即检查的实体条件。
- triggers：达到阶段后等待的事件条件。
- action：满足触发后或主动突破入口执行的动作。

“服用丹药后才能突破”属于一次性触发状态，而不是要求两个事件同时发生。

## 即时触发与持久化触发

即时触发只检查当前上下文，处理完成后结束，例如受击触发护盾、攻击触发额外动作和方块破坏触发掉落。这类触发不需要保存等待状态。

持久化触发会改变业务状态，后续事件再根据状态判定，例如服用丹药后允许突破、完成事件后解锁技能和达到阶段后等待雷劫。这类系统只保存业务状态：

    cultivation transition = waiting_for_trigger
    required trigger id = example:pill_taken
    armed_at = game time

不保存运行时 TriggerSubscription 对象本身。

## 存档持久化设计

### 为什么不直接序列化 Subscription

TriggerSubscription 不建议直接写入实体或世界存档：

- 数据包重载后 Trigger 定义可能改变或被删除。
- 技能来源可能已经撤销，旧订阅会继续残留。
- Codec、类结构或上下文字段改变后，旧运行时对象无法安全反序列化。
- 订阅通常包含 listener、缓存引用和运行时索引，不适合作为持久化格式。

存档只保存业务状态，订阅属于可重新计算的派生缓存。

### 持久化对象与重建器

每个需要等待事件的模块保存自己的最小状态，并提供重建入口：

    Persistent state
        -> validate against current datapack registries
        -> Rehydrator
        -> create TriggerSubscription

| 重建器                          | 扫描内容                   |
|------------------------------|------------------------|
| Ability 模块重建器            | 当前实体已授予的 Ability 及其来源（由 `TriggerRehydrators` 统一调用）。 |
| CultivationTriggerRehydrator | 当前资源、境界目标和等待中的突破状态。    |
| ExternalTriggerRehydrator    | 其他模块保存的解锁或等待状态。        |

### 存档加载流程

1. 加载实体、世界和模块附件中的持久化业务状态。
2. 等待数据包注册表完成加载。
3. 清空旧的运行时订阅索引。
4. 扫描能力来源、修炼状态和其他模块状态。
5. 验证 Trigger、Ability、Resource 和 Realm 引用是否仍存在。
6. 对有效状态重新创建 ARMED 或 DISABLED 订阅。
7. 对无效状态执行迁移、降级或清理，并记录日志。
8. 完成重建后才允许发布普通游戏事件。

建议服务端世界加载后执行一次全量重建；实体延迟加载时，在实体加入世界或附件首次访问时执行该实体的重建。

### 数据包修改导致的无效状态

当数据包修改导致旧状态无法解析时，不应恢复半有效的订阅：

1. 记录实体、模块、资源和原始 ID。
2. 如果有迁移映射，迁移到新 ID。
3. 如果找不到定义，删除运行时订阅。
4. 修炼突破状态回退到安全阶段，例如保留当前经验但取消等待中的触发器。
5. 重新扫描技能来源；没有来源的技能直接撤销。

运行时订阅重建失败不应导致整个存档加载失败，除非持久化状态违反明确的数据完整性约束。

### 数据包重载

数据包重载后，所有运行时 Trigger 定义都可能变化，应执行：

    reload begin
        -> 暂停 TriggerDispatcher 发布
        -> 清空所有运行时订阅索引
        -> 重建当前实体和世界状态
        -> 删除失效引用并输出报告
        -> 恢复事件发布

重载期间产生的事件应延迟到重建完成后处理，不能发送给旧订阅对象。

### 去重与稳定身份

重建必须幂等，多次执行不会产生重复订阅。建议使用：

    owner + module + source + trigger id + transition id

Dispatcher 注册时按 identity 去重，撤销时按 identity 精确删除。不要使用运行时对象地址或列表下标作为持久化身份。

## 错误、取消与递归保护

- Trigger 匹配异常只影响当前订阅，并记录上下文和 Trigger ID。
- listener 执行异常不能破坏其他订阅，应捕获并记录日志。
- Pre 事件可以取消当前订阅的本次执行，不应取消全局信号。
- 一次性订阅只有在业务提交成功后才标记 CONSUMED。
- 同一实体、同一订阅递归发布相同信号时需要去重，防止动作触发自身造成无限递归。

## KubeJS 和外部扩展

KubeJS 事件应能够读取完整的 TriggerSignal 和 TriggerContext，包括 signalType、context、actor、target 以及扩展参数。

外部模块发布自定义信号时必须使用命名空间，例如 example:pill_taken 和 example:
quest_completed。需要跨重启的扩展应保存自己的业务状态，并在重建阶段重新注册订阅。

## 推荐包结构

    data/trigger/
        Trigger.java
        TriggerContext.java
        TriggerSignal.java
        TriggerSignals.java
        builtin/
            TickTrigger.java
            DamageTrigger.java
            ItemUseTrigger.java
            BreakthroughTrigger.java
    runtime/trigger/
        TriggerDispatcher.java
        TriggerSubscription.java
        TriggerRehydrator.java
        TriggerRehydrators.java
        TriggerLifecycleEvents.java

`TriggerSignals` 集中保存本模组内置信号的稳定 `Identifier`，事件桥不得再用散落的字符串拼接信号 ID。外部模块可以直接传入带命名空间的自定义 ID。

## 当前项目的迁移建议

1. `AbilityEventBridge` 通过 `TriggerSignal` 发布事件，技能订阅由新的 Dispatcher 处理。
2. `AbilityTriggeredEvent` 直接使用 `Holder<Ability>` 保存技能定义，并使用 `Identifier signalType` 与 `TriggerContext`，避免同时传递重复的技能 ID 和 definition。
3. `TriggeredAbilityType` 和 `CultivateConditions` 均使用 `triggers` 数组。
4. Ability 和 Cultivation 分别实现 `TriggerRehydrator`，由统一生命周期模块负责重建。
5. 后续可引入类型安全的 `ContextKey<T>`，并扩展 KubeJS 自定义信号发布 API。

最终，Dispatcher 不需要了解业务模块；模块只需在正确的生命周期阶段创建或撤销订阅。存档只保存可验证的业务状态，加载和数据包重载后通过扫描与重建避免旧订阅造成数据不一致。

## 当前实现状态（持久化审计）

当前代码已经采用“业务状态持久化、订阅运行时重建”的方案：

- `TriggerSubscription` 仅存在于 `TriggerDispatcher` 的内存索引中，没有 Codec，也不会写入实体或世界附件。
- Ability 模块从 `AbilityAttachment` 中保存的能力来源重新注册触发器；装备变化、Curios 同步、实体加入世界、服务端启动和数据包重载都会触发重建。
- Cultivation 模块只依据修炼附件、当前资源链和突破目标计算等待状态。进入修炼且达到突破阈值后才创建订阅；退出修炼、修炼失败、突破成功或目标变化时清理订阅。
- 数据包重载先清空 Dispatcher，再扫描所有已加载维度中的 LivingEntity，验证当前注册表定义后重新注册有效订阅。
- 实体离开世界和服务端停止时按 owner 清理，避免 UUID 复用或旧世界对象残留。
- `TriggerRehydrator`、`TriggerRehydrators` 和 `TriggerLifecycleEvents` 提供统一的模块注册及生命周期入口；业务模块不再各自监听启动、重载和实体生命周期事件。

### 持久化字段约束

需要跨重启的模块只保存以下类型的信息：稳定的资源/能力/境界 ID、当前阶段或等待状态、必要的时间戳和业务参数。禁止保存以下运行时对象：`TriggerSubscription`、listener/gate lambda、Dispatcher 索引、实体引用、注册表 Holder 的缓存实例。

### 重建器约定

每个模块提供一个幂等的 `rehydrate`（或等价入口）：先按模块清理旧订阅，再从附件和当前数据包注册表扫描并注册。遇到不存在或无法验证的 ID 时记录日志并丢弃该订阅；不得让单个失效订阅阻止存档加载。重建入口必须可重复调用，以支持数据包热重载和实体延迟加载。

后续新增模块时应遵循同一约定；若模块的等待状态需要迁移，应在业务附件中增加版本号和迁移逻辑，而不是尝试反序列化旧的订阅对象。
