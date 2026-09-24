# 内容表对照基座的缺口清单

审计基准：当前工作区代码（NeoForge 26.1.2）。所有结论来自**直接读源码**，行号对应本文件写入时的状态。
对照物是本地分析工作区（不在本仓库内）的 `analysis/registries/` 下的注册表总表（1920 行 / 10 个类别）、
`变种汇总.csv`（208 族 / 19 轴）与 15 个模组的分模组分析文档。

> 变更记录
> - 新增本文件。按用户要求「按整理的内容表格对照基座，看基座还欠缺什么；具体内容不归基座，基座只管通用逻辑」。
    > 本文件只记**通用能力**的缺口：某个具体丹药、具体阵法、具体数值不给基座记一笔。

## 0. 判据与读法

一条内容需求算「基座缺口」，必须同时满足：

1. **它是通用的**——换个内容包仍然需要同一套机器（例：一个"能按等级门槛出产物的工位"）。
   具体物品 ID、具体数值、具体文本一律不算。
2. **基座要么没有，要么有定义但没有消费者**。后者一律记为「已建未接线」，与「根本没有」分开——
   两者的修法完全不同：前者是接线（小），后者是新增设施（大）。

每条缺口给四件事：**内容需要什么 → 基座现状（带 `文件:行号`）→ 缺什么 → 最小补法**。
补法注明落点：`Java`（必须新增代码）/ `数据包`（现有字段就够，只差写 JSON）/ `文档`（能力已有，只是没写清）。

---

## 1. 一页结论

基座的**内核是齐的**：33 个数据包注册表、4 类 Action / 5 类 Condition / `NumberProvider` + exp4j、
统一 `Ability`、灵气与资源、附件与同步、事件与 KubeJS 回调、25 个固有类型注册表都跑得起来，
而且对象之间是**引用而非硬编码**。按内容表逐类对照之后，欠缺集中在三处：

| 类别              | 条目数 | 性质                    | 典型                                                                  |
|-----------------|----:|-----------------------|---------------------------------------------------------------------|
| **A 已建未接线**     |  24 | 定义、Codec、字段都在，就是没有消费者 | 炼丹工作台（接口零实现）、`spirit_herb` 的 5 个字段、四个能力组件、`RuntimeDimensionService` |
| **B 根本没有的通用设施** |  15 | 换任何内容包都需要，基座里不存在对应概念  | 工序台、图鉴目录、物品右键路径、伤害管线、生成表                                            |
| **C 文档与代码不一致**  |  16 | 文档宣称「完成 / 已接入」，代码里没有  | `docs/模块实现审计.md` 的炼丹行                                               |
| **D 已确认的实现缺陷**  |   4 | 不是缺设施，是现有代码错了         | 灵材台灵气每 tick 被清、锻打输入槽 15 vs 12、`blank_talisman` 三套名字、品质三修正从不求值       |

> 另有一份同日的**子代理深度报告** `research/audit/生产系统审计.md`（608 行），只覆盖生产/制作五个系统，
> 结论与本文件一致并更细。本文件对其中**负载最重的 6 条**（两个缺陷 + 品质修正 + `blank_talisman` 命名 +
> `AlchemyRecipe` 双份 + 文档 32/34）做了独立复核，**6 条全部成立**，其余条目未逐条复核。

**最该先做的一件事**：A 类里的**炼丹工作台**。它是唯一一个「文档写完成、代码里连方块都没有」的模块，
而内容侧对应 9 级丹炉、192 个药材方块、28 张丹方、107 个丹药效果。
`alchemy_recipe` 的配方、会话、温度、炉阶、成败行为**全部已实现**，只差一个方块把它接上。

---

## 2. 变种轴 → 基座设施对照（19 轴 / 208 族 / 823 行）

`变种汇总.csv` 的 19 条轴逐条对照。**轴本身不是基座的事**（哪几个物品该合并是内容决策），
但「收敛之后用什么承载」是基座的事。

| 轴                            |         族/行 | 收敛后的形态                         | 基座现状                                                                                                                                                                                | 判定        |
|------------------------------|------------:|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| `年份档`                        |    44 / 105 | 1 物品 + 年份值                     | `spirit_herb` 有 `age` 字段，但**零消费者**（`SpiritHerb.java:18,23`，全仓无 `.age()` 调用）                                                                                                         | **A**     |
| `生长阶段`                       |    33 / 145 | 1 方块 + `age` blockstate + 按龄掉落 | 无任何作物/生长机制；`growth_rate`、`drop_chance` 同样零消费者                                                                                                                                       | **A + B** |
| `外观`                         |     23 / 66 | `item_model` / 实体 variant      | 原版+资源包已覆盖，基座不需要概念                                                                                                                                                                   | ✅ 不用做     |
| `图鉴分页`                       |    22 / 198 | 1 通用 Screen + (条目, 页码)         | **没有图鉴系统**。`InformationPanelScreen` 只显示玩家自身状态；`InformationManager.java:26-40` 是 Java `static` 注册，不吃数据包                                                                              | **B（最大）** |
| `等阶`                         |     21 / 84 | 1 注册项 + 等级值                    | 无通用"同族第 N 阶"。`SpiritStoneVein.Grade`（`runtime/world/SpiritStoneVein.java:34-51`）是**硬编码 6 级枚举**、只认 `mxt:spirit_stone_ore`，且只被 `/mxt aura vein` 诊断读（`command/AuraCommand.java:88-93`） | **B**     |
| `品阶`                         |     14 / 41 | 1 注册项 + 等级值                    | `item_quality` 已存在，可承担（`data/quality/ItemQuality.java`）。但三个修正只有 `value/forging/alchemy`，**没有"效果强度"**                                                                                | 部分 ✅      |
| `效果等级`                       |     13 / 47 | 1 `MobEffect` + `amplifier`    | 基座**不注册任何自定义 `MobEffect`**；`mxt:apply_effect` 只能引用原版（`data/action/builtin/entity/ApplyEffectAction.java:20,37`）。四个状态类能力组件零消费者（§5）                                                   | **B**     |
| `品相`                         |      6 / 17 | 1 物品 + 强度组件                    | 同「等阶」；`item_quality` 可勉强承担但语义是"品质"不是"强度档"                                                                                                                                           | **B（轻）**  |
| `等级`                         |      6 / 37 | 1 方块 + 等级值                     | `AlchemyRecipe.minimum_furnace_tier` 存在，但 `furnaceTier()` 是**接口方法**（`runtime/alchemy/AlchemyWorkstation.java:28`），等级由 Java 决定而非数据；且无实现者                                             | **A + B** |
| `结果状态`                       |      5 / 12 | 方块态 / 物品状态组件                   | 方块态原版已覆盖；物品状态无通用"状态组件"                                                                                                                                                              | **B（轻）**  |
| `命令参数`                       |      3 / 20 | 1 条命令 + 整型参数                   | 命令框架齐（`command/ServerCommandManager.java`，每类一条 `LiteralArgumentBuilder`）                                                                                                                  | ✅         |
| `部件`                         |       2 / 4 | 1 方块 + `part` blockstate       | 原版 blockstate                                                                                                                                                                       | ✅         |
| `结构变体`                       |       2 / 4 | 同一结构集                          | 见 §4.8（只有一处矿石 worldgen）                                                                                                                                                             | 见 §4.8    |
| `功能模式`                       |       1 / 4 | 1 方块 + `mode`                  | 基座做法是 2 个方块 + `StationMenu.Mode` 四态（`registry/MxtMenus.java:18-21`），已是"一类方块+模式"                                                                                                     | ✅         |
| `门派`                         |       1 / 6 | 1 物品 + 门派组件                    | 基座不再提供 `sect`（数据包注册表与运行时已整体删除）；`TokenComponent` 可带 `kind/value`（`data/item/TokenComponent.java:25`），内容包可据此自建门派标识                                                                    | **B（轻）**  |
| `群系变体`                       |       1 / 2 | 群系                             | 原版；`AuraZone.biomes` 可按群系 Holder 或标签（`data/aura/AuraZone.java:36`）                                                                                                                  | ✅         |
| `同族变种` / `(存疑)` / `效果编号(存疑)` | 11 族 / 29 行 | ——                             | 轴未定，属内容决策                                                                                                                                                                           | 待人工       |

**小结**：19 轴里 **5 轴已经能表达**（命令参数、部件、功能模式、群系变体、外观），
**5 轴卡在同一处**——基座没有「一个物品/方块带上一个通用档位值」这个概念。
把 §4.9 的「通用变体组件」补上，`年份档`、`等阶`、`品相`、`结果状态`、`等级`
合计 **82 族 / 235 行**一起解锁。

---

## 3. 分类注册表对照

### 3.1 物品 866 行

基座自己的物品只有 38 件（`registry/MxtItems.java:27-63`），其余由 KubeJS 注册——
这条边界是对的，`research/12` 也写明了。缺的不是"物品"，是**物品的行为承载方式**：

| 内容需要什么                                         | 基座现状                                                                                                                                                                                                                                                           | 判定    |
|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| 右键使用 → 条件 + 消耗 + 动作（46 张符箓、各种右键道具）             | `ItemBinding.actions` **只在 `LivingEntityUseItemEvent.Finish` 分发**（`runtime/item/ItemBindingService.java:79-82, 223-230`），即物品必须先带 `minecraft:consumable` 才会走到。唯一右键入口是 `weapon_binding.use_action`（`ItemBindingService.java:72-76, 208-218`），但它会同时套上武器攻击属性与品质组门槛 | **B** |
| 使用门槛失败时不消耗、并说明原因                               | `ItemQualityService` 以 `HIGHEST` 取消 `RightClickItem`/`RightClickBlock`/`Attack`/`UseItemStart`/`UseItemTick` 并发具名原因（`runtime/item/ItemQualityService.java:44-87, 118-125`）——**这半条已经有了**，缺的是把它接到通用 `item_binding` 上                                             | 部分 ✅  |
| 消耗耐久而不消耗本体（符笔、法宝）                              | `mxt:damage_item`（`data/action/builtin/item/DamageItemAction.java`）、`DurabilityCondition`、`RelativeDurabilityCondition` 都有                                                                                                                                     | ✅     |
| 配方产物带组件（品质/变体/状态）                              | `SpiritRecipe.result` 是 `ItemStackTemplate`（`recipe/SpiritRecipe.java:21`）✅；但 `AlchemyRecipe.success_outputs` 是 `List<Identifier>`（`data/alchemy/AlchemyRecipe.java:23,34`）→ **炼丹产物不能带组件**                                                                     | **A** |
| 通用的"变体 / 档位 / 年份 / 外观"值                        | `MxtDataComponents` 21 个组件（`registry/MxtDataComponents.java:29-49`）里**没有**任何一个通用档位值                                                                                                                                                                            | **B** |
| 按 `ItemMatcher` 匹配的一串描述行（内容侧 501 行硬编码 tooltip） | 已有 7 个 tooltip 追加器，但全是 Java 实现、按子系统写死；`item_binding` **没有 `description` 字段**                                                                                                                                                                                   | **B** |

### 3.2 方块 419 行

| 内容需要什么                        | 基座现状                                                                                                        | 判定       |
|-------------------------------|-------------------------------------------------------------------------------------------------------------|----------|
| 可交互方块开界面                      | 6 个方块各自 `useWithoutItem` + `openMenu`（`item/block/*.java`），共 9 个 `MenuType`（`registry/MxtMenus.java:16-24`） | ✅（模式可复用） |
| 带等级的方块（9 级丹炉 / 9 级炼器鼎 / 坊市模式） | 无通用"方块等级"概念；基座做法是给每个等级注册一个方块，或做成独立方块                                                                        | **B（轻）** |
| 作物方块（33 族 / 145 行生长阶段）        | 无                                                                                                           | **B**    |
| 部件/结果状态 blockstate            | 原版 blockstate                                                                                               | ✅        |
| 方块被灵气灌注（展示架已实现）               | `DisplayStandBlockEntity.add` + `AuraItemAccess.onCharged`（见 `research/audit/spirit.md` §3.2）               | ✅        |

### 3.3 GUI 248 行

| 分组                                   |          行数 | 基座现状                                                                                      |
|--------------------------------------|------------:|-------------------------------------------------------------------------------------------|
| 图鉴类（功法图鉴 61 + 法宝图鉴 37 + 器路/丹方/物品描述等） |        ~198 | **无**。基座只有 9 个按功能写死的容器界面                                                                  |
| 工位类（炼丹 33 + 炼器 27 + 符阵 33）           | ~93（与图鉴有重叠） | 只有锻造台、灵材合成台两个工位界面                                                                         |
| 状态/面板类                               |         ~20 | `TechniquePanelScreen`（功法列表）、`InformationPanelScreen`（人物信息）、`HotbarConfigurationScreen` ✅ |

基座对"目录"的现状被自己一句话说清：`ItemQualityService.java:35` 把 `item_quality` 叫 `catalogue`，
但它只是一个可排序标签集合，**没有界面**。

### 3.4 实体 151 行 / 药水效果 107 行

基座只注册 4 个实体类型，全部是技术实体：`flying_sword`、`soul`、`spirit_burst`、`colored_lightning`
（`registry/MxtEntityTypes.java:17-20`）——**没有一个是怪**。自定义怪需要 Java（或 KubeJS 注册）是既定边界，
`research/12` 已写明"GeckoLib 负责模型动画"。真正缺的是**生成**与**状态**，见 §4.5 / §4.3。

### 3.5 命令 76 行 / 结构 33 行 / 群系 8 行 / 维度 2 行

- **命令**：76 条里 66 条没有中文名，多为 MCreator 的方向/调试命令。基座的命令框架（§2 `命令参数`）够用，
  **不算缺口**。
- **结构 / 群系 / 维度**：见 §4.8。

---

## 4. 系统级缺口

### 4.1 生产三台：炼丹、炼器、制符（最优先）

**内容需要什么**：三台机器，形态高度相似——
多材料槽 + 配方匹配 + **工位等级门槛** + **时长/温度/火候** + **异火这类外部等级输入** +
**成功率** + **熟练度增长** + **失败产物** + **产物带品质**。
炼丹 9 级炉、炼器 9 级鼎 + 异火 0..9 + 炼器/炼材模式、制符台 6 槽 + 符笔等级 + 成功率。

**基座现状**：

| 零件            | 现状                                                                                                                                                                                                                      | 证据                                              |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| 配方模型          | `AlchemyRecipe` 有 `inputs` / `target_temperature` / `temperature_tolerance` / `minimum_furnace_tier` / `duration` / `minimum_aura` / `success_outputs` / `failure_outputs` / 四个成败 Action（`aura_kinds` 已于 2026-09-17 删除） | `data/alchemy/AlchemyRecipe.java:20-25`         |
| 会话与结算         | `AlchemySession` + `AlchemyWorkstationState` + `AlchemyWorkstationService`（材料锁定、计时、结算、扣灵气）                                                                                                                              | `runtime/alchemy/*`                             |
| 工作台契约         | `AlchemyWorkstation` 接口：`alchemyState()` / `furnaceTier()` / `temperature()` / `setChanged()`                                                                                                                           | `runtime/alchemy/AlchemyWorkstation.java:25-32` |
| **方块**        | **无**。全仓 `implements AlchemyWorkstation` **零命中**                                                                                                                                                                        | grep 结果                                         |
| **方块实体**      | 5 个，全是交易站/展示架/灵材台/锻造台                                                                                                                                                                                                   | `registry/MxtBlockEntities.java:17-21`          |
| **菜单 / 界面**   | 9 个 `MenuType` 里没有炼丹                                                                                                                                                                                                    | `registry/MxtMenus.java:16-24`                  |
| **默认数据 / 资源** | `src/main/resources` 里没有丹炉的方块态、模型、贴图、配方                                                                                                                                                                                 | 资源树                                             |

**一条很说明问题的旁证**：基座**自己一次都没有跑过这四套系统**。
全仓（含 `run/kubejs`）**没有任何** `mxt:alchemy` / `mxt:spirit_shaped` / `mxt:spirit_shapeless` /
`mxt:refining` / `mxt:formation` 的配方 JSON；`data/mxt/mxt/` 只有 block_aura / cultivation / currency /
element / item_aura / resource 六类。也就是说"炼丹完成"这句话从来没有被运行验证过——
测试包给了 forging / blueprint / tool / item_quality / talisman / spirit_herb 的**定义**夹具，
但**没有任何物品引用 `tool_binding` / `blueprint_binding`**，也没有一条炼丹或灵材配方。

**缺什么**（按重要性）：

1. **一个通用的工序台方块 + 方块实体 + 菜单 + 界面**，把 `AlchemyWorkstation` 实现出来。
   等级不该是"9 个方块类"，而应是方块态或方块实体数据（配 `minimum_furnace_tier`）。
2. **工位等级与外部输入进数据**：`furnaceTier()` 现在是接口方法（Java 决定）。
   炼器的"异火等级从下方方块读"这类**相邻方块提供的等级输入**在基座里没有概念。
3. **成功率与熟练度**：`AlchemyRecipe` 没有 `success_chance` 一类字段，
   也没有"这次炼制给哪个 resource 加多少"的字段（内容侧三套并存的炼丹经验机制）。见 §4.10。
4. **产物带组件**：`success_outputs` 是 `List<Identifier>`，应迁到 `ItemStackTemplate`
   （`SpiritRecipe` 已经这么做了，见 `recipe/SpiritRecipe.java:21`）。
5. **配方类别/模式切换**：炼器的 `炼器` / `炼材` 双模式、制符台的 `类型` 循环选择，
   基座没有"同一工位按模式选不同配方表"的概念。
6. **制符的铭刻服务**：文档自认未接入（`docs/模块实现审计.md:126`，`research/audit/talisman.md` §6）。
   注意基座的 `talisman` 是**灌注—激发**语义（灌满 aura 即发动、一用即焚），
   与内容的「符箓 = 有灵力门槛的一次性法术」**不是同一个设计**——这一条需要拍板，见 §8。

**最小补法（Java）**：一台 `mxt:process_table`（方块态 `tier`），复用现有 `AlchemySession` /
`ForgingSession` 那套"锁材料 → 计时 → 结算"；`AlchemyRecipe` 补 5 个可选字段
（`success_chance` / `mastery_resource` + `mastery_gain` / `catalyst_slot` / `mode`），
产物迁 `ItemStackTemplate`。**内容侧 28 张丹方、9 级炉、9 级鼎都能因此落地，不需要再写 Java。**

### 4.2 战斗与伤害管线

**内容需要什么**：整套 `物攻 / 法攻 / 物防 / 法防 / 火攻 / 雷攻 / 毒攻 / 火防 / 雷防 / 毒防`
（`analysis/16-共享数据契约.md` §3.2，跨 9 个模组、上千次读写），以及"谁打的 / 什么类型 / 多少级"。

**基座现状（2026-09-20 起本节已闭环，下面保留原始判断，改动见文末「已闭环」段）**：

- `mxt:damage` 与 `mxt:damage_target` 都只发 `damageSources().generic()`
  （`data/action/builtin/entity/DamageAction.java`、`builtin/bientity/DamageTargetBiEntityAction.java`），
  **causing entity 为 null，伤害不属于施法者**；`DamageAction` 的类注释明确写着
  "source-specific damage belongs in a dedicated code-owned action type"。
- **唯一的例外是阵法**：`AttackFormationAction` 有 `damage_type` 与 `attribute_to_owner`（默认 true），
  `FormationActionRunner.java:85-95` 会造 `playerAttack`/`mobAttack`
  （`data/formation/AttackFormationAction.java:23-32`）。
  也就是说**基座已经有"带类型、带归属的伤害"这一套，只是长在阵法模块里**，通用动作没有。
- 全仓**没有** `LivingIncomingDamageEvent` 拦截，只有 `LivingDamageEvent.Post` 被动旁观
  （`runtime/ability/AbilityEventBridge.java:94`、`runtime/cultivation/CultivationActionEventBridge.java:57`）。
- `mxt:hurt` 信号确实把 `DamageSource` 塞进了 `TriggerContext`（`data/trigger/TriggerContext.java:58`），
  但 **`damageSource()` 在 `src/main` 零读取点**——条件和公式变量都拿不到攻击者。
- `SkillStage.damageMultiplier` 零消费者，代码里已有 `//TODO::Consume damage_multiplier once a damage pipeline exists`
  （`data/cultivation/SkillStage.java:22`）；`research/audit/technique.md` 的 T10 也记着。
  注意 `technique` 与 `realm_stage` **都没有** `damage_multiplier` 字段
  （功法只有 `cultivationModifier`，`data/cultivation/Technique.java:33`）。
- `FormulaContext` 的 `caster_*` / `target_*` 前缀**已经能读属性和资源**
  （`registry/MxtFormulaVariables.java:99-122`），所以公式侧是够的——缺的是**管线**。
- 唯一现成的缩放项是**元素亲和**：`AbilityService.java:483-488` 会产出一个 `element_modifier` 公式值，
  内容可以把它手写进 `mxt:damage` 的 `amount` 里；**境界/技能等级没有对应项**。
- 伤害条件族（`data/condition/builtin/damage/*`，9 个）**只作用于"受伤判定"**
  （挂在 `LivingDamageEvent.Post` 上，`AbilityEventBridge.java:93-102`），**不参与造成伤害**。

**缺什么**：① 一次伤害从"谁、用什么、打在谁身上"到"实际扣多少"的公共路径；
② 一个通用的带 `damage_type` + attacker 的动作（把阵法那套提出来）；
③ 让条件/公式读得到 `DamageSource`。
`research/audit/formation.md` §6 已把它列为"总纲 D1（统一伤害管线）"。

**最小补法（Java）**：新增 `mxt:typed_damage`（`amount` + `damage_type` + `attacker` 来源），
照抄 `AttackFormationAction` + `FormationActionRunner` 的落点；
在一个 `LivingIncomingDamageEvent` 监听里按施法者水平/属性重算。
`mxt:damage` 保留为"环境伤害"语义。

**已闭环（2026-09-20，实际做法与上面的"最小补法"有出入，以下为准）**：

- 新增 `runtime/damage/DamageCalculationService`，把一次伤害分成**两层**：
  **第一层出力**在发伤害处（攻击方一侧）乘 `damage_multiplier` 与攻击者灵根对目标灵根的
  `overcomes[].multiplier`；**第二层减免**在 `runtime/damage/DamageEventBridge` 的
  `LivingIncomingDamageEvent` 里乘受击者灵根的 `adapted_to[].multiplier`。
  为什么这样切：第一层需要"谁、用什么、打谁"三件事同时在场（只有发伤害处有），
  第二层需要看到**所有**打过来的伤害（包括别的模组与原版的），而"适应什么"本来就是受击者的属性；
  两层不会重复——那个事件每次伤害序列只触发一次。
- **没有**新增 `mxt:typed_damage` 动作类型。`damage_type` 由各条发伤害路径自带
  （阵法 `AttackFormationAction` 本来就有），通用动作只补归属：`DamageTargetBiEntityAction` 从此把
  施加者记为加害者，`DamageAction` 只在"打的对象不是施法者自己"时记归属（反噬/丹毒这类保留无主语义）。
  来源统一在 `DamageCalculationService.source(...)` 里构造：有 `damage_type` 就用它并把加害者记为起因，
  没有就按原版 `playerAttack`/`mobAttack`/`generic` 选择——阵法原来那段私有实现已经删掉。
- `mxt:explode`（实体行为）也收进来了：每次单体爆炸伤害过第一层，爆源记为施法者。
  `mxt:lightning` 与方块爆炸仍由原版结算，只受第二层影响（没有可归的攻击者）。
- 元素关系从"只有谁克谁"变成"每条关系自带伤害倍率"：
  `Element` 的 `overcomes` / `adapted_to` 现在是 `{elements, multiplier}` 数组，
  加载期校验有限非负；`mxt:element_overcomes` 条件改读"关系存不存在"（`Element#overcomes`）。
  一个实体多条灵根时所有成立的关系相乘。
- `SkillStage.damageMultiplier` 的 TODO 与其类注释一起撤掉：施放能力时 `AbilityService` 把
  "授予该能力、且施法者当前所在的那一级"的倍率写进公式上下文（`damage_multiplier`，
  多个功法都授予同一能力时取最高），管线第一层读它。`research/audit/technique.md` 的 T10 同步标记为已闭环。
- 公式变量文档、`docs/数据包格式.md` 的 `element` / `skill_stage` 两节与新增的「伤害结算」一节、
  `docs/模块实现审计.md` 的元素/功法/技能水平三行都已同步。
- 实机验证（test-mod `/mxt_test damage`，两个单灵根探针实体 + 一次真实 `AbilityService.useCarried`）：
  元素层 `10 × 1.5 = 15.0`、减免后 `7.5`、实际掉血 `7.5`；技能层 `1.1 → 1.25`、实际掉血 `3.75`、
  施放上下文里读到 `1.25`；原版来源（`mobAttack`）的 4 点伤害只吃减免 → 掉血 `2.0`。
  仍缺的：伤害类型与元素之间没有映射（元素只从双方灵根读，不从 `damage_type` 反推）。

### 4.3 状态与效果（107 个药水效果）

**内容需要什么**：妖兽/秘境的 I~V 级状态、丹药 buff（魔修四档、鹰枭攻击 2~5 级）、
需要显示图标与层数的限时状态。

**基座现状**：

| 可能承担的东西         | 现状                                                                                                                                                              |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 自定义 `MobEffect` | **基座注册 0 个**；`ApplyEffectAction` 收原版 `MobEffect`                                                                                                                |
| `curse`         | 有 `duration_ticks` / `tick_interval` / `max_stacks` / `StackingMode` / 施加·周期·到期·解毒 Action（`data/curse/Curse.java`）——**形态上最接近"限时命名状态"**，但语义被限定为负面、可解毒，且没有图标、不改属性 |
| 四个能力组件          | `toggle` / `timer` / `resource` / `target_lock` **注册了但零消费者**（见 §5）                                                                                              |

**缺什么**：一个中立的"限时命名状态"（名字 + 图标 + 层数 + 起止条件 + 属性修正 + 可被条件读取）。
现在只能用 `curse` 冒充，或退回原版效果。

**最小补法**：两条路，需拍板 —— ① 把 `curse` 的中立字段补齐（`icon`、`AttributeEntry[]`），
语义从"诅咒"泛化成"状态"（`cleanse_tags` 已于 2026-09-19 删除，解毒改由解毒剂 + `mxt:curse` 标签管理）；② 新增 `mxt:status`
注册表。
路 ① 改动小但要动既有语义，路 ② 干净但多一张表。

### 4.4 灵植与作物（`年份档` + `生长阶段` = 77 族 / 250 行）

**基座现状**：`SpiritHerb` 有 7 个字段，**只有 2 个有消费者**：

| 字段              | 消费者                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------|
| `items`         | `SpiritHerbService.find` → `ItemQualityService.find`（`runtime/item/ItemQualityService.java:254,261`）✅ |
| `quality`       | 同上 ✅                                                                                                  |
| `age`           | **无**                                                                                                 |
| `element_tags`  | `mxt:herb_tag` 的 `element`（2026-09-19 接线：任何接受 `ItemMatcher` 的地方都能问"任意火属性灵草"）✅                         |
| `material_tags` | `mxt:herb_tag` 的 `material`（同上）✅                                                                      |
| `growth_rate`   | **无**（等种植/生长系统）                                                                                       |
| `drop_chance`   | **无**（等种植/生长系统）                                                                                       |

（`SpiritHerb.java:14-17` 的类注释明确说它**不为数据包条目注册物品**——这条边界是对的。）

同时 `AuraZone.Rules` 的三个灵气—灵植联动字段：`alchemy_env_bonus` **已于 2026-09-19 接上**（该区域内的丹药配方视为满足
`minimum_aura`，见 `runtime/alchemy/AlchemyWorkstationService.java`），`spirit_plant_bonus` 与 `natural_spawn_herb` 仍*
*零消费者**（`data/aura/AuraZone.java:140-149`；`docs/灵气环境数据包.md:95` 自己也写了"尚未接入消费者"）。
对照之下同一 `Rules` 里的另两个**已经接了**：`cultivate_suppress`（`AuraResult.java:38-39`、
`CultivationActionService.java:302`）、`tribulation_modify`（`TribulationService.java:25-26,85`）。

**缺什么**：① 种植/生长/采集生命周期（作物方块、`age` blockstate、按龄掉落、灵气加成生效）；
② `age` 作为一个可读的档位值（百年/千年）——这一条由 §4.9 的通用变体组件解决。

**最小补法（Java）**：一个 `mxt:spirit_crop` 方块 + `SpiritHerb.age/growth_rate/drop_chance`
的消费者（`element_tags`/`material_tags` 已由 `mxt:herb_tag` 消费）；`AuraZone.Rules` 剩两项随同一套系统接进去。

### 4.5 妖兽生成（151 个实体）

**内容需要什么**（`analysis/05-妖兽-yvanchuyaoshou/07-生成与刷新.md`）：
9 张**按群系/维度分表**的生成表，每张表逐条 `条件 + 概率`（后面分支拿剩余概率），
落点计算（玩家半径 8..32 环带、向下找地面、头顶 3 格空、露天/黑暗判定）、
**每玩家怪物上限**（记分板 `SumLimit`）、生成计时器、**诱妖草**这类加成物品、清除机制。

**基座现状**：

- `CreatureProfile` 的 9 个字段里**没有任何生成相关字段**
  （`realm_stages` / `intelligence` / `condition` / `inner_core` / `loot_table` / `contract_tags` /
  `entity_type_tags` / `preferred_aura_elements` / `minimum_aura`，`data/creature/CreatureProfile.java:28-33`）。
  **2026-09-25 更新**：`realm_stages` 已删除，换成一个 `spawn_action`（档案写入时跑一次的行为）——它仍**不是**
  生成规则，下面那条"缺一张生成规则注册表"的结论不变。
- `CreatureProfileService`（`:28-68`）只做"给**已经生成**的 Mob 套档"，
  按实体类型/标签选档 + 条件 + `minimum_aura` + `preferred_aura_elements` 门禁，然后写 `CREATURE_SPIRIT` 附件。
- main 源码里 `SpawnPlacement` / `FinalizeSpawn` / `MobSpawn` / `SpawnEvent` / `Spawner` **零命中**。
- `CreatureProfile.realmStages` 本身也零消费者（**已于 2026-09-25 关闭**：该字段整体删除，换成有消费者的
  `spawn_action`——档案写进生物时执行一次，见 `research/47` §4.1 的字段再修订）。

**缺什么**：一张**生成规则注册表**（`entity` + `biome/dimension` + `condition` + 权重/概率 + `y` 范围 + 光照）
与一个生成调度器（每玩家/每区块上限、计时、清除）。这是"通用逻辑"，具体出什么怪由内容写。

**最小补法**：优先**零代码**——NeoForge `neoforge:add_spawns` 这个 biome modifier 目录
基座自己就在用（`data/mxt/neoforge/biome_modifier/add_spirit_stone_ore.json`），
能做到群系/权重/高度；缺的只是文档与示例（§4.8）。
若要"按灵气浓度生成"或"每玩家上限/诱饵加成"，再加一个监听
`MobSpawnEvent.PositionCheck` / `FinalizeSpawnEvent` 的 bridge，
内部复用 `CreatureProfileService.apply` + `AuraService.getPositionAura`（约 40 行），无需新注册表。

### 4.6 图鉴与描述（~198 行 GUI + 501 行 tooltip）

**基座现状**：

- **没有目录/图鉴**。`InformationManager.java:26-40` 是一张 Java `static` 注册表，
  只收"玩家自身状态"的读取器（`Side.BASIC` / `Side.CULTIVATION`），**不接受数据包条目**。
- 已有可复用零件：`IconReference`（`data/IconReference.java`，贴图或 `ItemStackTemplate`）、
  `IconRenderer`（`render/IconRenderer.java`，全仓唯一的图标→像素点）、
  `DescribedEntry<T>`（`data/DescribedEntry.java`，值 + 说明文本）。
- 物品描述：7 个 tooltip 追加器，全部按子系统 Java 实现；没有"按 `ItemMatcher` 匹配的一串描述行"这种数据表。

**缺什么**：一张 `mxt:catalog_entry` 之类的注册表（`icon` + `title` + `description` + `category` + `page` + `condition`）
加一个通用分页 Screen。这是**单条收益最大**的缺口：198 行 GUI 里 61 个功法图鉴 + 37 个法宝图鉴

+ 丹方/器路/物品描述全部是它的实例。

**最小补法（Java）**：一个注册表 + 一个 Screen（`IconReference` 与 `DescribedEntry` 已经现成），
外加一个通用的"物品描述"表（可直接作为 `catalog_entry` 的一种，或给 `item_binding` 加 `description`）。

### 4.7 储物（`12-储物袋` + 法宝储物）

**基座现状**：`ArtifactStorageComponent`（`List<ItemStack>`）、`ArtifactStorageService`、
`ISpiritStorage`、`ItemArchetype` 的储物能力 **都在，但**（`ItemArchetype` 已于 2026-09-21 改名为 `Artifact`
，本文其余处保留当时的名字与行号）：

- **没有任何物品使用它**。`MxtItems.SPIRIT_STONE_BAG` 是空壳 `Item::new`（`registry/MxtItems.java:42`）。
- **没有任何菜单/界面**。`MxtMenus` 9 个里没有储物。
- `ArtifactStateComponent.nourishment` **已于 2026-09-19 接上**：灌能时按"真正收下 ÷ 本次有效容量"上涨（`0..1`
  、只升不降），并作为容量加成参与法器上限（`runtime/artifact/ArtifactService.java`）。
- **2026-09-21**：槽位声明从 `ItemArchetype.storage_slots` 挪进 `abilities` 里的 `mxt:storage` 条目，`ISpiritStorage`
  三处签名改为带 `HolderLookup.Provider`（定义要在客户端也读得到）。界面仍然没有。

**缺什么**：一个通用储物容器物品 + 界面（槽数来自 `mxt:storage` 条目，即数据驱动）。
**最小补法**：新增一个 `SpiritStorageItem` + `MenuType`，复用 `ArtifactStorageService`（容量计算已写好）。

### 4.8 世界内容（结构 33 / 群系 8 / 维度 2 / 灵脉 9 阶）

**基座现状**（本节逐项已对着代码核过）：

| 项     | 现状                                                                                                                                                                                                                                                                                                                                                                                     |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 世界生成  | **只有一处，且只服务基座自己硬编码的矿石**：`data/mxt/neoforge/biome_modifier/add_spirit_stone_ore.json`（`add_features`）+ `worldgen/configured_feature/spirit_stone_ore.json` + `worldgen/placed_feature/spirit_stone_ore.json`，方块硬编码在 `MxtBlocks.java:28`。Java 侧**零世界生成代码**（`BiomeModifier`/`StructureSet`/`TemplatePool`/`ProcessorList`/`DimensionType`/`StructureType`/`StructurePiece` 在 main 中 0 命中） |
| 结构    | **只有"只读匹配"，没有生成**。`FormationStructureValidator.java:30-83` 读 `level.getStructureManager().get(template)` 逐格比对；`Formation.java:46,49,81-88` 强制 `structure_template` 与 `structure` 恰好二选一。全仓**没有任何 `.nbt` 文件**，没有 `data/*/structure` 目录                                                                                                                                                   |
| 战利品   | 完备。原版 loot table 全可用 + 基座 6 个条件类型（`MxtLootConditions.java:15-20`）与 4 个函数类型（`MxtLootFunctions.java:18-21`）+ KubeJS 注册口                                                                                                                                                                                                                                                                  |
| 群系    | 不注册，只消费。`AuraZone.biomes` 支持 Holder 或 `#tag`（`AuraZone.java:36,46`；`AuraService.java:158-168`；`RegistryCodecs.java:26-35`）。条件侧有 `BiomeTagBlockCondition`                                                                                                                                                                                                                               |
| 维度    | 定义走原版；灵气侧 `AuraZone.dimensions` 用 `LevelStem` key/tag。`RealmInstanceService.destination()` = `server.getLevel(...)`（`:134-136`），**只查已加载维度**；维度缺失即 `MISSING_DIMENSION`（`:41-42`）；入口坐标硬编码 `0.5, getHeight(MOTION_BLOCKING_NO_LEAVES,0,0), 0.5`（`:50-53`）                                                                                                                                 |
| 维度装载器 | `RuntimeDimensionService.java:31-81` 装/卸都写好了，但**全仓零调用者**（无命令、无 KubeJS、无内部调用）                                                                                                                                                                                                                                                                                                           |
| 区域灵气  | `AuraZone` 的粒度是"整维度 / 整群系 + 噪声"；**有界区域只能运行时经 KubeJS 写入** level 附件（`MxtKubeJsApi.java:147-151`、`AuraWorldAttachment.java:19-74`），**无数据包格式、无命令**                                                                                                                                                                                                                                         |
| 灵脉    | `SpiritStoneVein` 是**纯测量器**：方块硬编码（`:22,28`）、阈值硬编码 6 级（`:34-51`），唯一消费者是 `/mxt aura vein`。它**不生成任何东西**                                                                                                                                                                                                                                                                                   |

**缺什么**：

1. **世界生成扩展点的文档与示例是零**。`docs/` 全库 grep
   `add_features|biome_modifier|configured_feature|placed_feature|add_spawns|structure_set|template_pool|dimension_type`
   **0 命中**——基座自己在用这条路做矿石，却从没告诉内容包这条路。
   内容侧 33 条结构、8 个群系、2 个维度全部要靠它。
2. **数据驱动区域灵气**：`aura_zone` 没有 `areas`/`shape` 字段，没有区域注册表。
3. **灵脉不是数据**：方块、等级阈值、判定都写死，且无玩法消费者。
4. **秘境不驱动维度装载**（`docs/模块实现审计.md:93` 的说法经代码核对**仍然成立**）；
   `realm_instance` 没有入口坐标字段。

**最小补法**：

- 1 是 **文档 + JSON 骨架，0 行 Java**；
- 2 在 `AuraZone.CORE_CODEC`（`AuraZone.java:42-52`）加可选 `areas` 并在 `AuraService.staticZone`（`:152-174`）匹配，
  或新增 `mxt:aura_region` 注册表（`MxtResourceKeys.java:114` 后 + `MxtDatapackRegistries.java:85-90` 各一行）；
- 3 把方块改成 `#mxt:spirit_vein` 标签、阈值挪进数据，约 20 行 + 1 个标签；
- 4 在 `RealmInstanceService.destination()` miss 时调 `RuntimeDimensionService.load`，`expire/exit` 后 `unload`（约 5 行），
  再给 `RealmInstance` 加可选 `entry`（position / heightmap / yaw）。

### 4.9 通用档位/变体值（一次解锁 5 条轴 / 82 族 / 235 行）

`MxtDataComponents` 的 21 个组件里没有任何一个能表达"这是同一族的第 N 档"。
内容侧需要它的是：`年份档`（44/105）、`等阶`（21/84）、`品相`（6/17）、
`结果状态`（5/12）、`等级`（6/37）。

**最小补法（Java）**：一个 `mxt:variant` 组件（`Identifier family` + `int tier`，或直接 `Holder<...>` 指向定义），
附带 ① tooltip 追加器（显示"三阶 / 百年"）、② 一个 `mxt:variant_is` 条件、
③ 一条公式读取路径。`family` 直接用现有注册表 ID（如 `mxt:spirit_herb/yuan_ci_cao`），
不必新增注册表。

### 4.10 通用等级 / 熟练度链

**内容需要什么**：`画符等级`（符阵）、`炼丹等级`（炼丹）、`炼器等级`（炼器）、功法层数 ——
四个"累加经验 → 过阈值 → 升级 → 解锁/加成"的阶梯。这些**不是功法**。

**基座现状**：

- `skill_stage` 的**数据结构本身是通用的**：链身份 `skill` 是自由 `Identifier`，
  `ServerCache.rebuildSkillChains`（`runtime/ServerCache.java:204-256`）只沿 `next_stage` 推 rank，不认识功法。
- 但**全部消费者都绑在功法上**：`Technique.default_stage` / `mastery_resource` / `configuration`
  （`data/cultivation/Technique.java:35-37,47-50`）→
  `SkillStageService`（`:37-84`）/ `TechniqueMasteryService`（`:40-75`）/
  `CultivationGrantService`（`:50-52`）/ `TechniqueProgress` / `TechniquePanelScreen`。
- 状态槽的键是 `Holder<Technique>`（`attachment/SpiritIdentityAttachment.java:29,35`），
  所以**独立技能没有状态、没有晋升器**：数据包今天能定义一条画符等级链（不会被缓存拒绝），
  但玩家**永远停在入口级**。
- 另外四件事也缺：
    - `skill_stage` 的**比较条件**不存在（`MxtEntityConditions` 里没有；境界侧有
      `mxt:realm` 的 EXACT/AT_LEAST/AT_MOST 与 `mxt:has_realm` 可照抄，`RealmEntityCondition.java:24-29,57-66`）。
    - **公式变量**里没有阶段 rank（`MxtFormulaVariables.RealmVariable:146-166` 只在有 `ResourceSubject` 时给境界）。
    - **通用"累加 → 阈值 → 升级"原语**不存在。现成的是**三套 bespoke**：
      境界（`CultivationService.addProgress:165-207` + `threshold:283-297` + `commit:116-144`）、
      功法熟练度（`TechniqueMasteryService.promote:56-75`）、丹药毒性（`PillService.java:20-27`）。
    - **没有"写等级"的动作**：`MxtEntityActions` 里没有 set-skill-stage 一类动作，
      `CultivationService.setRealm:271-281` 只作用于境界且只被 `/mxt` 管理命令调用（`MxtCommand.java:277-280`）。
      后果很具体：**「画符经验 ≥ 300 → 画符 1 级」今天无法纯数据包实现**——
      `mxt:trigger`(tick) + `mxt:resource_compare` + `mxt:add_resource` 三个零件都够，
      唯独缺最后那一下"把等级写上去"。

**最小补法（Java）**：
① `SpiritIdentityAttachment` 的状态键从 `Holder<Technique>` 泛化成 `Identifier skill`
（或把 `SkillStage` 的拥有者抽成一个接口，让功法成为其一）；
② 一个与功法无关的 `SkillProgressionService`（照抄 `TechniqueMasteryService`，把 technique 参数换成 skill）；
③ 条件 `mxt:skill_stage`（带比较运算符）+ 公式变量 `skill_rank`；
④ 一个通用的 `ResourceThresholdProgression`（`resource` + 有序阈值表 + 目标状态键 + 消耗策略），
把上面四套 bespoke 都收敛成它的实例。

### 4.11 顺带：资源比较与惰性初始化

三条小的通用性缺口，来自 `resource` 的实际用法：

1. **`mxt:resource_compare` 只能 `>=`**：`ResourceCompareEntityCondition` 字段是
   `resource` + `min`，判定硬编码 `>=`（`data/condition/builtin/entity/ResourceCompareEntityCondition.java:15-26`）。
   而 `util/math/Comparison` 已有完整 6 运算符 + `compare_to`（`util/math/Comparison.java:14-48`），
   被约 25 个条件复用。境界侧另有自己的 `RealmEntityCondition.Comparison`（EXACT/AT_LEAST/AT_MOST）。
   **最小补法**：让资源条件接受 `Comparison.CODEC`。
2. **没有资源条的资源不会主动初始化**：`AbilityEventBridge.java:250-255` 只筛有非空 `bars` 的数值，
   靠 `ResourceService.change`（`ResourceService.java:49`）惰性初始化。
   于是"定义了 `default_value` 但还没被写过"的数值，`caster_<resource>` 会读到 **0 而不是
   `default_value`**。这是内容包很容易踩的一个静默偏差，至少要写进文档。
   （`resource` 本身数量不限、不需要修炼档案、不需要资源条——`default_value` 与 `max` 是仅有的两个必填字段，
   `data/resource/Resource.java:23-33`。）
3. **公式里的资源名会被扁平化**：`caster_<ns>_<path>`（`util/formula/FormulaNames.java:39-41`），
   两个不同命名空间下同名 path 的资源会**撞名**，而撞名只打一条警告并**忽略后者**（`:92-97`）。
   内容包跨命名空间写同名资源时是静默失效，建议至少升级为加载期错误。

### 4.12 已确认的实现缺陷（读代码发现，不是文档问题）

这四条不是"缺设施"，是现有代码写错了。都已逐行复核。

**D1 · 灵材合成台的灵气每 tick 被清空，玩家永远灌不满。**
`SpiritCraftingTableBlockEntity.serverTick` → `craftAvailable` **每 tick** 跑一次（`:76-83`）。
它用 `FormulaContext.of(this.level)` 求灵气花费（`:107`；而 `FormulaContext` 自己写明 level 上下文
**不含实体变量**），算出 `costs` 后调 `configureAuraCosts`（`:90`），而后者在花费表变化时
**清空已灌灵气**（`:138-142`）。同一次求值在菜单侧用的是 `FormulaContext.of(this.player)`。
于是**只要配方灵气量依赖玩家，两端算出的表就不一样 → 方块每 tick 清一次 → 一滴都留不住**。
修法：两端同源（把求值上下文统一），或让"清空"只在**格子/配方**变化时发生而不在花费表重算时发生。

**D2 · 锻打输入槽声明 15、实际 12。**
`ForgingBlueprint.MAX_INPUT_ENTRIES = 15`（`data/forging/ForgingBlueprint.java:46`，`:39` 的注释也写"15 input slots"），
校验只拒绝 `> 15`（`:108`）；而 `ForgingSurface.INPUT_SLOTS = 4 × 3 = 12`（`runtime/forging/ForgingSurface.java:17-19`）。
**后果**：声明 13~15 种不同材料的蓝图**能通过加载，却永远开不了工**（`INSUFFICIENT_MATERIALS`）。这是个静默死蓝图。

**D3 · `mxt:blank_talisman` 有三套名字，游戏里显示成原始键名。**
物品 id 是 `mxt:blank_talisman`（`registry/MxtItems.java:47`），
模型文件是 `models/item/blank_talisman_paper.json`（**没有** `blank_talisman.json`），
语言键只有 `item.mxt.blank_talisman_paper`（`assets/mxt/lang/zh_cn.json:266`）——
**`item.mxt.blank_talisman` 不存在，所以这张符纸在游戏里显示的是未翻译的键**。
`docs/通用物品.md:23` 更是把它写成了不存在的物品 id `mxt:blank_talisman_paper`。

**D4 · `item_quality` 的三个修正从来不被求值。**
`valueMultiplier` / `forgingModifier` / `alchemyModifier` 各自是 `(description, modifier)`，
而**唯一读取点** `ItemQualityTooltipAppender.appendModifier` 只打印 `description()`，
**从不求值 `modifier()`**（`data/quality/ItemQualityTooltipAppender.java:46-48,53-55`）。
所以 `docs/数据包格式.md:725,730` 说的"货币价值修正""实际运行时使用"两个都没发生。
典型的"字段已建、只差三行求值"——但求值点本身也不存在（无价值结算、无锻造修正、无炼丹修正），
所以这是 §5 的一条 A 类，不只是三行的事。

---

## 5. 已建未接线清单（A 类，可逐条核对）

每一条都是「定义 + Codec + 字段在，消费者不在」。修法基本都是接线，不是新设施。

|  # | 位置                                                                                                                           | 现状                                                                                                                                                                                                                                                                                                                                     | 证据                                                                                                                                       |
|---:|------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | `AlchemyWorkstation`                                                                                                         | 接口**零实现**；无丹炉方块/方块实体/菜单/界面                                                                                                                                                                                                                                                                                                             | `runtime/alchemy/AlchemyWorkstation.java:25`；`MxtBlocks` 14 个方块、`MxtBlockEntities` 5 个、`MxtMenus` 9 个均无                                  |
|  2 | `MxtRecipeTypes.REFINING`                                                                                                    | 只被 `RefiningRecipe` 自身引用 → 无消费者；且 `ItemArchetype` 已是数据包注册表，这条配方路径是重复的。**2026-09-21 复核：仍然没有消费者**（`archetype` 字段仍是内联的）                                                                                                                                                                                                                   | `recipe/RefiningRecipe.java:56`（全仓唯一命中）                                                                                                  |
|  3 | `MxtRecipeTypes.FORMATION`                                                                                                   | 同上；`Formation` 已是数据包注册表                                                                                                                                                                                                                                                                                                                | `recipe/FormationRecipe.java:53`                                                                                                         |
|  4 | `SpiritHerb` 的 `age`/`growth_rate`/`drop_chance`/`element_tags`/`material_tags`                                              | 5 个字段零消费者（只有 `items`/`quality` 有）；**`element_tags`/`material_tags` 已于 2026-09-19 由新条目 `mxt:herb_tag` 消费**，`age`/`growth_rate`/`drop_chance` 仍等灵植生长系统                                                                                                                                                                                   | `data/alchemy/SpiritHerb.java:18-25`；`runtime/alchemy/HerbTagEntry.java`                                                                 |
|  5 | `AuraZone.Rules` 的 `spirit_plant_bonus`/`alchemy_env_bonus`/`natural_spawn_herb`                                             | 零消费者（同 `Rules` 的另两个已接）；**`alchemy_env_bonus` 已于 2026-09-19 接上**（该区域内的丹药配方视为满足 `minimum_aura`），另外两个仍等灵植生长系统                                                                                                                                                                                                                             | `data/aura/AuraZone.java:140-149`；`runtime/alchemy/AlchemyWorkstationService.java`                                                       |
|  6 | `ItemArchetype.item_type`                                                                                                    | **2026-09-21 复核：仍无按值分流的判断**。重设计后它成为必填且非空、并明确为"器型标识"，但代码里没有任何 `switch`/`if` 读它的值；它是给数据包与内容方认族的标签，不是分发键                                                                                                                                                                                                                                  | `data/artifact/ItemArchetype.java`                                                                                                       |
|  7 | `ItemArchetype.spirit_capacity`                                                                                              | **已于 2026-09-21 重做**：从单个 `NumberProvider` 变成 `灵气 → 上限` 的 map，上限按灵气分别计算，非有限/非正按 0；存量改用共用组件 `mxt:spirit_storage`                                                                                                                                                                                                                         | `runtime/artifact/ArtifactService.java`（`capacity`/`addEnergy`）                                                                          |
|  8 | `ArtifactStorageComponent` / `ArtifactStorageService` / `ISpiritStorage`                                                     | **2026-09-21：槽位来源改接**（定义里的 `mxt:storage` 条目，`ISpiritStorage` 三处签名带上 `Provider`）；**2026-09-22：界面已接上**——`mxt:storage` 现在是一个 `ToggableArtifactAbility`（轮盘上「储物」那一格），按下打开复用原版箱子菜单的窗口（`MxtMenus.ARTIFACT_STORAGE` + `ContainerScreen`），容器是 `ArtifactStorageContainer`（改动整份写回组件、法器离身即关窗）；格数改成按 9 向上取整、最多 54，见 `research/32_法器开关与轮盘接线设计.md` §7 | `runtime/artifact/ArtifactStorageService.java`（原结论里的"仍无物品与菜单使用"已不成立）                                                                     |
|  9 | `ArtifactStateComponent.nourishment`                                                                                         | **已于 2026-09-19 接上、2026-09-21 复核仍成立**：灌能时按"真正收下 ÷ 本次该灵气的有效上限"上涨（夹在 `0..1`、只升不降），同时作为容量加成 `× (1 + 0.5 × nourishment)`。同轮该组件删掉了 `spirit_energy` 与 `archetype` 两个字段                                                                                                                                                                       | `runtime/artifact/ArtifactService.java`                                                                                                  |
| 10 | `data_storage_type`：`toggle`/`timer`/`resource`/`target_lock`                                                                | **已于 2026-09-19 关闭**：四者各有一个 `mxt:storage_toggle`/`storage_timer`/`storage_resource`/`storage_target` 实体条件读取（`mxt:charges`/`mxt:cooldown` 另补了 `storage_charges`/`storage_cooldown`，六种类型全部可读）                                                                                                                                            | `registry/MxtEntityConditions.java`；`data/condition/builtin/entity/Storage*EntityCondition.java`                                         |
| 11 | `ChargesDataStorage.recharge_ticks`                                                                                          | **已于 2026-09-19 关闭**：`AbilityEventBridge` 每 tick 按"距上次写入 ≥ recharge_ticks"回充一次，最多一步、不脏化附件                                                                                                                                                                                                                                              | `runtime/ability/AbilityStorage.java`；`runtime/ability/AbilityEventBridge.java`                                                          |
| 12 | `SkillStage.damage_multiplier`                                                                                               | **已于 2026-09-20 关闭**：施放能力时 `AbilityService` 把"授予该能力、且施法者当前所在的那一级"的倍率写进公式上下文（`damage_multiplier`），`DamageCalculationService` 第一层读它；TODO 与类注释里的推后说明一并删除                                                                                                                                                                                  | `runtime/cultivation/SkillStageService.java`；`runtime/ability/AbilityService.java`                                                       |
| 13 | `CreatureProfile.realm_stages`                                                                                               | **已于 2026-09-25 关闭（换形状而非接消费者）**：字段删除，改为 `spawn_action`（单个 `EntityAction`，档案写入时执行一次）                                                                                                                                                                                                                                   | `data/creature/CreatureProfile.java`；`runtime/creature/CreatureProfileService.java`；`research/47` §4.1                                                                              |
| 14 | `Technique.grade`                                                                                                            | **已于 2026-09-19 接上**：功法面板的行悬浮提示显示"品阶：<原文>"（存在 `mxt.technique_grade.<grade>` 时用翻译）。**2026-09-23 推翻**：自由文本 `grade` 换成可选 `Holder<item_quality>`（面板行首改为「功法名 + 等级」、名称与品阶按该档的颜色上色），`mxt.technique_grade.*` 那套查键退休，见 `research/46` | `screen/information/TechniquePanelScreen.java`；`data/cultivation/Technique.java` |
| 15 | `SpiritRoot.rarity` / `Physique.rarity`                                                                                      | **已于 2026-09-21 关闭**：信息面板的灵根行与体质行在悬浮提示里显示稀有度（`DefinitionText.rarity`，存在 `mxt.rarity.<rarity>` 时用翻译、否则原文，与功法 `grade` 同一套读法），`/mxt spirit_root list` 与 `/mxt physique list` 也打印它；`Physique` 另加了两个与元素无关的伤害倍率（`damage_dealt_multiplier`/`damage_taken_multiplier`），由伤害管线两层读取                                                                          | `DefinitionText`；`screen/information/InformationManager`；`runtime/damage/DamageCalculationService`                                       |
| 16 | `TriggerContext.damageSource()`                                                                                              | **零读取点**——`mxt:hurt` 把 `DamageSource` 放进去了，但条件/公式都拿不到                                                                                                                                                                                                                                                                                  | `data/trigger/TriggerContext.java:58`（全仓唯一命中）                                                                                            |
| 17 | `CultivationAffinity.multiplier` 的 `roots` / `techniques` 两个 `Function` 参数                                                   | **已于 2026-09-21 关闭**：两个参数与同样没人用的 `Provider access` 一起删除，倍率改为从 `SpiritIdentityAttachment` 自己持有的灵根与功法读（顺便把两个重载里重复的尾巴抽成 `combine`）；`abilityMultiplier` 的 `roots` 参数同样删除                                                                                                                                                                   | `runtime/cultivation/CultivationAffinity.java`；`runtime/cultivation/CultivationActionService.java`；`runtime/ability/AbilityService.java` |
| 18 | `RuntimeDimensionService`                                                                                                    | 类可用但**全仓零调用者**（无命令、无 KubeJS、无内部调用）                                                                                                                                                                                                                                                                                                     | `runtime/world/RuntimeDimensionService.java:31-81`                                                                                       |
| 19 | `SpiritStoneVein`                                                                                                            | 纯诊断读取器，无玩法消费者；方块与等级阈值写死                                                                                                                                                                                                                                                                                                                | `runtime/world/SpiritStoneVein.java:22,28,34-51`；唯一调用 `command/AuraCommand.java:90`                                                      |
| 20 | `ResourceLedger`                                                                                                             | 与附件账本并行的**第二套账本**，`src/main` 无生产调用点                                                                                                                                                                                                                                                                                                    | `runtime/resource/ResourceLedger.java`（全 67 行，仅自用）                                                                                       |
| 21 | `MxtItems` 里的空壳物品：`talisman_brush` / `talisman_ink` / `cinnabar` / `blank_talisman` / `recall_talisman` / `realm_reward_box` | 全部 `Item::new`，无行为                                                                                                                                                                                                                                                                                                                     | `registry/MxtItems.java:47,51,53,59-61`                                                                                                  |
| 22 | `ItemQuality.Modifier.modifier`（三个修正各一个）                                                                                     | **已于 2026-09-19 关闭**：`value_multiplier` 进入货币单位面值（取整，Tooltip 同步显示结算值）、`forging_modifier` 进入锻造品质档（`额外步数 ÷ modifier`，取锁定材料中最低一档）、`alchemy_modifier` 进入炼丹时长（`声明时长 ÷ modifier`，取开炉原料中最低一档）                                                                                                                                                  | `runtime/economy/CurrencyValueService.java`；`runtime/forging/ForgingService.java`；`runtime/alchemy/AlchemySession.java`                  |
| 23 | `TalismanComponent.appended()`                                                                                               | 铭刻的写入缝：`src/main` **零调用者**（唯一调用在 test-mod `MxtTestMod.java:5070`）                                                                                                                                                                                                                                                                      | `data/item/TalismanComponent.java:44-48`                                                                                                 |
| 24 | `IdentificationComponent`                                                                                                    | 只有读（`item/IdentificationMirrorItem.java:29-33`），**没有写入方**                                                                                                                                                                                                                                                                              | `data/item/IdentificationComponent.java`                                                                                                 |

另有一条曾经**文档承诺但代码没有**的 API：`research/04_数据包与KubeJS边界.md` 的设计稿里写过的
`Mxt.registries().registerJson("ability", "example:fire_ball", …)` 在 `compat/kubejs/MxtKubeJsApi.java`
里**不存在**（该 API 只有 `ability/curse/useAbility/…/addAuraBox/removeAuraArea` 等运行时方法）——该文档的 API
一节现已按实际形状改写并注明这一点。
即 **KubeJS 不能注入数据包定义**，只能扩展固有类型；数据定义一律走数据包。

---

## 6. 文档与代码不一致（C 类）

|  # | 位置                                                      | 文档说                                                                                                                                                                    | 代码是                                                                                                                                                                                                             |
|---:|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1 | `docs/模块实现审计.md:104`                                    | 炼丹「完成」——「原料、温度、炉阶、时长、灵气环境、产物和成败行为由炼丹工作台处理」                                                                                                                             | **没有炼丹工作台**（§4.1、§5#1）                                                                                                                                                                                          |
|  2 | `docs/getting-started.md:51`                            | 「阵法、锻造、炼丹」一栏写「完成」                                                                                                                                                      | 炼丹未接线                                                                                                                                                                                                           |
|  3 | `docs/模块实现审计.md:26`                                     | 「`ItemStackTemplate` 用于返还物、**配方产物**和聊天悬浮物品」                                                                                                                            | `AlchemyRecipe` 产物仍是 `List<Identifier>`                                                                                                                                                                         |
|  4 | `docs/模块实现审计.md:94`、`docs/数据包格式.md:1267`                | 维度装载器提供"备用的 LevelStem 装载/卸载能力"                                                                                                                                         | 类可用但**零调用者**，是死代码                                                                                                                                                                                               |
|  5 | `docs/灵气环境数据包.md:84,89,102,104`                         | 引用 `base_aura` 与 `block_aura.aura_per_block`                                                                                                                           | **两个字段都不存在**。实际是 `aura.<resource>.amount`（`AuraZone.java:43` + `AuraService.java:495-498`）；`BlockAura` 只有 `blocks`/`aura`（`BlockAura.java:18-21`；`aura_kinds` 已于 2026-09-17 删除）。`docs/数据包格式.md:1124-1134` 写的是对的 |
|  6 | `docs/数据包格式.md:1162`                                    | `structure_template` 标"**必填**"                                                                                                                                         | 同表 `:1163` 又说二选一；代码里两者都是 `optionalFieldOf` + `validate` 强制**恰好一个**（`Formation.java:46,49,81-88`）                                                                                                                |
|  7 | `docs/数据包格式.md:1261`                                    | `realm_instance.dimension`「缺省时由入口逻辑决定」                                                                                                                                 | 缺省 → `destination()` 返回 empty → 进入立即 `MISSING_DIMENSION`（`RealmInstanceService.java:41-42,134-136`）                                                                                                             |
|  8 | `docs/数据包格式.md:338`                                     | 把 `alchemy_recipe` 列进数据包注册表路径表（`mxt/alchemy_recipe`）                                                                                                                   | 它是配方类型，路径应为 `data/<ns>/recipe/`，`"type": "mxt:alchemy"`                                                                                                                                                         |
|  9 | `docs/` 全库                                              | **没有任何**世界生成/结构/群系/维度定义的说明（grep `add_features\|biome_modifier\|configured_feature\|placed_feature\|add_spawns\|structure_set\|template_pool\|dimension_type` **0 命中**） | 基座自己正用 `neoforge:add_features` 做矿石生成，只是没写                                                                                                                                                                       |
| 10 | `research/audit/technique.md` §9-7 / §12.5              | 功法面板图标是 TODO，等 `technique` 加 `icon`                                                                                                                                    | **已过期**：`icon` 字段已存在（`Technique.java:32,41`）且面板已在渲染；文件内 `//TODO` 0 命中。另：T6「暂无遗忘入口」也已过期（`/mxt technique drop\|repair` 已存在，`TechniqueCommand.java:57-60,159-182`）；该文件行号普遍漂移；§8.2 引用的 `data/Title.java` **不存在**    |
| 11 | `research/00_策划整理与范围.md:19-23`                          | 「明确不在本期：预置的…具体内容」                                                                                                                                                      | `MxtItems` 已预置 38 件具体物品（四档灵石、令牌、符纸…），`data/mxt/mxt/` 已有 17 个默认定义，`data/mxt/worldgen/` 有基座自己的矿石                                                                                                                  |
| 12 | `runtime/cultivation/TechniqueCommand.java:184-187` 类注释 | `rebuild` 重建「granted abilities, **passive attributes and resource ceilings**」                                                                                          | `rebuild`（`:188-190`）只调 `CultivationGrantService.recalculate`；被动属性靠每 tick 的 `PassiveAttributeService.tick`（`AbilityEventBridge.java:120`），资源上限来自 `resource` 定义（`ResourceService.resolveBounds:101-105`）         |
| 13 | `docs/通用物品.md:23`                                       | 物品 id 写作 `mxt:blank_talisman_paper`                                                                                                                                    | **不存在这个物品**：id 是 `mxt:blank_talisman`（`MxtItems.java:47`），只有模型文件与语言键叫 `..._paper`。见 §4.12-D3                                                                                                                    |
| 14 | `docs/guide/datapack/overview.md:54-60`                 | 注册表索引列 **32** 条                                                                                                                                                        | 代码当时是 **34** 条（`MxtResourceKeys.java:86-119`），表里漏了 `tool_binding` 与 `blueprint_binding`；该表已补齐，现为 **33** 条（`sect` 注册表已删除）                                                                                        |
| 15 | `docs/getting-started.md:48` vs `:51`                   | 同一文件里 `:48` 写「灵植、炼丹和自然生成规则仍缺少完整消费者」，`:51` 又写「阵法、锻造、炼丹」完成                                                                                                               | **文件内自相矛盾**；`:48` 是对的                                                                                                                                                                                           |
| 16 | `docs/guide/play/interaction.md:7`                      | 灵材台"输入**保留**…**取出结果时**扣除灵气"                                                                                                                                            | 代码是**每 tick 自动**推进：9 格各 `shrink(1)`、产物直接进结果槽、灵气在 tick 内扣（`SpiritCraftingTableBlockEntity.java:96-99`）                                                                                                           |

> 注：`docs/数据包格式.md:764,768`（`damage_multiplier` 无消费者）、`:808`（入口级不写条目）、
> `docs/灵气环境数据包.md:93,95`（aura_kinds 已接、三个 rules 字段无消费者）**均与代码一致**——
> 本次核对里这几处是准的，别把它们一并当错。

---

## 7. 建议顺序

按「解锁的内容量 ÷ 改动量」排：

|    优先级     | 事项                                                                                                                                                                                                                                          | 改动量             | 解锁                                                    |
|:----------:|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-------------------------------------------------------|
|   **P0**   | **炼丹/炼器通用工位**（§4.1）：实现 `AlchemyWorkstation` 的方块+BE+菜单+界面；`furnaceTier` 数据化                                                                                                                                                                  | 中               | 9 级丹炉 + 9 级鼎 + 28 丹方 + 192 药材方块                       |
|   **P0**   | **通用档位/变体组件**（§4.9）                                                                                                                                                                                                                         | 小               | 82 族 / 235 行（年份、等阶、品相、结果状态、等级）                        |
|   **P0**   | **世界生成文档 + JSON 骨架**（§4.8-1）                                                                                                                                                                                                                | 极小（0 行 Java）    | 33 条结构 / 8 群系 / 2 维度 / 全部矿脉                           |
|   **P0**   | **修 D1 / D2 / D3**（§4.12）：灵材台灵气被清、锻打槽 15 vs 12、`blank_talisman` 三套名字                                                                                                                                                                        | 极小              | 三个静默失效：灵材台永远灌不满、13~15 材料的蓝图永久死、符纸显示原始键名               |
|   **P1**   | **目录/图鉴系统**（§4.6）                                                                                                                                                                                                                           | 中               | 22 族 / 198 行 GUI + 501 行描述                            |
|   **P1**   | **通用物品右键路径**（§3.1）：`item_binding` 补 `use_action` + `costs` + 右键分发                                                                                                                                                                           | 小               | 46 张符箓与全部右键道具                                         |
|   **P1**   | **通用等级/熟练度链**（§4.10）：状态键泛化 + 独立晋升服务 + `mxt:skill_stage` 条件                                                                                                                                                                                  | 小～中             | 画符/炼丹/炼器等级 + 功法层数                                     |
|   **P1**   | **灵植生命周期**（§4.4）+ `SpiritHerb` / `AuraZone.Rules` 死字段接线                                                                                                                                                                                     | 中               | 77 族 / 250 行                                          |
|   **P1**   | **清理 A 类 2/3/6/7/9/11/14/15/17/18/20/22/23/24**（死路径、死字段、无写入方的缝）                                                                                                                                                                             | 极小              | 减维护面；多为直接删或一行接线                                       |
| ~~**P2**~~ | **统一伤害管线**（§4.2）——**已于 2026-09-20 完成**：`DamageCalculationService` 两层（发伤害处的出力层 + `LivingIncomingDamageEvent` 的减免层），`mxt:damage` / `mxt:damage_target` / 阵法攻击 / `mxt:explode` 全部收拢，元素克制/适应倍率写进 `element` 定义，`SkillStage.damage_multiplier` 接上 | 已完成             | 内容整套攻防数值 + `damage_multiplier`（仍缺：伤害类型→元素的映射、灵宝侧攻防数值） |
|   **P2**   | **数据驱动区域灵气**（§4.8-2）                                                                                                                                                                                                                        | 小～中             | 富灵气区、灵脉、生成三者的统一区域概念                                   |
|   **P2**   | **生成表 / 灵气驱动生成**（§4.5、§4.8-3）                                                                                                                                                                                                               | 中               | 151 个实体 / 9 张刷新表 / `natural_spawn_herb`               |
|   **P2**   | **状态注册表**（§4.3）                                                                                                                                                                                                                             | 中               | 107 个效果                                               |
|   **P3**   | **储物物品+界面**（§4.7）                                                                                                                                                                                                                           | 小               | 储物袋全系                                                 |
|   **P3**   | **秘境驱动维度装载 + 入口坐标**（§4.8-4）                                                                                                                                                                                                                 | 小（约 5 行 + 1 字段） | 2 个维度                                                 |
|   **P3**   | **资源条件支持完整比较运算符**（§4.11）                                                                                                                                                                                                                    | 极小              | 全部阈值判定                                                |
|   **P3**   | 符文 / 淬炼 / 修理台等炼器支线                                                                                                                                                                                                                          | 小～中             | 炼器系统余下 5 个子系统                                         |

---

## 8. 需要拍板的三处设计冲突

1. **符箓语义冲突**。基座的 `talisman` 是「灌注灵气 → 灌满即自动发动 → 一用即焚」的载体
   （`runtime/talisman/TalismanService.java`，`research/audit/talisman.md` 全篇），
   而内容侧的符箓是「右键使用 → 过灵力门槛 → 扣灵力 → 施法」的一次性法术，
   另有符笔等级、成功率、熟练度、云图符升级。两者不是同一件东西。
   要么给基座补「铭刻服务 + 制符台」并让 `talisman` 承载内容语义，
   要么承认基座的 `talisman` 是另一个玩法，内容符箓走 §3.1 的通用物品右键路径。
2. **自定义属性 vs 资源池**。内容侧有 20+ 个派生数值（法攻/法防/火雷毒攻防/神识/智力/体质/心伤/杀气/…）。
   基座**没有注册任何自定义 `Attribute`**，且数据包/KubeJS 无法注册属性 → 只能用 `resource` 池表达。
   资源池能用（可读进公式、可显示资源条），但拿不到原版属性的同步、装备修正、
   `AttributeModifier` 那一整套。要么基座预置一批通用属性，要么在文档里明确"一律走 resource"。
3. **`curse` 泛化成"状态"还是新开 `mxt:status`**（§4.3）。前者改动小但要动既有语义，
   后者干净但多一张注册表 + 一套运行时。

---

## 附：本文件引用的工作区位置

| 位置                                                                                                                        | 内容                                                                                              |
|---------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `registry/MxtResourceKeys.java:86-120`                                                                                    | 33 个数据包注册表的唯一清单                                                                                 |
| `runtime/alchemy/AlchemyWorkstation.java`                                                                                 | 零实现的炼丹工作台契约                                                                                     |
| `data/alchemy/AlchemyRecipe.java` / `SpiritHerb.java`                                                                     | 炼丹配方 / 灵植定义                                                                                     |
| `recipe/SpiritRecipe.java`                                                                                                | 唯一把产物写成 `ItemStackTemplate` 的配方                                                                 |
| `runtime/item/ItemBindingService.java:73-82, 223-230`                                                                     | `item_binding` 只在 `Finish` 分发                                                                   |
| `runtime/item/ItemQualityService.java:44-87, 100-174`                                                                     | 物品门槛与具名拒绝原因（可复用的半边）                                                                             |
| `data/action/builtin/entity/DamageAction.java` vs `data/formation/AttackFormationAction.java`                             | generic 伤害 / 唯一带类型与归属的伤害                                                                        |
| `data/trigger/TriggerContext.java:58`                                                                                     | 零读取点的 `damageSource()`                                                                          |
| `data/condition/builtin/entity/ResourceCompareEntityCondition.java`                                                       | 只能 `>=` 的资源条件                                                                                   |
| `data/ability/component/*` + `registry/MxtAbilityComponents.java:17-20`                                                   | 四个无消费者的能力组件                                                                                     |
| `data/cultivation/SkillStage.java:22`                                                                                     | `damage_multiplier` 的 TODO                                                                      |
| `runtime/cultivation/{SkillStageService,TechniqueMasteryService}.java` + `attachment/SpiritIdentityAttachment.java:29,35` | 通用链 + 专用于功法的消费者                                                                                 |
| `data/curse/Curse.java:21-38`                                                                                             | 最接近"限时命名状态"的定义                                                                                  |
| `data/aura/AuraZone.java:36, 140-149` + `runtime/world/AuraWorldAttachment.java:19-74`                                    | 群系/维度挂载、三个死规则字段、只能 KubeJS 写入的区域                                                                 |
| `data/creature/CreatureProfile.java:29-33` + `runtime/creature/CreatureProfileService.java:29-75`                         | 生物档案字段表（无生成字段；**2026-09-25 起有 `spawn_action`，但那只是"写入时跑一次"**）与"只给已生成 Mob 套档"                                                                   |
| `data/artifact/ItemArchetype.java` / `ArtifactStateComponent.java`                                                        | 法器原型与状态（**2026-09-21 重设计**：`items` 认领物品 + `abilities` 固有分派列表 + 灵气 map 上限；`item_type` 仍无按值分流的判断） |
| `runtime/world/RuntimeDimensionService.java:31-81` / `RealmInstanceService.java:41-53,134-136`                            | 零调用者的维度装载器 / 秘境进入路径                                                                             |
| `runtime/world/SpiritStoneVein.java:22,28,34-51`                                                                          | 硬编码 6 级灵脉枚举                                                                                     |
| `runtime/formation/FormationStructureValidator.java:30-83` + `data/Formation.java:46,49,81-88`                            | 只有只读结构匹配                                                                                        |
| `data/mxt/{worldgen,neoforge/biome_modifier}/*`                                                                           | 基座唯一的世界生成设施（自己的矿石）                                                                              |
| `screen/information/InformationManager.java:26-40`                                                                        | Java 静态注册的信息面板（非数据驱动）                                                                           |
| `registry/{MxtBlocks,MxtBlockEntities,MxtMenus}.java`                                                                     | 基座自己的 14 方块 / 5 方块实体 / 9 菜单                                                                     |
| `MxtItems.java:27-63`                                                                                                     | 基座自己的 38 件物品（含 6 件空壳）                                                                           |
| `docs/模块实现审计.md` / `docs/数据包格式.md` / `docs/灵气环境数据包.md` / `research/04_数据包与KubeJS边界.md` / `research/audit/technique.md`    | C 类不一致的来源                                                                                       |
| `research/audit/生产系统审计.md`                                                                                                | 同日的子代理深度报告（608 行，只覆盖生产/制作五个系统）；本文件 §4.12 与 §5#22-24 的复核结果来自它，负载最重的 6 条已独立复验                     |
