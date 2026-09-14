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
> - 2026-09-12：按用户要求把功法的 `stage_abilities` + `advance_conditions` **合并**为单一 `configuration` map（key = 水平，条目 = 必填 `condition` + 选填 `ability`）；校验改为从 `default_stage` 沿链遍历：入口级不要求条目（写了也只取 `ability`），其后每一级必须有条目，且不可出现走不到的 key。
> - 2026-09-13：**补齐熟练度晋升运行时并做成数据驱动**（§8.0.1）：`SkillStage` 加 `mastery`（到达该级所需熟练度，链上非递减由缓存校验）、`CultivationTechnique` 加 `mastery_resource`、`SpiritIdentityAttachment` 加 `technique_stages`、新增 `TechniqueMasteryService`（晋升 + 发布 `mxt:technique_stage`）、`CultivationGrantService` 按当前水平累积授予。至此"没有玩家当前水平状态"不再是缺口。
> - 2026-09-13：按用户要求给 `damage_multiplier` 加 `//TODO`（暂不实现），并复核全部审计条目；新增 T10（`damage_multiplier` 无消费者）、T11（存的水平不校验归属，改链后会卡死）、T12（`technique_stages` 残留），补全 T4（`roots` 也是死参数），修掉 `docs/模块实现审计.md:113` 的"激活状态"错误措辞与 `ServerCache` 里一句不准确的注释。
> - 2026-09-13：**做掉 T2（失败反馈）**，并顺带把 `ItemQualityService.canUse` 从"只回 bool"改成能回报具名原因（见 §11）：新增 `ItemQualityService.Failure{BINDING_CONDITIONS,QUALITY_CONDITIONS,QUALITY_GROUP}` 与 `check(...)`（`canUse` 变成 `check(...).isEmpty()`，所有既有调用点不动），门槛与学习事务的拒绝都发 actionbar（`actionbar.mxt.item.cannot_use` + `actionbar.mxt.technique.failed` 两条消息族），en/zh 共补 10 个键。测试包补了门槛与学习事务的拒绝断言。
> - 2026-09-13：**新增功法图示列表界面**（见 §12）：`TechniqueProgress`（公共侧、可服务端测试的行模型 + 两种进度口径）、`TechniquePanelScreen`（单列可滚动列表：图标槽 + 等级名/序号 + 熟练度进度条 + 分隔线），入口是"默认不绑定"的按键 `key.mxt.technique_panel` **加**人物信息面板右上角按钮；进度口径由客户端配置 `config.mxt.client.techniques.progress_mode` 切换。背景留作美术素材（`textures/gui/classic/technique_panel.png`，240×300，当前是占位图），分隔线与进度条按用户要求用代码画。
> - 2026-09-13：用户调试功法面板时发现**信息面板不停报 `Information entry contains more than one line`**（见 §13）。根因不是这次新增的界面，而是 `InformationPanelScreen` 把"所有条目标题的最大宽度"当成每一行的标题列宽，一条长标题会把**所有**数值列挤到换行并被省略号截断；加上条目每次刷新都重建、`overflowReported` 随之复位，于是每秒重复报错。已改成**按需分配列宽**（`InformationHelper.columns`，纯算术、可服务端审计）、诊断按内容去重，并补上测试包缺失的 5 个显示名（含日志里那条 `realm_stage.mxt_test.spirit_power_refining`）+ 新增"面板会打印名字的定义都必须有翻译键"的审计。
> - 2026-09-13：用户看了功法面板截图后反馈"字看不清而且顶格"（见 §12.5）。查 MC 源码确认是 `ObjectSelectionList` 自带的界面装饰：列表底色贴图把手画背景压成平灰、首行上方那条原版分隔线紧贴第一行文字。已把 `extractListBackground` / `extractListSeparators` 置空、把 `scrollBarX()` 收进面板内、空状态文案改用正常文字色并内缩。
> - 2026-09-13：**把图标统一成一套**（见 §14）：`HotbarIcon` 提升为双端通用 `IconReference`（内部 `Either<Identifier, ItemStackTemplate>`），新增客户端渲染 Helper `IconRenderer`，`ability`/`resource`/`badge`/`forging_method`/`cultivation_technique` 全部改用它，并消掉热键栏与热键配置界面里重复的那段渲染逻辑。

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
| `mxt:skill_stage` | `data/<ns>/mxt/skill_stage/<id>.json` | `SkillStage.DIRECT_CODEC`（`data/cultivation/SkillStage.java:36-41`） | `registry/MxtDatapackRegistries.java:70` | 技能水平链的单级：`skill`（链身份，自由 `Identifier`）+ `next_stage` + `mastery`（到达所需熟练度，默认 `0`）+ `damage_multiplier`；解析期校验倍率有限非负，缓存重建时校验链顺序与 `mastery` 非递减 |

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
| `default_stage` | `Holder<skill_stage>`，可选 | 该功法水平链的入口等级（`CultivationTechnique.java:48`）。运行期作为"当前水平"的缺省值：附件里没有该功法的记录时即为入口级 | `SkillStageService.currentStage`；`TechniqueMasteryService` |
| `mastery_resource` | `Holder<resource>`，可选 | 衡量该功法熟练度的存储数值。写了它才会自动晋升（要求该资源 ≥ 下一级的 `mastery` 且下一级 `condition` 成立）；不写则永不晋升。缺 `default_stage` 而写了它在解析期报错 | `TechniqueMasteryService.promote` |
| `configuration` | `Map<Holder<skill_stage>, StageConfiguration>` `{}` | 功法对共用水平链每一级的注释（2026-09-12 合并原 `stage_abilities` + `advance_conditions`）：`condition`（**必填**，到达该级的条件）与 `ability`（选填，该级授予的能力，单值/`#tag`/数组，按最低要求累积生效）。查询 API：`SkillStageService.nextStage/advanceCondition/canAdvance/unlockedAbilities`，运行期由 `TechniqueMasteryService`（晋升）与 `CultivationGrantService`（按当前水平授予）消费 | `runtime/cultivation/SkillStageService.java`；链校验在 `ServerCache.validateTechniqueChains` |

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
- 触发器信号：`data/trigger/TriggerSignals.java` 有 `tick/attack/hurt/kill/block_break/block_use/item_use/equip/death/breakthrough`，2026-09-13 新增 `technique_stage`（晋升成功后发布，载荷 `technique` + `stage` rank），但**没有**"学会功法"信号；数据包技能无法用 `mxt:js` 等待学习事件。
- 运行时代码域 API：`compat/kubejs/MxtKubeJsApi.java` 只有 `ability/curse/useAbility/applyCurse/removeCurse/reclaimSoul/tryBreakthrough/addCultivation/tryConsumeResources/aura/addAuraBox/removeAuraArea/publishTrigger`，**无功法相关方法**（不能查询、授予或遗忘功法）。

## 7. 缺口与可疑点

| 编号 | 问题 | 证据 |
| --- | --- | --- |
| T1 | `grade` 无消费者：不显示、不影响掉落/突破/学习条件；`docs/模块实现审计.md:75` 自己也写"`grade` 仍仅存储" | `CultivationTechnique` 无 `.grade()` 调用点（全仓唯一 `.grade()` 命中是无关的 `SpiritStoneVein.Grade`）。注意 `grade` 与 `skill_stage` **不是**同一件事：前者是自由字符串元数据，后者才是可推进的水平链 |
| T2 | ~~学习失败**零反馈**~~ → **已解决（2026-09-13）**。原始情况：`TechniqueItemService.use` 丢弃 `Result`，lang 里也没有任何功法失败提示键；不过物品门槛那一层其实**有**反馈——`ItemQualityService` 以 `HIGHEST` 优先级监听同一个 `RightClickItem`，失败时取消事件并发一条**笼统**的 `actionbar.mxt.item.cannot_use`（无原因）。修订后（见 §11）：门槛改成返回具名原因 + 每条原因一句文案，学习事务失败也发 `actionbar.mxt.technique.failed` + 5 条原因文案 | `runtime/item/ItemQualityService.java`、`runtime/cultivation/TechniqueItemService.java`、`assets/mxt/lang/{en_us,zh_cn}.json` |
| T3 | ~~Curios 功法槽是死的~~ → **已解决（删除整套功法槽，2026-09-12，见 §5）**。原始问题留档：唯一读取已装备 Curios 的是 `AbilityEventBridge.syncCuriosAbilities`，它只读物品的 `mxt:item_abilities` 组件、不读 `technique_binding`，所以玉简放进功法槽既不学习也无效果 | `runtime/ability/AbilityEventBridge.java:205-223`；`CuriosIntegration.equipped` 调用点仅此一处 + 两个渲染器（`BackWeaponRenderer.java:39`、`BeltWeaponRenderer.java:37`） |
| T4 | **死参数**：`CultivationAffinity.multiplier(...)` 两个重载的 `Function<Identifier, Optional<CultivationTechnique>> techniques` 完全未使用，实际遍历 `spirit.learnedTechniques()`；同两个重载的 `Function<Identifier, Optional<SpiritRoot>> roots` 也未被引用（只有 `abilityMultiplier` 用它）；调用方仍在传解析器 | `CultivationAffinity.java:35-38, 62-65`；`CultivationActionService.java:100, 121` |
| T5 | ~~**审计文档与代码不一致**~~ → **已解决（2026-09-13）**：`docs/模块实现审计.md:75` 原写"主动功法…已接入"，`:113` 原写"激活状态已接入"，两者都已改写为实际行为（`CultivationTechnique` 没有主动/被动字段，能主动的只是 `granted_abilities` 里的主动型 ability；`TechniqueBinding` 没有激活/装备状态字段）。留档：核对 §2/§3 逐字段确认 | `data/item/TechniqueBinding.java:20-22`（`items`/`technique`/`quality_group`/`conditions`，无 active 字段） |
| T6 | **无遗忘/拆卸入口**：只有 `addLearnedTechnique` / `setLearnedTechniques` 直接改列表，`TechniqueService` 无 forget；互斥只在学习瞬间校验，换数据包或直写附件后可同时持有互斥功法。撤销侧其实已就绪：`CultivationGrantService.recalculate` 先撤销全部 `grant/` 来源再重建，所以调用方改完列表再调它，授予会跟着掉；缺的只是 API + 事件 + 互斥的追溯策略 | `SpiritIdentityAttachment.java:108-119`；`TechniqueService` 仅 `learn`；`CultivationGrantService.java:33-55` |
| T7 | **有"晋升"信号但仍无"学会/遗忘"信号、无脚本 API**（见 §6）：内容包可以响应 `mxt:technique_stage`，但无法感知"学会功法"，脚本也无法查询/授予/遗忘功法 | `TriggerSignals.java`；`MxtKubeJsApi.java` 全文 |
| T8 | **测试覆盖薄**：`MxtTestMod.java:233-237` 只断言 binding 能解析、`technique` id 正确、conditions 无 description；`sword_manual.json`/`body_manual.json` 共享 `mxt_test:technique/sword` 互斥标签却无用例断言 `CONFLICT`，也没有 `ALREADY_LEARNED`、`learn_condition` 失败、学习失败 `Result` 值的断言（水平链与熟练度晋升的断言已补齐：`:239-275`、`:1106-1132`） | `src/test-mod/java/.../MxtTestMod.java`；`src/test-mod/resources/data/mxt_test/mxt/cultivation_technique/*.json` |
| T9 | `exclusive_tags` 只与"学习前已持有的集合"比较，不检查同批/回溯一致性；本身是自定义分类键（`Identifier` 相等比较），**不是**注册表 tag | `TechniqueService.java:33-36` |
| T10 | `damage_multiplier` **无消费者**：只有 codec / 校验 / `toString` / 测试断言读它。原因是全仓没有统一的技能伤害管线——伤害来自 `mxt:damage` 动作自己的 `NumberProvider`。代码里已加 `//TODO::Consume damage_multiplier`（`SkillStage.java:39-41`），文档也标注了"暂未消费" | `grep damageMultiplier` 仅命中 `SkillStage.java` 自身与 `MxtTestMod.java:244` |
| T11 | 状态未校验归属：`SkillStageService.currentStage` 直接把附件里存的水平返回（缺失才回退 `default_stage`），不检查它是否属于该功法当前的链。数据包改链后可能留下"别条链的水平"：此时 `isStageAtLeast` 因链不同返回 false（不会错误解锁），但 `nextStage` 也返回 empty，功法**永久卡住**且没有任何日志 | `SkillStageService.java:44-47, 71-76`；`ServerCache.isStageAtLeast:167-171` |
| T12 | **待定的语义（非 bug）**：`setLearnedTechniques` 直接改列表时不会清 `technique_stages` 里该功法的记录。当前行为是"遗忘后重新学会会接着原来的水平"，因为 `TechniqueMasteryService` 只遍历 `learnedTechniques()`，不会为未学功法晋升。做 T6 的 `forget` 时需要明确选哪一种：重置到入口级，还是保留进度 | `SpiritIdentityAttachment.java:84-94, 108-119`；`TechniqueMasteryService.tick` |

## 8. 功法等级：可复用的等级链条调研（只读，2026-09-12）

问题：现有系统里有没有可以**直接**给 `CultivationTechnique.grade` 复用的"等级链条系统"？
结论：**没有可直接复用的通用等级链**——只有一个完整的境界链，加几个内联的阈值阶梯，且没有任何通用的链抽象类。

### 8.0 选定并落地的方案（2026-09-12，仅数据层）

调研后选的是"照抄 `realm_stage` 的模型，但链身份用自由 `Identifier`"（原方案 B）：

- 新注册表 `mxt:skill_stage`（`data/cultivation/SkillStage.java:36-41`，注册于 `MxtDatapackRegistries.java:70`）：`skill: Identifier`（必填，链身份）+ `next_stage: Holder<SkillStage>`（可选，单向指针）+ `mastery: NumberProvider`（默认 `0`，2026-09-13 加入）+ `damage_multiplier: double`（默认 `1.0`，解析期校验有限非负）。与 `realm_stage` 的 `resource`+`next_realm` 同形，唯一区别是链身份不是注册表条目，所以多个功法（以及将来的其它系统）可以共用一条链。
- `CultivationTechnique` 新增 `default_stage`（可选，链入口）与 `configuration`（`Map<Holder<skill_stage>, StageConfiguration>`；条目 = 必填 `condition` + 选填 `ability`）。为了让链可达，`configuration` 非空而 `default_stage` 缺失会在解析期报错。
- 地图值选了 `Codec.unboundedMap` 而不是 `CollectionCodecs.map`：后者（`AutoIgnoreMapCodec.java:31-38`）会**静默丢弃**解码失败的键值、只打一行 warn，与"加载期把所有问题收集起来报出"的既有策略冲突。
- **语义（2026-09-12 定稿）**：`ability` 是**最低要求**——当前水平位于该级或其之后时生效（累积解锁）；`condition` 是**到达**该级的条件。链条顺序由 `ServerCache.rebuildSkillChains` 在服务端启动/数据包重载时推导（链首 + rank，拒绝多首级/跨链/成环/缺失）。`ServerCache.validateTechniqueChains` 再从 `default_stage` 沿链遍历：入口级**不要求**条目（写了则只取 `ability`，`condition` 不作为门槛），**其后每一级都必须有条目**，且不允许出现从入口走不到的 key。比较与查询接口：`isStageAtLeast`、`rankForStage`、`skillForStage`、`SkillStageService.unlockedAbilities/nextStage/advanceCondition/canAdvance`。
- 测试夹具：`data/mxt_test/mxt/skill_stage/{sword_art_1,sword_art_2}.json` + `sword_manual.json` 的 `default_stage`/`configuration`（入口级也写了条目、二级 `ability` 用数组写法），`MxtTestMod` 有一段静默断言（链身份、倍率、条目数、能力总数、键的 `skill` 一致、rank 0/1、`isStageAtLeast` 双向、level1 → 1 个能力 / level2 → 2 个能力、`nextStage` 到顶为空、`advanceCondition` 与条目里的 `condition` 同一实例）。熟练度相关的夹具与端到端断言见 §8.0.1。
- 负路径实验（2026-09-12）：临时删掉 `sword_art_2` 的条目后启动服务端，缓存重建抛 `Technique mxt_test:sword_manual does not configure the skill stage mxt_test:sword_art_2`，服务器不启动、审计不执行；夹具随后还原并重新同步 build 产物。
- **未做**（下一步见 §9）：`damage_multiplier` 的消费点、`grade` 的语义、遗忘入口与学习失败反馈。链顺序、校验、两个查询与晋升运行时均已就绪，不再是缺口。

### 8.0.1 熟练度晋升运行时（2026-09-13 落地）

用户要求"加上熟练度运行时，并且数据驱动"，因此 §8.0 的"没有玩家当前水平状态"在本轮补齐：

- `SkillStage` 新增 `mastery: NumberProvider`（默认 `Constant(0)`），语义 = **到达该级所需的熟练度**，属于水平本身（共享链对所有人量同一段爬升）。`ServerCache.rebuildSkillChains` 在推导 rank 的同时拒绝"下一级 `mastery` 比上一级更低"的链（`IllegalStateException: Skill chain ... lowers its mastery requirement at stage ...`，服务器不启动）。
- `CultivationTechnique` 新增 `mastery_resource: Optional<Holder<Resource>>`（缺 `default_stage` 而写了它在解析期报错）。它只回答"用什么衡量"，数值怎么涨由内容包决定：`mxt:trigger` 规则、修炼档案或脚本都行——这就是"数据驱动"的落点，模组不认识熟练度是什么。
- 状态存储：`SpiritIdentityAttachment.technique_stages: Map<Holder<CultivationTechnique>, Holder<SkillStage>>`（JSON `technique_stages`）。键是功法而非链，因为链可以共享而"这条链上我爬到哪"属于持有者；**没有记录 = 仍在 `default_stage`**，所以入口级不需要写入，旧存档也不会缺字段。
- 晋升执行：`runtime/cultivation/TechniqueMasteryService.tick(entity)` 遍历已学功法，单次 pass 每功法最多晋升 64 级（防呆上限），条件是"有 `mastery_resource` + 有下一级 + 资源值 ≥ 下一级 `mastery` + 下一级 `condition` 成立"；提交后再发布 `mxt:technique_stage` 信号（`TriggerContext` 公式值 `stage` = rank，扩展值 `technique` = 功法 id 字符串），因此反应方读到的是新水平。整个 pass 只在真晋升时重算一次授予。
- 授予：`CultivationGrantService` 对每个已学功法把 `grantedAbilities()` 与 `SkillStageService.unlockedAbilities(definition, currentStage)` 合并（累积最低要求）后统一以 `mxt:grant/technique/<ns>/<path>` 来源授予。
- 钩子：`AbilityEventBridge` 的 `% 20 == 0` 周期块里调用 `tick`；同时修炼档案的回复循环改为只遍历 `mxt:cultivation` 档案并跳过没有存储条目的数值（不再遍历全部 resource 数值，见 `research/audit/resource-cultivation-split.md`）。
- 测试夹具/断言：`skill_stage/sword_art_2.json` 加 `"mastery": 10`、`resource/sword_mastery.json`（不属于任何修炼档案，纯存储）、`trigger/sword_mastery_from_break.json`（破方块 +5）、`sword_manual.json` 加 `mastery_resource`；`MxtTestMod` 端到端跑一遍"猪学功法 → 熟练度 5 不晋升 → 10 晋升到 `sword_art_2` 且 `qingxiao_firebolt` 被授予"。
- 负路径实验（2026-09-13）：临时把 `sword_art_1` 的 `mastery` 改成 `20`（高于下一级的 `10`），服务端启动即抛 `IllegalStateException: Skill chain mxt_test:sword_art lowers its mastery requirement at stage mxt_test:sword_art_2`，审计不执行；夹具已还原并重新同步 build 产物。

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

## 9. 建议动手顺序

1. ~~**掌握程度的运行时（§8.0 的下一步）**~~ → **已于 2026-09-13 完成，见 §8.0.1**：状态（`SpiritIdentityAttachment.technique_stages`）、晋升执行（`TechniqueMasteryService`）、按水平累积授予（`CultivationGrantService`）、`mxt:technique_stage` 信号都已落地；数值怎么涨刻意留给内容包（`mxt:trigger` 规则 / 修炼档案 / 脚本），这就是"数据驱动"的落点。本条余下的只有两项：
   - `damage_multiplier` 消费点（**T10，已按用户要求留 TODO 推后**）：目前没有统一的技能伤害管线（伤害来自 `mxt:damage` 动作里的 `NumberProvider`），要么做成公式变量（需要 `FormulaContext` 携带水平主体），要么在 `DamageAction` 里按施法者水平乘算。TODO 写在 `SkillStage.java` 类注释上方。
   - `grade`（T1）与 `skill_stage` 的关系也要一次定清：建议 `grade` 只做展示/掉落元数据，数值进阶交给水平链。`docs/模块实现审计.md:74` 的错误措辞已修正。
2. ~~**T2 失败反馈**~~ → **已于 2026-09-13 完成，见 §11**。门槛与学习事务两级都回具名原因并发 actionbar；`ItemQualityService.canUse` 保持签名不变的同时新增 `check(...)` 返回 `Optional<Failure>`。
3. **T6/T12 遗忘入口**：`TechniqueService.forget` + 重算授予/被动属性 + 事件（`TechniqueForgetEvent`）+ 互斥的追溯校验策略，并顺带定下 T12 的语义（遗忘是否重置水平）。
4. **T7 信号与 API**：`mxt:technique_stage` 已就绪；仍缺"学会功法"信号（`TriggerSignals.TECHNIQUE_LEARN`，或复用 `TechniqueLearnEvent`）与 `MxtKubeJsApi` 的功法查询/授予/遗忘方法。
5. **T4/T8 收尾**：删掉 `CultivationAffinity.multiplier` 的 `roots` 与 `techniques` 两个死参数，补 `CONFLICT`/`ALREADY_LEARNED`/`CONDITIONS` 的学习事务测试（沿用测试包现有 `mxt_test:technique/sword` 互斥夹具）。
6. **T11 归属校验（低优先，无兼容要求时可不动）**：`currentStage` 加一次"存的水平是否属于该功法当前链"的判断，不属于就回退 `default_stage` 并 `LOGGER.warn`，避免改链后功法静默卡死。
7. 展示层：**功法列表界面已完成（§12）**——图标槽位、等级（名 + 序号）、两种口径的熟练度进度条都已就绪；剩下的是给 `cultivation_technique` 加 icon 字段并在 `TechniquePanelScreen` 的 `//TODO::` 处叠图。tooltip 展示（T2 之外的等级/倍率/能力列表）仍未做。

## 10. 关键文件索引

- 定义：`src/main/java/com/iafenvoy/mxt/data/cultivation/CultivationTechnique.java`
- 水平链：`src/main/java/com/iafenvoy/mxt/data/cultivation/SkillStage.java`
- 水平查询：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/SkillStageService.java`（解锁能力 + 晋升条件）
- 熟练度晋升：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/TechniqueMasteryService.java`
- 列表行模型：`src/main/java/com/iafenvoy/mxt/runtime/cultivation/TechniqueProgress.java`
- 列表界面：`src/main/java/com/iafenvoy/mxt/screen/technique/TechniquePanelScreen.java`、`src/main/resources/assets/mxt/textures/gui/classic/technique_panel.png`
- 图标模型（§14）：`src/main/java/com/iafenvoy/mxt/data/IconReference.java`、`src/main/java/com/iafenvoy/mxt/render/IconRenderer.java`
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
- 测试夹具：`src/test-mod/resources/data/mxt_test/mxt/cultivation_technique/{qingxiao_breathing_manual,sword_manual,body_manual}.json`、`.../mxt/technique_binding/qingxiao_breathing_jade_slip.json`、`.../mxt/skill_stage/{sword_art_1,sword_art_2}.json`、`.../mxt/resource/sword_mastery.json`、`.../mxt/trigger/sword_mastery_from_break.json`
- 相关文档：`docs/数据包格式.md`（`skill_stage` 段 + `cultivation_technique` 字段）、`docs/guide/datapack/overview.md:56`、`docs/模块实现审计.md:74-75`、`docs/item-bindings.md:10-16, 65-78`、`docs/guide/datapack/cultivation.md:5`、`docs/curios槽位.md`、`E:\Website\docs\docs\mod\mxt\datapack\json\{cultivation_technique,skill_stage,index}.md`、`...\datapack\overview.md`、`...\player-guide\curios-slots.md`

## 11. 拒绝路径与反馈（2026-09-13 落地，T2）

玩家右键功法物品到"学会/被拒绝"之间有两道判定，两道现在都会说明原因。

### 11.1 判定顺序

| 顺序 | 判定 | 位置 | 具名值 |
| --- | --- | --- | --- |
| 0 | 未匹配 `technique_binding` | `TechniqueItemService.use` | —— 不接管右键，返回 `false`，物品走原版行为 |
| 1 | `bindings.conditionsMet`（四类绑定的 `conditions`） | `ItemQualityService.check` | `BINDING_CONDITIONS` |
| 2 | 解析出的 `item_quality` 自身 `condition` | `ItemQualityService.check` | `QUALITY_CONDITIONS` |
| 3 | `quality_group` 成员资格（品质缺失或不属于该组） | `ItemQualityService.check` | `QUALITY_GROUP` |
| 4 | `cultivation_technique.learn_condition` | `TechniqueService.learn(entity,…)` | `CONDITIONS` |
| 5 | `#mxt:disabled` 标签 | `TechniqueService.learn(spirit,…)` | `DISABLED` |
| 6 | 已学会 | 同上 | `ALREADY_LEARNED` |
| 7 | 互斥标签与已持有集合求交 | 同上 | `CONFLICT` |
| 8 | `TechniqueLearnEvent.Pre` 取消 | 同上 | `CANCELLED` |

顺序是先门槛后事务，所以"物品根本不能用"会盖过"功法能不能学"。`CONDITIONS` 排在自己的 `DISABLED`/`ALREADY_LEARNED`/`CONFLICT` 之前（实体版重载先判 `learn_condition` 再委托），一个既被禁用又已学会、条件还不满足的功法报的是 `CONDITIONS`。

### 11.2 事件优先级带来的一个既有事实

`ItemQualityService` 用 `EventPriority.HIGHEST` 监听 `RightClickItem`/`RightClickBlock`/`AttackEntityEvent`/`UseItemStart`/`UseItemTick`，失败时 `setCanceled(true)`；`TechniqueItemService` 是 `EventPriority.HIGH`，而 `@SubscribeEvent` 默认 `receiveCanceled = false`。**所以门槛失败时 `TechniqueItemService.use` 根本不会被调用**，它自己的门槛分支只服务于直接调用 `use(...)` 的代码（命令、脚本）。改造后这一点没变，只是门槛的消息从笼统变成具名。

### 11.3 消息

| 键 | 形状 |
| --- | --- |
| `actionbar.mxt.item.cannot_use` | `"This item cannot be used right now: %s"`，参数是下一条 |
| `actionbar.mxt.item.cannot_use.binding_conditions` / `.quality_conditions` / `.quality_group` | 三条门槛原因 |
| `actionbar.mxt.technique.failed` | `"Cannot learn %s: %s"`，参数为功法名（`DefinitionText.name(holder, "cultivation_technique")`）与下一条 |
| `actionbar.mxt.technique.failure.disabled` / `.already_learned` / `.conflict` / `.conditions` / `.cancelled` | 五条事务原因 |

键名由枚举常量小写拼接，en/zh 各 5 个新键 + 门槛 4 个（含改写后的基键），两边键集合完全一致（各 324 个）。校验脚本：`C:\Users\tanks\AppData\Local\Temp\mxt-lang-check.mjs`（比对 en/zh 键集合、扫描所有字面 `translatable("…")`、按枚举常量推导拼接键）。

### 11.4 API 变化

- 新增 `ItemQualityService.check(LivingEntity, ItemStack)` → `Optional<Failure>`；`canUse` 各重载变成 `check(...).isEmpty()`，签名不变，`ItemBindingService`/`AbilityEventBridge` 等既有调用点零改动。
- `ItemQualityService.notifyCannotUse(LivingEntity, Failure)` 改为 public，供低优先级调用方复用同一条消息。
- `TechniqueService` 未改签名；`TechniqueItemService.use` 读一次 `Result` 并回报。

### 11.5 测试

`MxtTestMod.verifyTechniqueRefusals`（在 `verifyItemBindings` 里调用）：
- `mxt_test:locked_carrot`（`mxt:constant false` 条件）→ `check` 返回 `BINDING_CONDITIONS` 且 `canUse == false`；
- 功法玉简 → `check` 为空且 `canUse == true`（保证不是把所有物品都判失败）；
- 学 `sword_manual` 成功 → 再学一次 `ALREADY_LEARNED` → 学共享互斥标签 `mxt_test:technique/sword` 的 `body_manual` 得 `CONFLICT`。

## 12. 功法列表界面（2026-09-13）

用户给的线框：窄高面板，单列可滚动，每条 = 左侧图标槽 + 上方"等级/经验值"一行 + 下方进度条 + 一条分隔线。落点如下。

### 12.1 分层

- `runtime/cultivation/TechniqueProgress.java`（**公共侧，无客户端引用**）：行模型与进度口径。
  - `rows(SpiritIdentityAttachment, ResourceHolderAttachment, FormulaContext)` → `List<Entry>`，每条已学功法一行。
  - `Entry(technique, stage, rank, total, currentRequirement, hasMastery, mastery, required)`；`stage == null` 表示这条功法没有水平链，`required == NaN` 表示已经在链顶。
  - `progress(Entry, Mode)` → `Progress(done, span, fraction)`；`Mode.ABSOLUTE`（总熟练度对下一级要求）/ `Mode.WITHIN_LEVEL`（本级内已积累）。
  - 链序**不查 `ServerCache`**（它绑在服务端实例上）：直接沿 `next_stage` 走，带 512 级防呆上限。存了一个别条链的水平时 `rankOf` 返回 `-1`，行按"无水平"呈现——这同时兜住了 T11 在展示层的表现。
- `screen/technique/TechniquePanelScreen.java`（客户端）：只负责画。
- `config/MxtClientConfig.Techniques.progress_mode`：`EnumEntry<TechniqueProgress.Mode>`，默认 `ABSOLUTE`。

### 12.2 入口

- 按键 `key.mxt.technique_panel`，`InputConstants.UNKNOWN`（**默认不绑定**，和 `key.mxt.swap_back` 一致），只在 `Minecraft.screen == null` 时打开。
- `InformationPanelScreen` 头部右上角新增 `Button`（文案 = `screen.mxt.technique_panel`），`layoutButton()` 在 `init()` 与 `layoutWidgets()` 末尾各调一次，随窗口缩放重新定位。

### 12.3 面板几何（逻辑像素）

| 常量 | 值 |
| --- | --- |
| 面板 | 240 × 300（`technique_panel.png` 的原始尺寸，超出窗口时按比例压缩 blit） |
| 内边距 / 滚动条预留 | 12 / 6 |
| 行高 | 34 |
| 图标 | 24 × 24，用 `gui/classic/slot_24.png` 原始尺寸，`rowTop + (34-24)/2` 居中 |
| 文本块 | `rowX + 24 + 9` 起，到行右边界 |
| 第一行文字 | 行顶 + 4（左：等级，右：经验值，右对齐优先占位） |
| 进度条 | 行顶 + 19，高 7，宽 = 文本块宽 |
| 分隔线 | 行顶 + 31，两像素：`0xFF555555` + `0xFFFFFFFF`（与 `HotbarConfigurationScreen` 的分隔线同款） |

进度条按用户要求**全部代码画**：1px 黑边框 + `0xFF373737` 底槽 + 填充段。填充色取该功法 `mastery_resource` 的 `Resource.particleColor()`（与 `SpiritCraftingScreen` 的进度条取色方式一致），没有时退回 `0xFF8B8B8B`。分隔线同理。

### 12.4 背景素材

`assets/mxt/textures/gui/classic/technique_panel.png`，**240 × 300**，用户手画。当前提交的是一张占位图（经典配色：`B8B8B8` 底 + 1px 黑边框 + 左上 `FFFFFF` / 右下 `555555` 高光）。`blit(..., panelWidth, panelHeight, 240, 300)`，所以换图时保持这个尺寸即可，代码不用动。

### 12.5 显示细节

- 等级：数据包若定义了 `skill_stage.<ns>.<path>` 这个名（`Language.getInstance().has(...)` 判定）就显示名字，否则显示序号；两者都带 `(rank+1/total)`。整串按可用宽度省略号截断（等级列让位给右对齐的经验值）。
- 经验值：`ABSOLUTE` 显示 `done/span` = `熟练度/下一级要求`；链顶显示 `Mastered`；无 `mastery_resource` 显示 `No mastery`。`WITHIN_LEVEL` 显示本级内的 `done/span`。
- 悬浮：整行高亮（`0x503F6A91`，同信息面板），tooltip 显示功法全名 + 水平 id（行内只放得下短名/序号）。
- 空列表：画 `screen.mxt.technique_panel.empty`。
- 滚动：`ObjectSelectionList`（与信息面板同一套），并照抄信息面板显式覆写 `mouseScrolled`（滚轮一格 = 一行）。
- 关掉 `ObjectSelectionList` 自带的界面装饰（2026-09-13 按用户反馈补）：`extractListBackground` 置空——原版会在列表区域铺一层半透明黑贴图（`inworld_menu_list_background.png`，u/v 取 `getRight()`/`getBottom()+scroll` 平铺），会把用户手画的背景压成一块平灰；`extractListSeparators` 置空——原版在首行上方 2px、末行下方各画一条 `inworld_header/footer_separator.png`，而每行本来就有自己的分隔线，首行那条还会紧贴第一行文字（用户说的"顶格"就是这个）。
- 滚动条位置：原版 `scrollBarX() = getRowRight() + 6 + 2` 会落到面板边框上，覆写成 `getRowRight()`，正好落进预留的 `SCROLLBAR_ROOM`（直接引用 `AbstractScrollArea.SCROLLBAR_WIDTH`，不再写死 6）。
- 空状态文案用 `TEXT_COLOR`（`0xFF404040`，和面板其余文字一致，原 `0xFF7A7A7A` 在 `B8B8B8` 上对比度不足）并再内缩 `EMPTY_INSET`；"无水平"行用的 `UNKNOWN_COLOR` 也从 `0xFF7A7A7A` 调到 `0xFF5A5A5A`。
- 未同步改动：人物信息面板的两个列表仍保留原版装饰（列表底色 + 首末分隔线）——那是它长期以来的观感，且是文字列表，底色有分隔作用；要不要一并去掉等用户决定。
- 刷新：沿用 `MxtClientConfig.informationRefreshInterval()`，刷新时保留滚动位置。
- 预留：图标目前只画空槽框，位置在 `extractContent` 里留了 `//TODO::`，等 `cultivation_technique` 有 icon 字段后直接叠一层 blit。

### 12.6 没有新增网络包

`SpiritIdentityAttachment`（`learned_techniques` + `technique_stages`）与 `ResourceHolderAttachment` 都是 `ShouldSyncAttachment`，`mxt:cultivation_technique` / `mxt:skill_stage` 也是客户端同步注册表，所以等级与进度在客户端直接算。

### 12.7 测试

- `MxtTestMod.verifyTechniqueProgress`（服务端可跑，挂在熟练度端到端测试之后）：断言真实行（1 条、`sword_art_2`、rank 1/2、熟练度 10、本级要求 10、`hasNextLevel()` 为假、`ABSOLUTE` 分数 1.0）；用构造的 `Entry` 断言入口级两种口径一致（都是 0.4）与二级以上两种口径不同（`ABSOLUTE` 0.72 / `WITHIN_LEVEL` 8/15，且 `done=8`、`span=15`）。
- `MxtTestMod.verifyTechniquePanelAssets`：用 classloader 断言 `technique_panel.png`、`slot_24.png` 在 jar 内，并解析 `assets/mxt/lang/{en_us,zh_cn}.json` 断言面板/按键/配置键全部存在——否则贴图路径写错或翻译键拼错在客户端是静默缺陷（画成缺失贴图占位符或直接打印原始键）。
- **未验证**：界面实际渲染效果（客户端专属，服务端审计跑不到）。贴图/键/数据都验过，视觉效果需要人眼看一次。

## 13. 信息面板的换行诊断与列宽修复（2026-09-13，调试功法面板时发现）

**现象**（用户日志，Render thread，每秒重复）：

```
Information entry contains more than one line (width=225):  = 灵力: realm_stage.mxt_test.spirit_power_refining
Information entry contains more than one line (width=90): 生命值 = 20.0 / 20.0
Information entry contains more than one line (width=90): 维度 = overworld
```

**与本次新增界面无关**：报错点在 `InformationPanelScreen.LineEntry.extractContent`（人物信息面板），早于功法面板就存在。

**根因（三件事叠在一起）**

1. **列宽按"全局最长标题"固定**：`buildEntries` 取所有条目里最宽的标题（这里是"经验等级"= 36px）作为 `nameWidth`，`extractContent` 再用它算数值列起点 `valueX = x + 8 + nameWidth + 8`。于是标题只有 18px 的"维度"也要让出 36px，数值列只剩 `availableWidth - nameWidth - 8`。
2. **窗口偏窄**：`panelWidth = min(510, width - 12)`。用户可以复现的窗口下 `panelWidth ≈ 398`，反推基础列表 `availableWidth = 90`、修炼列表 `availableWidth = 225`。90 - 36 - 8 = 46px，装不下 `overworld`(≈54) 或 `20.0 / 20.0`(≈55) → `font.split` 出两行；修炼列表里那条值含**未翻译的原始键**（`realm_stage.mxt_test.spirit_power_refining`，见下）长约 244px > 181 → 同样两行。
3. **诊断每次刷新都重报**：`overflowReported` 是 `LineEntry` 的字段，而 `refreshInformation()` 每个刷新间隔（默认 20 tick）都 `replaceEntries` 生成新对象，标志复位 → 每秒一次 ERROR。

顺带发现：那条值之所以是原始键，是**测试包漏了一个显示名**——10 个 `realm_stage` 里 9 个有 `realm_stage.mxt_test.*` 键，`spirit_power_refining` 没有。

**修复**

| 改动 | 位置 |
| --- | --- |
| 列宽改为**按需分配**：数值先拿它需要的宽度，标题列让位；标题保底占 1/4 行宽（保证还能认出是哪一行），只有在谁都装不下时才截断数值 | 新增 `InformationHelper.columns(availableWidth, preferredNameWidth, valueWidth)` + `InformationHelper.Columns`，`LineEntry` 调用它 |
| 诊断按"标题=值"内容去重，集合挂在 screen 上而不是条目上 | `InformationPanelScreen.overflowReports`，由 `InformationList`/`LineEntry` 构造器传入 |
| 测试包补 5 个显示名：`realm_stage.mxt_test.spirit_power_refining`、`resource.mxt_test.{channel_probe,sword_mastery}`、`physique.mxt_test.{blazing_body,sword_master_body}`（中英各一份） | `src/test-mod/resources/assets/mxt_test/lang/*.json` |
| 新增"面板会打印名字的定义必须有翻译键"的审计（`resource`/`realm_stage`/`spirit_root`/`physique`/`cultivation_technique`；**故意不含 `skill_stage`**，因为无名水平是有设计支持的，功法面板会退回序号） | `MxtTestMod.verifyDisplayNames` |

**为什么抽成纯函数**：列宽分配原本埋在客户端渲染里，服务端审计够不到。抽到 `InformationHelper`（无客户端引用）后可以用真实数值断言，`MxtTestMod.verifyInformationColumns` 覆盖：
- `columns(90, 36, 54)` → 标题 28 / 数值 **54**（日志里那条 `overworld` 的宽度，正好装下，不再换行）
- `columns(90, 36, 6)` → 标题 **36** / 数值 46（数值短时保持标题对齐）
- `columns(90, 36, 200)` → 标题 **22**（= 90/4 保底）/ 数值 60
- `columns(1, 36, 200)` → 非负、不抛

**顺带修掉的文档漂移**：`keys-and-hud.md` 把 Position 列为人物信息面板的基本信息条目，但 `InformationManager` 只注册了 health/food/experience/dimension（`info.mxt.position` 是**无人引用的死键**），已从文档移除；死键本身留着没删。

**仍未验证**：实际渲染效果（同 §12）。




## 14. 图标统一为 IconReference（2026-09-13）

**起因**：`HotbarIcon` 只是热键栏的图标，但仓里还有三种各不相同的"图标"写法；给功法加图标会变成第四种。

**收敛前**（见 §14.1 的原地清单）：
- `HotbarIcon`：`{texture|item}` 二选一，只有 `Ability.icon` 与 `Resource.icon` 用；
- 裸 `Identifier` 当 GUI 贴图：`Badge.sprite`（四个 badge 实现都有，且**全仓零渲染器**）、`ResourceBar` 的 `sprite_location` / `background_sprite` / `fill_sprite`；
- 名字叫 icon、实际是**物品 ID**：`ForgingMethod.display_icon`（`BuiltInRegistries.ITEM` 惰性解析），写贴图路径只会得到空物品；
- 干脆没字段、从已有数据推：`ForgingBlueprint` 用 `result()`、`ForgingScreen.Entry` 直接抱 `ItemStack`。

**收敛后**

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 数据（双端） | `data/IconReference.java` | `record IconReference(Either<Identifier, ItemStackTemplate> value)`；`CODEC` 用 `Codec.either(Identifier.CODEC, ItemStackTemplate.CODEC)`，**内联**——裸字符串是贴图，对象是物品。辅助：`texture()`/`item()`/`stack()`/`of(ItemStack)`/`texture(id)`/`item(template)`；`toString()` 保持浅层（含 holder） |
| 渲染（客户端） | `render/IconRenderer.java` | `render(graphics, icon, x, y, boxSize)` 把 16x16 的图标居中画进 box；`renderOrName(graphics, font, icon, name, x, y, boxSize)` 在没有图标时用名字前 3 个字顶上（沿用热键栏原偏移，观感不变） |

**改了哪些地方**

| 定义 / 界面 | 改动 |
| --- | --- |
| `Ability.icon`、`Resource.icon` | `Optional<HotbarIcon>` → `Optional<IconReference>`，JSON 形状**内联**（原本是 `{texture\|item}`，现改成裸字符串 / 对象） |
| `HotbarEntry.icon()`、`AbilityHotbarEntry`、`SpiritHotbarEntry` | 返回类型跟着换 |
| `HotbarEntry.render` | 内联的 item/texture 分支 + 名字回退 → 一行 `IconRenderer.renderOrName(...)` |
| `HotbarConfigurationScreen.renderIcon` | 同上（这段原本是 `HotbarEntry.render` 的拷贝）；调用点从 `(x+3, y+3)` 改成传槽位框 `(x, y)`，由 Helper 居中 |
| `Badge.sprite()` + `SpriteBadge`/`TooltipBadge`/`KeybindBadge`/`CraftingRecipeBadge`/`EmptyBadge` | `Identifier sprite` → `IconReference icon`，JSON `"sprite": "..."` → `"icon": "..."`（贴图）或 `"icon": {"id": ...}`（物品）。**破坏性改名**：`sprite` 这个名字只描述贴图，而这套现在两种都能装 |
| `ForgingMethod` | `Optional<Identifier> displayIcon` → `Optional<IconReference> icon`，JSON `"display_icon": "minecraft:x"` → `"icon": {"id": "minecraft:x"}`。`iconStack()` 改为 `icon.flatMap(IconReference::stack)`；`displayName` 注释补上"贴图图标没有物品名可借，退回 id" |
| `ForgingScreen` | `Entry(Identifier, ItemStack)` → `Entry(Identifier, @Nullable IconReference)`；`blueprintEntries` 用 `IconReference::of` 包 `result()` 物品；`stepIcons` 返回 `List<IconReference>`（缺省用 `null`）；`EMPTY_STEP` 变成 `IconReference`（barrier 物品） |
| `CultivationTechnique` | **新增** `Optional<IconReference> icon`（JSON `icon`） |
| `TechniquePanelScreen` | 图标槽框里按 `technique.icon()` 叠一层 `IconRenderer.render(...)`，替掉原来的 `//TODO::` |
| `data/HotbarIcon.java` | **已删除** |

**刻意没改的**（这不是漏掉，是它们不是"图标"）
- `OriginsRenderData.sprite_location` + `bar_index`/`icon_index`、`TexturedRenderData.background_sprite`/`fill_sprite`：这些是**资源条美术**——一对"底图+填充"、任意宽高、还有 25 格贴图集的索引，`IconReference` 是单个 16x16 图标，塞进去会丢掉索引与尺寸语义。是否接入**留了 TODO**（两处 `RenderData` 源文件 + 数据包文档各一条），后续再定。
- 槽位里的**真实物品**：`InformationPanelScreen` 的装备槽、`ExchangeStationScreen` 的产物、`DisplayStandComponentProvider` 展示的物品——它们展示的就是那件物品本身，不是某个定义的图标。

**测试**（`MxtTestMod.verifyIconReferences`）
- 裸字符串 `"mxt:...png"` 解出贴图分支；对象 `{"id": "minecraft:diamond"}` 解出物品分支并实体化成钻石堆；
- 裸字符串 `"minecraft:diamond"` **被贴图分支拿走**（钉死解析顺序，而不是"看哪支碰巧先成功"）；
- `{}`、`[]`、`7` **都被拒绝**；
- 五个定义族都保住了自己的图标：`firebolt`（物品 `minecraft:fire_charge`）、`spirit_power`（贴图）、`badge/sprite`（贴图）、`polish`（物品 `minecraft:diamond`，且 `displayName` 借到钻石的物品名）、`sword_manual`（贴图）/`body_manual`（物品 `minecraft:book`）。

**顺带修的踩坑点**：`ForgingMethod` 的 `display_icon` 以前写贴图路径会静默变成空图标（因为用 `BuiltInRegistries.ITEM` 解析），现在贴图走字符串分支就真的是贴图。

**一次返工（同日）**：初始实现把两支写成 `{"texture": ...}` / `{"item": ...}` 两个键，用 `Codec.xor` 保证"恰好二选一"。按用户要求改成**内联**：去掉两个键，直接用 `Codec.either(Identifier.CODEC, ItemStackTemplate.CODEC)`。代价是 `either` 的语义是"先成功者胜"而不是"互斥"，而 `Identifier.CODEC` 与 `ItemStackTemplate.CODEC` **都接受裸字符串**（后者是 `Codec.withAlternative(MAP_CODEC.codec(), Item.CODEC, ...)`，见 `ItemStackTemplate.java:29`），所以：(1) 物品必须写成对象 `{"id": ...}`，裸字符串永远归贴图；(2) "两个都写"不再报错（该形状本身已不存在）。相应地删掉了测试里"两个都写要被拒绝"的断言，换成钉死"裸字符串归贴图"的断言。