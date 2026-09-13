# 功法（`cultivation_technique`）数据驱动与功能审计

审计基准：MC 26.1.2 / NeoForge 26.1.2.99 / KubeJS 26.1.2-8.0.4。
结论均来自直接读代码，行号对应当前工作区状态（2026-09-12）。本文件是"先记录、后动手"的持久化笔记，便于后续完善功法时不必重新翻代码。

> 变更记录
> - 2026-09-12：新增本文件。
> - 2026-09-12：**删除 Curios 功法槽设计**（§5、T3 已解决），新增 §8 功法等级链条调研。
> - 2026-09-12：按 §8 的方案 B 落地**数据层**：新增 `skill_stage` 注册表（`skill` + `next_stage` + `damage_multiplier`），功法新增 `default_stage` 与 `stage_abilities`；运行期（玩家当前水平、链校验、`damage_multiplier` 消费）仍未接入。
> - 2026-09-12：定下 `stage_abilities` 的 key 为**最低要求**；补上链条顺序能力（`ServerCache` 推导链首与 rank、校验多首级/跨链/成环/缺失）、`isStageAtLeast` 比较与 `SkillStageService.unlockedAbilities` 查询。玩家当前水平状态仍缺。
> - 2026-09-12：新增 `advance_conditions`（key = 晋升到的等级，value = 该级所需条件），并补上 `nextStage` / `advanceCondition` / `canAdvance` 查询与缓存期链校验。仍缺玩家当前水平状态，因此还没有真正的"升级"执行路径。
> - 2026-09-12：新增 `mxt:trigger` 数据包注册表（事件规则：信号匹配器 + 条件 + 实体行为），`TriggerDispatcher.publish` 统一派发，规则按信号建索引。可用于"触发条件就给某个 resource +1"这类需求。

## 0. 边界澄清（避免概念混淆）

- **功法 = `cultivation_technique`**：玩家学会后常驻生效的修炼法门。
- **手法 = `forging_method`**：锻造单步效果，与功法无关（`ForgingBlueprint.allowed_methods`、`ToolBinding` 那一套）。
- **修炼行为 = `cultivate_action`**：与功法**无关联**。`CultivationModeService.resolveAction`（`runtime/cultivation/CultivationModeService.java:71-77`）只按 `default` 标记 / 玩家已选 / 注册表第一个来解析，不读 `learnedTechniques`。
- 功法自身只有两个数据驱动注册表：`cultivation_technique`（定义）与 `technique_binding`（载体绑定）。掌握程度由第三张表 `skill_stage` 表达——它按自由 `skill` 标识分链，可被多个功法共用（§8）。

## 1. 注册表清单

| 注册表 | JSON 位置 | Codec | 注册点 | 备注 |
| --- | --- | --- | --- | --- |
| `mxt:cultivation_technique` | `data/<ns>/mxt/cultivation_technique/<id>.json` | `CultivationTechnique.DIRECT_CODEC`（`data/cultivation/CultivationTechnique.java:26-34`） | `registry/MxtDatapackRegistries.java:69` | 同一 codec 兼作同步 codec（`MxtDatapackRegistries.java:91-94`），客户端可读，故 tooltip 能用 `context.registries()` 解析 |
| `mxt:technique_binding` | `data/<ns>/mxt/technique_binding/<id>.json` | `TechniqueBinding.CODEC`（`data/item/TechniqueBinding.java:23-28`） | `registry/MxtDatapackRegistries.java:84` | 走 `ItemMatcher`，载体是**现有物品**，不创建书籍/玉简 |
| `mxt:skill_stage` | `data/<ns>/mxt/skill_stage/<id>.json` | `SkillStage.DIRECT_CODEC`（`data/cultivation/SkillStage.java:26-38`） | `registry/MxtDatapackRegistries.java:70` | 技能水平链的单级：`skill`（链身份，自由 `Identifier`）+ `next_stage` + `damage_multiplier`；解析期校验倍率有限非负 |

两者都支持 `#mxt:disabled` 标签禁用：`MxtDatapackRegistries.java:52,100-109,152-174`；功法侧入口检查在 `TechniqueService.java:29-30`。

## 2. `cultivation_technique` 字段与真实消费者

| 字段 | 类型 / 默认 | 实际功能 | 消费点 |
| --- | --- | --- | --- |
| `grade` | String `common` | **无任何读取点**，纯存储元数据（等级方案见 §8） | 全仓 `.grade()` 零调用（唯一同名调用是 `SpiritStoneVein.Grade`，无关） |
| `learn_condition` | `EntityCondition` `always_true` | 学习时校验，失败 → `CONDITIONS` | `runtime/cultivation/TechniqueService.java:50-51` |
| `exclusive_tags` | `Identifier[]` `[]` | 互斥：汇总已学功法的标签集合，与新功法求交集，命中 → `CONFLICT` | `TechniqueService.java:33-36`；另见 `runtime/cultivation/CultivationIdentityService.java:46-48`、`TitleService.java:31-33`（同型机制） |
| `cultivation_modifier` | `NumberProvider` `1` | 修炼速度倍率，**逐功法连乘**进 affinity；非有限或负数 → NaN → 修炼中断 `INVALID_FORMULA` | `runtime/cultivation/CultivationAffinity.java:49-53, 76-80`；被 `CultivationActionService.java:99-101, 120-122` 调用 |
| `passive_modifiers` | `AttributeEntry[]` `[]` | 常驻被动属性，修饰符来源标识 `technique` | `runtime/ability/PassiveAttributeService.java:107-108` |
| `granted_abilities` | `HolderOrTag<ability>[]` `[]` | 学习后授予能力；支持 `#tag`，经 `RegistryCodecs.resolve` 展开去重；source = `mxt:grant/technique/<ns>/<path>`，重算时先撤销全部 `grant/` 来源再重建。**始终生效** | `runtime/cultivation/CultivationGrantService.java:36-37, 45-47, 60-70` |
| `default_stage` | `Holder<skill_stage>`，可选 | 该功法水平链的入口等级；`CultivationTechnique.java:46`。**运行期尚未读取** | ——（仅编解码与测试断言） |
| `stage_abilities` | `Map<Holder<skill_stage>, HolderOrTag<ability>[]>` `{}` | 按水平解锁的能力，value 可写单值/`#tag`/数组（`Codec.unboundedMap` + `RegistryCodecs.holderOrTagList`，`CultivationTechnique.java:47-48`）。**运行期尚未读取**，且语义（累积 vs 严格）未定 | ——（仅编解码与测试断言） |
| `advance_conditions` | `Map<Holder<skill_stage>, EntityCondition>` `{}` | **晋升到** key 所写等级所需的条件（2026-09-12 新增）：链条由多个功法共用，攀爬条件属于本功法；value 支持单条件或数组。查询 API 见 `SkillStageService.nextStage/advanceCondition/canAdvance`。**运行期尚未消费**（没有玩家当前水平状态） | `runtime/cultivation/SkillStageService.java`；缓存校验在 `ServerCache.validateTechniqueChains` |

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
| T1 | `grade` 无消费者：不显示、不影响掉落/突破/学习条件；`docs/模块实现审计.md:74` 自己也写"仅存储/展示候选" | `CultivationTechnique` 无 `.grade()` 调用点。注意 `grade` 与新的 `skill_stage` **不是**同一件事：前者是自由字符串元数据，后者才是可推进的水平链 |
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

### 8.0 选定并落地的方案（2026-09-12，仅数据层）

调研后选的是"照抄 `realm_stage` 的模型，但链身份用自由 `Identifier`"（原方案 B）：

- 新注册表 `mxt:skill_stage`（`data/cultivation/SkillStage.java:26-38`，注册于 `MxtDatapackRegistries.java:70`）：`skill: Identifier`（必填，链身份）+ `next_stage: Holder<SkillStage>`（可选，单向指针）+ `damage_multiplier: double`（默认 `1.0`，解析期校验有限非负）。与 `realm_stage` 的 `resource`+`next_realm` 同形，唯一区别是链身份不是注册表条目，所以多个功法（以及将来的其它系统）可以共用一条链。
- `CultivationTechnique` 新增 `default_stage`（可选，链入口，`:46`）与 `stage_abilities`（`Map<Holder<skill_stage>, HolderOrTag<ability>[]>`，value 支持单值 / `#tag` / 数组，`:47-48`）。为了让链可达，`stage_abilities` 非空而 `default_stage` 缺失会在解析期报错（`:51-54`）。
- 地图值选了 `Codec.unboundedMap` 而不是 `CollectionCodecs.map`：后者（`AutoIgnoreMapCodec.java:31-38`）会**静默丢弃**解码失败的键值、只打一行 warn，与"加载期把所有问题收集起来报出"的既有策略冲突。
- **解锁语义已定（2026-09-12）**：`stage_abilities` 的 key 是**最低要求**——当前水平位于该级或其之后的级别时条目生效（能力累积解锁，不逐级替换）。为支持这一点，链条顺序由 `ServerCache.rebuildSkillChains`（`runtime/ServerCache.java`）在服务端启动/数据包重载时推导：以"没有任何一级指向它"的那一级为链首，沿 `next_stage` 编号 rank（链首为 `0`），并拒绝多首级、跨链指向、成环、指向缺失条目，以及"功法的 `stage_abilities` key 与 `default_stage` 不在同一条链"。比较与查询接口：`isStageAtLeast(current, required)`、`rankForStage`、`skillForStage`，以及 `SkillStageService.unlockedAbilities(technique, current)`（展开 `#tag`、去重、按最低要求过滤）。
- 测试夹具：`data/mxt_test/mxt/skill_stage/{sword_art_1,sword_art_2}.json` + `sword_manual.json` 的 `default_stage`/`stage_abilities`（单值、数组两种写法各一）/`advance_conditions`（对 `sword_art_2` 设条件），`MxtTestMod` 有一段静默断言（链身份、倍率、条目数、键的 `skill` 一致、rank 0/1、`isStageAtLeast` 双向、level1 → 1 个能力 / level2 → 2 个能力、`nextStage` 到顶为空、`advanceCondition` 对未声明级别为 `always_true`）。
- **未做**（下一步见 §9）：玩家当前水平状态（所以按水平解锁与晋升都还没接进运行时）、`damage_multiplier` 的消费点。链顺序、校验与两个查询已就绪，不再是缺口。

### 8.1 唯一的完整等级链条：`realm_stage`（境界链）

- 数据模型 `data/cultivation/RealmStage.java:30-63`：`resource`（**必填**）、`next_realm`（单向 next 指针）、`breakthrough_exp`（本级下限）、`max_experience`（本级上限）、`breakthrough`（条件组）、`auto_breakthrough`、`costs`、`ability_requirements`、`tribulation`、`passive_modifiers`、`success_action`/`fail_action`、`aura_share_weight`、`cultivate_condition`。构造期还校验常量的 `breakthrough_exp <= max_experience`（`:39-44`）。
- 推进逻辑 `runtime/cultivation/CultivationService.java`：
  - `next()`（`:130-141`）从当前级沿 `next_realm` 走**一跳**，并强制链身份一致（链以 `mxt:cultivation` 档案为键，`RealmStage.cultivation` 指向档案）；凡人态改用该档案的 `first_realm` 与 `start_exp`（2026-09-12 从 `Resource` 迁出，见 `research/audit/resource-cultivation-split.md`）。
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

1. **掌握程度的运行时（§8.0 的下一步）**：
   - 玩家当前水平状态：新附件字段（按功法或链键存 `Holder<SkillStage>`），或复用 `SpiritIdentityAttachment`。链顺序、校验、"最低要求"解锁查询与晋升条件查询（`nextStage`/`advanceCondition`/`canAdvance`）都已就绪，缺的只是这个状态。
   - 接入授予流程：`CultivationGrantService.recalculate` 目前只处理 `granted_abilities`；按水平解锁应改为"该功法已授予的 = `granted_abilities` ∪ `SkillStageService.unlockedAbilities(technique, 当前水平)`"，并沿用 `mxt:grant/technique/...` 来源整体撤销重算。
   - 晋升执行路径：用 `canAdvance` 做前置校验 + 写状态 + 重算授予/被动属性；练习数值怎么涨（经验/修炼/事件）仍待定，可以挂到新的 `mxt:trigger` 事件规则上。
   - `damage_multiplier` 消费点：目前没有统一的技能伤害管线（伤害来自 `mxt:damage` 动作里的 `NumberProvider`），要么做成公式变量（需要 `FormulaContext` 携带水平主体），要么在 `DamageAction` 里按施法者水平乘算。
   - `grade`（T1）与 `skill_stage` 的关系也要一次定清：建议 `grade` 只做展示/掉落元数据，数值进阶交给水平链。`docs/模块实现审计.md:74` 的错误措辞已在本轮修正。
2. **T2 失败反馈**：把 `Result`/`Failure` 转成 actionbar 文案（`TechniqueService.Failure` 已 5 种，lang 需补 5 个键），或复用 `TechniqueLearnEvent.Post` 让内容包自行提示。
3. **T6 遗忘入口**：`TechniqueService.forget` + 重算授予/被动属性 + 事件（`TechniqueForgetEvent`）+ 互斥的追溯校验策略。
4. **T7 信号与 API**：新增 `TriggerSignals.TECHNIQUE_LEARN`（或复用 `TechniqueLearnEvent`）与 `MxtKubeJsApi` 的功法查询/授予/遗忘方法。
5. **T4/T8 收尾**：删掉 `CultivationAffinity` 的死参数，补 `CONFLICT`/`ALREADY_LEARNED`/`CONDITIONS` 的学习事务测试（沿用测试包现有 `mxt_test:technique/sword` 互斥夹具）。

## 10. 关键文件索引

- 定义：`src/main/java/com/iafenvoy/mxt/data/cultivation/CultivationTechnique.java`
- 水平链：`src/main/java/com/iafenvoy/mxt/data/cultivation/SkillStage.java`
- 水平查询：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/SkillStageService.java`（解锁能力 + 晋升条件）
- 事件规则：`src/main/java/com/iafenvoy/mxt/data/trigger/TriggerRule.java`、`src/main/java/com/iafenvoy/mxt/runtime/trigger/TriggerRuleService.java`
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
- 测试夹具：`src/test-mod/resources/data/mxt_test/mxt/cultivation_technique/{qingxiao_breathing_manual,sword_manual,body_manual}.json`、`.../mxt/technique_binding/qingxiao_breathing_jade_slip.json`、`.../mxt/skill_stage/{sword_art_1,sword_art_2}.json`
- 相关文档：`docs/数据包格式.md`（`skill_stage` 段 + `cultivation_technique` 字段）、`docs/guide/datapack/overview.md:56`、`docs/模块实现审计.md:74-75`、`docs/item-bindings.md:10-16, 65-78`、`docs/guide/datapack/cultivation.md:5`、`docs/curios槽位.md`、`E:\Website\docs\docs\mod\mxt\datapack\json\{cultivation_technique,skill_stage,index}.md`、`...\datapack\overview.md`、`...\player-guide\curios-slots.md`
