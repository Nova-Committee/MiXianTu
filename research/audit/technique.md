# 功法（`cultivation_technique`）数据驱动与功能审计

审计基准：MC 26.1.2 / NeoForge 26.1.2.99 / KubeJS 26.1.2-8.0.4。
结论均来自直接读代码，行号对应当前工作区状态（2026-09-12）。本文件是"先记录、后动手"的持久化笔记，便于后续完善功法时不必重新翻代码。

> 变更记录
> - 2026-09-12：新增本文件。
> - 2026-09-12：**删除 Curios 功法槽设计**（§5、T3 已解决），新增 §8 功法等级链条调研。

## 0. 边界澄清（避免概念混淆）

- **功法 = `cultivation_technique`**：玩家学会后常驻生效的修炼法门。
- **手法 = `forging_method`**：锻造单步效果，与功法无关（`ForgingBlueprint.allowed_methods`、`ToolBinding` 那一套）。
- **修炼行为 = `cultivate_action`**：与功法**无关联**。`CultivationModeService.resolveAction`（`runtime/cultivation/CultivationModeService.java:71-77`）只按 `default` 标记 / 玩家已选 / 注册表第一个来解析，不读 `learnedTechniques`。
- 功法只有两个数据驱动注册表：`cultivation_technique`（定义）与 `technique_binding`（载体绑定）。无第三张表（没有"功法槽位表""功法等级表"之类）。

## 1. 注册表清单

| 注册表 | JSON 位置 | Codec | 注册点 | 备注 |
| --- | --- | --- | --- | --- |
| `mxt:cultivation_technique` | `data/<ns>/mxt/cultivation_technique/<id>.json` | `CultivationTechnique.DIRECT_CODEC`（`data/cultivation/CultivationTechnique.java:26-34`） | `registry/MxtDatapackRegistries.java:69` | 同一 codec 兼作同步 codec（`MxtDatapackRegistries.java:91-94`），客户端可读，故 tooltip 能用 `context.registries()` 解析 |
| `mxt:technique_binding` | `data/<ns>/mxt/technique_binding/<id>.json` | `TechniqueBinding.CODEC`（`data/item/TechniqueBinding.java:23-28`） | `registry/MxtDatapackRegistries.java:84` | 走 `ItemMatcher`，载体是**现有物品**，不创建书籍/玉简 |

两者都支持 `#mxt:disabled` 标签禁用：`MxtDatapackRegistries.java:52,100-109,152-174`；功法侧入口检查在 `TechniqueService.java:29-30`。

## 2. `cultivation_technique` 字段与真实消费者

| 字段 | 类型 / 默认 | 实际功能 | 消费点 |
| --- | --- | --- | --- |
| `grade` | String `common` | **无任何读取点**，纯存储元数据（等级方案见 §8） | 全仓 `.grade()` 零调用（唯一同名调用是 `SpiritStoneVein.Grade`，无关） |
| `learn_condition` | `EntityCondition` `always_true` | 学习时校验，失败 → `CONDITIONS` | `runtime/cultivation/TechniqueService.java:50-51` |
| `exclusive_tags` | `Identifier[]` `[]` | 互斥：汇总已学功法的标签集合，与新功法求交集，命中 → `CONFLICT` | `TechniqueService.java:33-36`；另见 `runtime/cultivation/CultivationIdentityService.java:46-48`、`TitleService.java:31-33`（同型机制） |
| `cultivation_modifier` | `NumberProvider` `1` | 修炼速度倍率，**逐功法连乘**进 affinity；非有限或负数 → NaN → 修炼中断 `INVALID_FORMULA` | `runtime/cultivation/CultivationAffinity.java:49-53, 76-80`；被 `CultivationActionService.java:99-101, 120-122` 调用 |
| `passive_modifiers` | `AttributeEntry[]` `[]` | 常驻被动属性，修饰符来源标识 `technique` | `runtime/ability/PassiveAttributeService.java:107-108` |
| `granted_abilities` | `HolderOrTag<ability>[]` `[]` | 学习后授予能力；支持 `#tag`，经 `RegistryCodecs.resolve` 展开去重；source = `mxt:grant/technique/<ns>/<path>`，重算时先撤销全部 `grant/` 来源再重建 | `runtime/cultivation/CultivationGrantService.java:36-37, 45-47, 60-70` |

## 3. `technique_binding` 字段

| 字段 | 类型 / 默认 | 功能 |
| --- | --- | --- |
| `items` | `ItemMatcher.Entry[]`（单值 / `#tag` / 混合数组 / `mxt:item|tag|wildcard|regex`） | 哪些现有物品是功法载体 |
| `technique` | `Holder<cultivation_technique>` **必填** | 右键该物品尝试学习的功法 |
| `quality_group` | 可选 `#tag` | 参与 `ResolvedBindings.qualityGroup()`，优先级 weapon → pill → technique → item（`runtime/item/ItemBindingService.java:317-321`） |
| `conditions` | `DescribedEntry<EntityCondition>[]` `[]` | 全部满足才允许学习；tooltip 逐条渲染 ✔/✖（`data/item/ItemBindingTooltipAppender.java:61, 65-75`） |

测试数据示例：`src/test-mod/resources/data/mxt_test/mxt/technique_binding/qingxiao_breathing_jade_slip.json` 把 `mxt:cultivation_jade_slip` 绑到 `mxt_test:qingxiao_breathing_manual`，带 `quality_group: "#mxt_test:group/forged"` 与一条 `mxt:always_true` 条件。

## 4. 运行时流程（唯一的正式学习入口）

入口：右键物品 → `TechniqueItemService.onItemUse`（`RightClickItem`，`EventPriority.HIGH`），仅服务端执行（`runtime/cultivation/TechniqueItemService.java:27-45`）：

1. `ItemBindingService.technique(stack)` 匹配 binding；无匹配 → 不接管（返回 `false`，物品原行为继续）。
2. `ItemQualityService.canUse(entity, stack)` 检查品质组与 `conditions`（`runtime/item/ItemQualityService.java:109-116`）；不满足 → **返回 `true`，即吞掉这次右键**，防止绕过判定。
3. `TechniqueService.learn(entity, spirit, holder, context)`（`TechniqueService.java:28-57`）按序判定：
   - `#mxt:disabled` → `DISABLED`
   - 已学 → `ALREADY_LEARNED`
   - 互斥标签冲突 → `CONFLICT`
   - `learn_condition` 失败 → `CONDITIONS`
   - `TechniqueLearnEvent.Pre` 被取消 → `CANCELLED`
4. 成功后：写入 `SpiritIdentityAttachment.learnedTechniques` → 发 `TechniqueLearnEvent.Post` → `CultivationGrantService.recalculate` + `AbilityEventBridge.rebuildTriggerSubscriptions`。
5. 失败仅返回 `Result`，**没有任何玩家可见反馈**（见 §7 T2）。

持久化与同步：`attachment/SpiritIdentityAttachment.java:20-25, 52-54, 72-83`，`learned_techniques` 字段，继承 `ShouldSyncAttachment` → 客户端持有已学列表（tooltip / 信息面板依赖它）。

## 5. 展示与载体物品

- 物品 tooltip：只显示功法名，`tooltip.mxt.item.cultivation_technique`（`ItemBindingTooltipAppender.java:120-123`）；不显示 `grade`、倍率或授予能力。
- 信息面板：`info.mxt.techniques` 一行，`InformationManager.java:39`（`lineWithDefinitions(..., "cultivation_technique")`）。
- 载体物品：`registry/MxtItems.java:46` `CULTIVATION_JADE_SLIP`（`item.mxt.cultivation_jade_slip`）；它只是普通物品，经 `technique_binding` 右键学习，**与槽位无关**。
- ~~Curios 功法槽~~ **已于 2026-09-12 整体删除**：`data/mxt/curios/slots/technique.json`、`data/curios/tags/item/technique.json`、`data/mxt/tags/item/technique_equipable.json`、`assets/mxt/textures/slot/empty_technique_slot.png` 四个文件删除，`data/mxt/curios/entities/entities.json` 去掉 `technique` 项，`curios.identifier.technique` lang（中英）移除，并同步修了 4 处文档（`docs/curios槽位.md`、`docs/guide/play/interaction.md`、`docs/模块实现审计.md:123`、`E:\Website\docs\...\player-guide\curios-slots.md`）。现在只注册 `back_weapon`、`belt_item` 两个物理槽位。
- **删除后的必做步骤（易漏）**：改完 `src/main/resources` 必须跑 `.\gradlew.bat processResources`（改 `src/test-mod/resources` 则是 `processTestModResources`）。dev 运行读的是 `build/resources/**`，不同步的话被删掉的槽位文件仍会继续加载——本次删除功法槽时 `build/resources/main` 里就残留了 `data/mxt/curios/slots/technique.json`，已重跑任务并逐文件哈希比对确认同步。

## 6. 事件、脚本与触发器

- 原生事件：`event/TechniqueLearnEvent.java`，`Pre`（`ICancellableEvent`）/`Post`，载荷 `spirit` + `technique` holder。
- KubeJS：`compat/kubejs/MxtKubeJsPlugin.java:66-67` 把两个事件转发为 `techniqueLearn`；`MxtKubeJsEventDispatcher.java:35,77` 用通用 `GenericKubeEvent` 派发，**没有**结构化的字段访问器（对比 `MxtTriggers` 那套）。
- 触发器信号：`data/trigger/TriggerSignals.java:10-19` 只有 `tick/attack/hurt/kill/block_break/block_use/item_use/equip/death/breakthrough`，**没有**"学会功法"信号；数据包技能无法用 `mxt:js` 等待学习事件。
- 运行时代码域 API：`compat/kubejs/MxtKubeJsApi.java` 只有 `ability/curse/useAbility/applyCurse/removeCurse/reclaimSoul/tryBreakthrough/addCultivation/tryConsumeResources/aura/addAuraBox/removeAuraArea/publishTrigger`，**无功法相关方法**（不能查询、授予或遗忘功法）。

## 7. 缺口与可疑点

| 编号 | 问题 | 证据 |
| --- | --- | --- |
| T1 | `grade` 无消费者：不显示、不影响掉落/突破/学习条件；`docs/模块实现审计.md:74` 自己也写"仅存储/展示候选" | `CultivationTechnique` 无 `.grade()` 调用点 |
| T2 | 学习失败**零反馈**：`use()` 丢弃 `Result`，lang 里也没有任何功法失败提示键 | `TechniqueItemService.java:43-44`；对比 `CultivationModeService.notifyFailure`（`:59-66`）有 actionbar 提示 |
| T3 | ~~Curios 功法槽是死的~~ → **已解决（删除整套功法槽，2026-09-12，见 §5）**。原始问题留档：唯一读取已装备 Curios 的是 `AbilityEventBridge.syncCuriosAbilities`，它只读物品的 `mxt:item_abilities` 组件、不读 `technique_binding`，所以玉简放进功法槽既不学习也无效果 | `runtime/ability/AbilityEventBridge.java:205-223`；`CuriosIntegration.equipped` 调用点仅此一处 + 两个渲染器（`BackWeaponRenderer.java:39`、`BeltWeaponRenderer.java:37`） |
| T4 | **死参数**：`CultivationAffinity.multiplier(...)` 两个重载的 `Function<Identifier, Optional<CultivationTechnique>> techniques` 完全未使用，实际遍历 `spirit.learnedTechniques()`；调用方仍在传解析器 | `CultivationAffinity.java:34, 59`；`CultivationActionService.java:100, 121` |
| T5 | **审计文档与代码不一致**：`docs/模块实现审计.md:74` 称"主动功法…已接入"，但 `CultivationTechnique` 没有主动/被动字段，代码无 active 概念（能主动的只是 `granted_abilities` 里的主动型 ability）；`:110` 的"激活状态"在 `TechniqueBinding` 里也没有对应字段 | 逐字段核对 §2/§3 |
| T6 | **无遗忘/拆卸入口**：只有 `addLearnedTechnique` / `setLearnedTechniques` 直接改列表，`TechniqueService` 无 forget；互斥只在学习瞬间校验，换数据包或直写附件后可同时持有互斥功法 | `SpiritIdentityAttachment.java:72-83`；`TechniqueService` 仅 `learn` |
| T7 | **无触发器信号、无脚本 API**（见 §6）：内容包无法感知"学会功法"，脚本无法查询/授予/遗忘 | `TriggerSignals.java:10-19`；`MxtKubeJsApi.java` 全文 |
| T8 | **测试覆盖薄**：`MxtTestMod.java:221-227` 只断言 binding 能解析、`technique` id 正确、conditions 无 description；`sword_manual.json`/`body_manual.json` 共享 `mxt_test:technique/sword` 互斥标签却无用例断言 `CONFLICT`，也没有 `ALREADY_LEARNED`、`learn_condition` 失败、能力授予结果的断言 | `src/test-mod/java/.../MxtTestMod.java:219-227`；`src/test-mod/resources/data/mxt_test/mxt/cultivation_technique/*.json` |
| T9 | `exclusive_tags` 只与"学习前已持有的集合"比较，不检查同批/回溯一致性；本身是自定义分类键（`Identifier` 相等比较），**不是**注册表 tag | `TechniqueService.java:33-36` |

## 8. 功法等级：可复用的等级链条调研（只读，2026-09-12）

问题：现有系统里有没有可以**直接**给 `CultivationTechnique.grade` 复用的"等级链条系统"？
结论：**没有可直接复用的通用等级链**——只有一个完整的境界链，加几个内联的阈值阶梯，且没有任何通用的链抽象类。

### 8.1 唯一的完整等级链条：`realm_stage`（境界链）

- 数据模型 `data/cultivation/RealmStage.java:30-63`：`resource`（**必填**）、`next_realm`（单向 next 指针）、`breakthrough_exp`（本级下限）、`max_experience`（本级上限）、`breakthrough`（条件组）、`auto_breakthrough`、`costs`、`ability_requirements`、`tribulation`、`passive_modifiers`、`success_action`/`fail_action`、`aura_share_weight`、`cultivate_condition`。构造期还校验常量的 `breakthrough_exp <= max_experience`（`:39-44`）。
- 推进逻辑 `runtime/cultivation/CultivationService.java`：
  - `next()`（`:130-141`）从当前级沿 `next_realm` 走**一跳**，并强制 `resource` 一致；凡人态改用 `Resource.first_realm` 与 `start_exp`。
  - `threshold()`（`:253-267`）解析本级 `[breakthrough_exp, max_experience]`。
  - `commit()`（`:100-128`）突破事务：阈值 → 进度 → 条件 → 能力要求 → `CultivationBreakEvent.Pre` → 扣资源 → `setRealmStage` + 进度归零 → `Post`。
  - `addProgress()`（`:146-164`）按本级 `max_experience` 封顶；`remainingProgressCapacity()`（`:190-203`）；`breakthroughStatus()`（`:209-219`，供信息面板与自动突破）；`pendingConditions()`（`:226-229`）。
  - 内部通用类型 `Threshold` / `Transition`（`:269-290`）已经是"链上一跳 + 阈值"的抽象。
- 状态存储 `attachment/CultivationAttachment.java:24-25, 33-34, 104-108`：`cultivation_progress: Map<Resource, Double>` + `realm_stages: Map<Resource, RealmStage>`，**按 Resource 分键** → 多资源 = 多条并行链。
- 已被多方消费（说明这套模型确实好用）：`PassiveAttributeService.java:105-106`（境界被动属性）、`ResourceService.java:118`、`AuraDistributionService.java:161`、`InformationManager.java:94-108`、`RealmEntityCondition.java:38`、`RealmLootCondition.java:28`、`MxtCriteriaTriggers.BREAKTHROUGH`、`ServerCache.resourceForRealm`、`CultivationActionService.java:495, 519`。
- **不能直接复用的两个硬绑定**：① `RealmStage.resource` 必填且链内一致性有校验；② 进度与"当前级"状态只存在于 `CultivationAttachment` 的 resource-keyed map 中。复用路线见 §9。

### 8.2 现成的"阈值阶梯"（有阈值、无链、无状态）

| 系统 | 结构 | 可复用性 |
| --- | --- | --- |
| `Sect.ranks`（`data/Sect.java:36-45`） | `Rank(id, priority, min_contribution, permissions, promotion_costs)`：按贡献阈值定位职级 + 晋升消耗 + 每级权限 | **最接近现成阶梯**，但它是 `Sect` 的内联字段而非独立注册表，且没有 next 指针（顺序由阈值隐含） |
| `ForgingBlueprint.quality_by_extra_steps`（`ForgingBlueprint.java:80, 118-119, 209-222`） | `QualityThreshold(maxExtraSteps, quality)` 升序阈值表，校验必须以 `Integer.MAX_VALUE` 收尾 | 纯"阈值 → `item_quality`"映射，无状态、无升级行为；可作为阈值表写法参考 |
| `Title.maximum_level`（`data/Title.java:24`） | 单字段等级上限（1..1000） | 只有上限，**没有等级系统**（审计已记"等级上限缺少消费者"） |
| `Curse.max_stacks` + `StackingMode`（`data/curse/Curse.java:21, 30-31, 49-57`） | 层数上限 + 堆叠策略 | 层数语义，不是等级链 |
| `SpiritStoneVein.Grade`（`runtime/world/SpiritStoneVein.java:34-51`） | 硬编码 6 级枚举 + `minimumBlocks` 阈值 | 纯运行时，数据包不可扩展 |
| `CreatureProfile.realm_stages`（`data/creature/CreatureProfile.java:28, 35`） | `List<Holder<RealmStage>>`，**无任何消费者**（`CreatureProfileService` 不读） | "非玩家对象复用境界链"曾预留但未落地，是给功法复用境界链的前车之鉴 |

### 8.3 平面注册表（有序但无进阶语义）

- `item_quality`（`data/quality/ItemQuality.java`）：注册表 + `tooltip_order` 原生标签排序 + 质量组标签 + `value_multiplier`/`forging_modifier`/`alchemy_modifier` + `condition`（排序见 `data/quality/ItemQualityTags.java`、`runtime/item/ItemQualityService.ordered`）。可当"品阶标签 + 数值修正 + 展示名"用，但**没有**升级/阈值/进阶语义。
- `CultivationTechnique.grade` 现状是自由字符串，无约束、无排序、无引用。

### 8.4 阶段序列（不是等级链）

- `Tribulation.phases`（`data/Tribulation.java:19-43`）：`Phase(duration, start_action, end_action)` 序列 + `difficulty_scale`，运行时顺序推进。可借鉴"多阶段 + 每阶段行为 + 难度缩放"，但没有等级阈值与持久进度。

### 8.5 命名层面确认

全仓没有 `Chain` / `Ladder` / `Progression` / `Tier` / `Level` 命名的基础设施类（只匹配到 `FoodLevelCondition`、`LightLevelCondition`、`ExperienceLevelCondition`、`SaturationLevelCondition` 这些原版等级条件）。即**不存在通用等级链抽象**。

### 8.6 研究笔记里的既有预期

- `research/13_Mine-And-Slash功能参考.md:159`："职业等级本身可以不移植，后续可替换成境界、熟练度或功法等级。"
- `research/list.txt:2` 把"多层等级系统/渡劫，被动加成（体质）"列为待办模块。

## 9. 建议动手顺序（未执行，仅方案）

1. **功法等级（T1/T5 + §8）**：先定"等级"的形态，三选一：
   - (a) **复用 `item_quality` 当品阶**：`grade` 改为 `Holder<ItemQuality>` 或 quality tag。改动最小，但只有标签/数值/展示，没有升级过程。
   - (b) **照抄 `realm_stage` 模型**：功法自带等级链（next 指针 + 阈值 + 条件 + 消耗 + 每级收益），运行时状态新增附件字段（如 `Map<Holder<CultivationTechnique>, Integer>` 或进度 `Double`）。
   - (c) **抽出通用 `LevelChain`**：把境界链的 next/阈值/条件/奖励抽象成共用类型，境界与功法共同消费。改动最大、收益最统一。
   无论选哪条，都要同步修 `docs/模块实现审计.md:74,110` 中"主动功法/激活状态"的措辞。
2. **T2 失败反馈**：把 `Result`/`Failure` 转成 actionbar 文案（`TechniqueService.Failure` 已 5 种，lang 需补 5 个键），或复用 `TechniqueLearnEvent.Post` 让内容包自行提示。
3. **T6 遗忘入口**：`TechniqueService.forget` + 重算授予/被动属性 + 事件（`TechniqueForgetEvent`）+ 互斥的追溯校验策略。
4. **T7 信号与 API**：新增 `TriggerSignals.TECHNIQUE_LEARN`（或复用 `TechniqueLearnEvent`）与 `MxtKubeJsApi` 的功法查询/授予/遗忘方法。
5. **T4/T8 收尾**：删掉 `CultivationAffinity` 的死参数，补 `CONFLICT`/`ALREADY_LEARNED`/`CONDITIONS` 的学习事务测试（沿用测试包现有 `mxt_test:technique/sword` 互斥夹具）。

## 10. 关键文件索引

- 定义：`src/main/java/com/iafenvoy/mxt/data/cultivation/CultivationTechnique.java`
- 绑定：`src/main/java/com/iafenvoy/mxt/data/item/TechniqueBinding.java`
- 学习事务：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/TechniqueService.java`
- 物品入口：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/TechniqueItemService.java`
- 授予：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/CultivationGrantService.java`
- 倍率：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/CultivationAffinity.java`
- 被动属性：`src/main/java/com/iafenvoy/mxt/runtime/ability/PassiveAttributeService.java`
- 附件：`src/main/java/com/iafenvoy/mxt/attachment/SpiritIdentityAttachment.java`
- 事件：`src/main/java/com/iafenvoy/mxt/event/TechniqueLearnEvent.java`
- 注册：`src/main/java/com/iafenvoy/mxt/registry/{MxtResourceKeys,MxtDatapackRegistries,MxtItems}.java`
- 展示：`src/main/java/com/iafenvoy/mxt/data/item/ItemBindingTooltipAppender.java`、`src/main/java/com/iafenvoy/mxt/screen/information/InformationManager.java`
- 等级链参考（§8）：`data/cultivation/RealmStage.java`、`runtime/cultivation/CultivationService.java`、`attachment/CultivationAttachment.java`、`data/Sect.java`、`data/quality/ItemQuality.java`、`data/forging/ForgingBlueprint.java`、`data/Tribulation.java`
- 测试夹具：`src/test-mod/resources/data/mxt_test/mxt/cultivation_technique/{qingxiao_breathing_manual,sword_manual,body_manual}.json`、`.../mxt/technique_binding/qingxiao_breathing_jade_slip.json`
- 相关文档：`docs/数据包格式.md:706-717, 783-790`、`docs/item-bindings.md:10-16, 65-78`、`docs/guide/datapack/cultivation.md:5`、`docs/curios槽位.md`、`E:\Website\docs\docs\mod\mxt\datapack\json\cultivation_technique.md`、`...\technique_binding.md`、`...\player-guide\curios-slots.md`
