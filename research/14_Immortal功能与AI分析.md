# Immortal 功能与 AI 对照分析

> 来源：`E:\Java\Immortal`，commit `fafda5d7efd4e7fded12ddd90e0b12ef428bae1a`，最新提交日期 2025-06-24。
>
> 目标项目：MiXianTu（Minecraft/NeoForge 26.1.2）。本文记录玩法与模块边界，不直接照搬 Immortal 的旧版 API。

## 总结

MiXianTu 已经可以作为修仙内容框架，支撑 Immortal 的大部分资源、灵气、境界、技能、炼丹、锻造和物品绑定内容。但在不新增框架模块的情况下，不能完整复现
Immortal 的全部玩法。

当前最明显的缺口是：实体元素附着与反应、法器刻印和触发器、分页秘籍、可编程傀儡、数据驱动修士 NPC，以及灵界专用规则。

Immortal 的具体数值和具体法术属于内容，不应并入 MiXianTu 框架；值得吸收的是状态模型、上下文设计和模块边界。

## 功能支撑矩阵

| Immortal 模块    | MiXianTu 支撑度 | 结论                                                                      |
|----------------|--------------|-------------------------------------------------------------------------|
| 灵气、修炼、境界、打坐、突破 | 高            | `resource`、线性 `realm_stage`、`aura_zone`、修炼模式和天劫可以覆盖单路线玩法。               |
| 多修炼路线与多来源修为    | 中            | 可以拆成多个资源和境界链，但不能直接表达多经验汇总与树状路线切换。                                       |
| 灵根             | 中            | 多个单元素灵根可以近似；单个灵根包含多个元素、随机权重和变异生成仍需扩展。                                   |
| 普通法术、冷却和消耗     | 高            | `ability`、`Cost`、Action/Condition 与服务端 hotbar 足够；复杂行为可追加 Action/KubeJS。 |
| 法术等级、法器刻印、物品触发 | 低            | “法术 + 等级 + 触发条件”的物品槽闭环尚未具备。                                             |
| 炼丹             | 高            | `alchemy_recipe` 与炼丹工作台覆盖核心流程；未知配方探索和爆炉可作为扩展。                           |
| 锻造             | 中高           | 锻造蓝图和会话覆盖核心；法宝类别、法术槽和精炼体系未完整接入。                                         |
| 分页秘籍           | 中            | 功法学习和能力授予已有；多页内容、逐页条件和书籍 GUI 未实现。                                       |
| 元素附着和元素反应      | 低            | `element` 目前主要提供关系和类型元数据，没有实体附着、衰减和反应结算器。                               |
| 符箓和刻印          | 低            | 有符纸等载体，缺少刻印数据、条件触发和专用 GUI 闭环。                                           |
| 傀儡与符文编程        | 低            | 没有可配置 AI、行为序列、过滤器组合和傀儡编辑闭环。                                             |
| 修士 NPC、交易、宗门   | 中            | 宗门与交易已有；NPC 档案、装备填充、法术配置和 AI 仍需扩展。                                      |
| 试炼、灵界、结构世界     | 中            | 天劫、`realm_instance` 和运行时维度服务可复用；灵界保护规则与生命周期联动仍需补。                       |

## 可借鉴设计

### 元素反应状态机

Immortal 的 `IMMEntityData` 为实体保存元素附着量、更新时间、候选反应和活动反应。tick 中按以下顺序工作：

1. 更新候选反应。
2. 检查反应 `match` 和优先级。
3. 执行 `doReaction`。
4. 按比例执行 `consume`。
5. 保存持续反应的活动强度。
6. 衰减元素附着量。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\capability\entity\IMMEntityData.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\api\spell\ElementReaction.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\ElementManager.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\ElementReactions.java`

MiXianTu 后续实现时，应把匹配、倍率、消耗和结果落到现有 `Condition`、`NumberProvider` 和 `Action`，避免每种反应都新增专用服务。

### 统一施法上下文

Immortal 的 `SpellCastContext` 统一携带施法者、法术等级、倍率、目标实体、目标方块、位置、朝向、使用物品、触发条件和原始事件。主动技能、物品刻印和实体
AI 因此可以共用技能实现。

源码：`E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\api\spell\SpellCastContext.java`。

MiXianTu 已有 Action、Condition、Cost 和 NumberProvider，但复杂技能的命中结果与触发来源仍应继续收敛到统一上下文。

### 法术实例与物品绑定

`SpellInstance` 将法术、等级和 `TriggerCondition` 存入物品，`SpellManager` 在实体事件中扫描并检查触发条件。这适合借鉴为灵宝/武器技能刻印模型，但不应与玩家已学习的普通
`ability` 状态混为一谈。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\codec\SpellInstance.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\SpellManager.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\menu\InscriptionTableMenu.java`

### 分页秘籍

`SecretManual` 由多个 `SecretScroll` 组成，每页通过 `ScrollContent` 分派内容，并支持页级学习条件、标题、描述和跳页控制。MiXianTu
的 `cultivation_technique` 适合保存功法状态与修炼倍率；秘籍阅读层应独立实现，或桥接外部书籍 UI。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\manual\SecretManual.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\manual\SecretScroll.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\impl\SecretManuals.java`

### NPC 档案分层

`HumanSetting` 将 NPC 的匹配条件、权重、背包填充、交易池和法术池分开，选择档案后分别填充各模块。这个设计比把完整 NPC
行为硬编码在实体类中更适合作为 MiXianTu 的 `creature_profile` 扩展方向。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\human\setting\HumanSetting.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\human\setting\trade\TradeSetting.java`

## 特殊 AI 核验

Immortal 确实存在特殊 AI，但不存在一个统一命名为 `special_ai` 的数据包注册表。源码中有三层不同实现。

### 1. 可编程傀儡 AI：真正的特殊系统

- `GolemEntity` 使用原版 `Brain<GolemEntity>`。
- 傀儡拥有行为符文槽和物品槽。
- `BehaviorRunes` 提供寻找目标、反击、近战、随机移动等行为节点。
- 每个行为节点声明所需的 Memory 状态和可用过滤器槽位。
- `FilterRuneTypes` 提供 `and`、`or`、`not`、`equal`、`less`、`greater` 等过滤组合。
- 修改符文后，`updateRuneInventory()` 会重建行为列表、Memory 列表和 Brain。
- `GolemBehavior#get(id, defaultValue)` 将过滤符文转换成行为使用的 Predicate。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\golem\GolemEntity.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\ai\behavior\golem\GolemBehavior.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\rune\behavior\BehaviorRunes.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\cultivation\rune\filter\FilterRuneTypes.java`

这是代码注册的简单注册表，行为实现和 Memory 需求仍在 Java 中，并不是完全由数据包生成的 AI 图。MiXianTu 可以借鉴“行为节点 +
条件过滤器 + 运行时重建”，但应优先用现有 Action/Condition 实现。

### 2. 修士和掠夺者的 Brain 活动状态机

`WanderingCultivatorAi` 和 `ChillagerAi` 使用核心、闲置、近战、远程和逃跑等 Activity，通过 Memory
条件切换状态，并组合使用技能、切换武器、保持距离和举盾等自定义 Behavior。

源码：

- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\human\cultivator\WanderingCultivatorAi.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\human\HumanLikeAi.java`
- `E:\Java\Immortal\neoforge\src\main\java\hungteen\imm\common\entity\human\pillager\ChillagerAi.java`

这属于专用实体 AI，不是通用数据包模块。MiXianTu 当前可以用 `creature_profile`、Action、Condition 和技能系统表达部分行为，但不能自动生成同等的
Brain 活动图。

### 3. 普通实体专用 Goal/Behavior

元素灵兽、炼气僵尸、草鱼等还使用普通原版 Goal 或自定义 Goal/Behavior。例如 `FireSpirit` 有专用攻击/移动/跳跃逻辑，`QiZombie`
有施法、攻击和举盾逻辑。这些属于具体内容，不应成为框架默认功能。

## 后续优先级

1. P0：实体元素附着附件、衰减、反应匹配和 Action 结果流水线。
2. P1：法器/灵宝技能实例、触发条件组件、等级和服务端冷却。
3. P1：秘籍分页内容模型，或与外部书籍 UI 的桥接。
4. P2：基于 Action/Condition 的可编程行为图，再按需适配 Brain。
5. P2：NPC 档案，使装备、交易、技能和权重可以独立配置。

## 不建议直接复用

- Immortal 的旧版注册、网络和 GUI API 不能直接作为 NeoForge 26+ 实现。
- `ElixirRoomBlockEntity` 含硬编码等级判断和未知配方爆炸逻辑，适合作为交互参考，不适合作为通用炼丹底层。
- 旧法宝锻造实现包含大量注释代码，移植前需要重新定义行为边界。
- 傀儡 Brain 直接依赖 `MemoryModule`，若框架直接采用会限制数据包扩展；应先抽象 Action/Condition，再按需适配 Brain。
