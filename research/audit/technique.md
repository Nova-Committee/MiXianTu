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
> - 2026-09-13：`IconReference.CODEC` 改为**内联** `Codec.either(Identifier.CODEC, ItemStackTemplate.CODEC)`（去掉 `texture`/`item` 两个键），并给 `OriginsRenderData`/`TexturedRenderData` 的贴图字段打上"是否接入 `IconReference`"的 TODO（见 §14 的返工段）。
> - 2026-09-13：`technique_binding` 新增 `learn_time`（长按 tick 数）与 `hold_animation`（长按播放的动作，白名单制），并补两本可直接测试的功法（见 §15）。
> - 2026-09-13：**修掉"学会成功没有任何提示"**（只写了失败分支），并新增学会后的物品冷却（原版 `CooldownTracker`，长度走服务端配置；只有成功才计）（见 §16）。
> - 2026-09-13：**修掉"长按学不会"**：学会原本挂在 `Stop`，而 `Stop` 在 `Consumable.onConsume` 吃掉物品之后才到，绑定已解析不到 → 书少一本、功法没学到。改挂 `Finish`（早于消耗）并先摘掉 `CONSUMABLE`（见 §17，同时更正 §15 的错误结论）。
> - 2026-09-13：**修掉"用一次就没了"**：`Finish` 事件携带的 `ItemStack` 是 `useItem.copy()` 的**副本**，清它的组件等于没清，真身仍被吃掉。改为同时清理手持 stack（见 §18）。
> - 2026-09-13：**新增 `/mxt technique repair`**：引用已删除定义的功法会让整个 `SpiritIdentityAttachment` 解码失败并静默回退到空（面板空白、书没反应、无报错）。命令清理失效引用与重复项并重建派生状态（见 §19）。
> - 2026-09-13：**修掉"长按拿手上没反应/刚 Start 就没了"的真正原因**：hold 的时长与动作原本靠"点击事件里往 stack 写 `CONSUMABLE`"，而那个写入**只在服务端**执行——客户端因此算出 duration=0、animation=NONE，玩家从头到尾没有进入过真正的 hold。改为 `TechniqueManualItem` 重写 `use`/`getUseDuration`/`getUseAnimation`，两边跑同一段代码（见 §20）。**约束：声明 `learn_time` 的物品必须是该类**，否则记录 ERROR 点名绑定。
> - 2026-09-13：**定案为 mixin**（见 §21）。§20 的物品类方案**取消**——它破坏了"绑定作用于任何已注册物品（含 KubeJS）"这一承诺。改为 `ItemMixin` 注入 `Item.use`/`getUseDuration`/`getUseAnimation` 三处，取值来自两端各自捕获的数据包绑定；不再写任何组件，也就不再有"抢在游戏吃掉它之前摘组件"的竞态。`TechniqueManualItem`、`TechniqueHoldComponent`、`TECHNIQUE_HOLD` 全部删除。
> - 2026-09-16：**称号与徽章预留彻底删除**。本文 §8.2 记录过的 `Title.maximum_level`（"只有上限、没有等级系统"）与 §14 记录过的 `Badge.sprite`（"全仓零渲染器"）都不再需要跟踪——`title`、`badge` 两个数据包注册表、`badge_type` 固有注册表、`Title`/`TitleService`/`Badge` 及其五个实现、附件里的 `titles` 字段、测试夹具与相关纹理全部移除，`BadgeCodecs` 里仍在使用的 `TRANSLATABLE_COMPONENT` 迁到 `util/codec/MiscCodecs.java`（杂项 Codec 的归处）。原条目保留在下方作为留档，见 `research/02_动态注册表清单.md` 的追加记录。

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
| `exclusive_tags` | `Identifier[]` `[]` | 互斥：汇总已学功法的标签集合，与新功法求交集，命中 → `CONFLICT` | `TechniqueService.java:33-36`；另见 `runtime/cultivation/CultivationIdentityService.java:46-48`（同型机制） |
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
- 刷新：沿用 `MxtClientConfig.INSTANCE.information.refreshInterval.getValue()`，刷新时保留滚动位置。
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

## 15. 长按学习与长按动作（2026-09-13）

**需求**：功法拿在手上，满足条件后**长按**一段时间即可学会（类似吃东西），时长写在绑定 JSON 里；随后用户要求默认动作从"吃东西"换掉，并且**数据包可以自己选动作**。

**`technique_binding` 新增两个字段**

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `learn_time` | Integer | `0` | 长按 tick 数，范围 `0..72000`。`0` = 右键即学（原行为）。用 tick 而不是秒，因为 use 周期本来就按 tick 计数 |
| `hold_animation` | String | `block` | 长按播放的动作。仅在 `learn_time > 0` 时有意义 |

**为什么必须挂在 `RightClickItem` 上而不是 `LivingEntityUseItemEvent.Start`**

这是本次最容易踩的坑。26.1.2 的 `Item.use`（`Item.java:207-230`）**先读 `DataComponents.CONSUMABLE`**，没有这个组件就落到 `EQUIPPABLE` / `BLOCKS_ATTACKS` / `KINETIC_WEAPON` 分支，最后返回 `InteractionResult.PASS`。也就是说普通物品**根本不会开始 use 周期**，`Start` 永远不会触发，在那里写组件是死代码。所以时长必须在 `RightClickItem` 阶段就写进手持 stack 的组件里（此时 `Item.use` 还没执行）。

配套地，`use()` 对长按绑定必须返回 `false`。返回值的语义是"是否认领/取消这次点击"，返回 `true` 会让调用方取消交互，而这次点击正是 use 周期的起点——取消掉长按就永远不会开始。瞬发绑定仍然返回 `true` 以认领点击。

**在哪学会**：`Finish`。~~`Stop`，不是 `Finish`。`Finish` 只在物品**真的被消耗**时触发，书是不会被吃掉的。~~ **这段判断是错的，见 §17**：`Finish` 并不是"只在被消耗时触发"，它是 `completeUsingItem` 在时长走完那一刻发的，**早于**消耗；而 `Stop` 是取消路径，且在它触发前 `Consumable.onConsume` 已经把书吃掉了一本。按这里写的原方案实现后，长按完全学不会。

**动作白名单**（`TechniqueBinding.ALLOWED_ANIMATIONS`）

用户先要求默认 `SPYGLASS`，在得知下面这段源码事实后改选"白名单排除 spyglass"：

`SPYGLASS` 无法做成"只要姿势、屏蔽副作用"，因为**姿势本身就是副作用**：
- `ItemInHandRenderer:431` 是 `if (!player.isScoping())` **包住整个手部渲染**——`isScoping()` 为真时手臂**连同物品**都不画。也就是说这个姿势的"视觉"其实是"没有视觉"，画面来自遮罩；屏蔽 scoping 之后它和 `NONE` 完全等价。
- `Player.isScoping()`（`Player.java:1991-1993`）读的是 `getUseItem().canPerformAction(ItemAbilities.SPYGLASS_SCOPE)`；而 FOV 锁 0.1（`AbstractClientPlayer:111-112`）与低鼠标灵敏度（`MouseHandler:394`）都是**无条件**跟随 `isScoping()` 的，且在 mod 够不到的类里。
- `AvatarRenderer:125-127` 的 `ArmPose.SPYGLASS` 只作用于第三人称；`ItemInHandRenderer` 的 switch 里**根本没有 SPYGLASS 分支**。

最终白名单 `block` / `brush` / `bundle` / `none` / `toot_horn`，其余全部在加载期报错。排除理由：
- `spyglass`：见上。
- `eat` / `drink` / `spear`：声明了 `hasCustomArmTransform`，`ItemInHandRenderer:494` 会因此**跳过**本该把物品举起来的手臂变换，而这些物品没有配套手臂模型；`spear` 还会读 kinetic-hit 计时器。
- `bow` / `trident` / `crossbow`：按"已蓄力多少"缩放姿势，用它们会让阅读看起来像在拉一张不存在的弓。

默认 `block` 的理由：它在客户端只有一段纯姿势变换，且被 `if (!(itemStack.getItem() instanceof ShieldItem))` 包着——我们的是普通 `Item`，所以这段分支**正好会执行**（`ItemInHandRenderer:507-514`），且不带任何全局副作用。`hasCustomArmTransform` 为 false，也不会触发 `isScoping()`。

**实现位置**

| 文件 | 改动 |
| --- | --- |
| `data/item/TechniqueBinding.java` | 加 `learnTime` / `holdAnimation` 两个组件；`NO_HOLD`、`DEFAULT_HOLD_ANIMATION`、`ALLOWED_ANIMATIONS`；`validate` 拒绝"无 `learn_time` 却写了非默认动作"和"动作不在白名单" |
| `runtime/cultivation/TechniqueItemService.java` | 删掉硬编码的 `HOLD_ANIMATION = EAT`；`onItemClick` 在点击时把 `Consumable` 写进手持 stack；`onUseStop` 在按满时学会；`use()` 对长按绑定返回 `false` |

**测试**（`MxtTestMod.verifySampleTechniques` + `verifyHoldAnimations`）
- 两本功法：`azure_water_manual`（`learn_time: 60` + `hold_animation: brush`）、`iron_body_manual`（无 `learn_time`，右键即学）；
- 玉简 `qingxiao_breathing_jade_slip` 补了 `learn_time: 40` 但不写 `hold_animation`，覆盖**默认值**路径；
- 白名单五个值逐个 round-trip 解码，`spyglass`/`eat`/`drink`/`bow`/`trident`/`crossbow`/`spear` 逐个断言**被拒绝**；
- 无 `learn_time` 却写非默认动作 → 拒绝；同样的动作写在有 `learn_time` 的绑定上 → 接受。

**归属修正（同日）**：两本手册最初注册在主 mod 的 `MxtItems`，用户指出**测试内容不该进主 mod**。已移到 `MxtTestTechniqueItems`（`mxt_test` 命名空间，纯 `Item` + 数据包绑定，无自带行为），主 mod 侧同步删除：`MxtItems` 的两项、`assets/mxt/items` 与 `assets/mxt/models/item` 各两个文件、`assets/mxt/lang` 里两条 `item.mxt.*`。物品 id 从 `mxt:azure_water_manual` 改成 `mxt_test:azure_water_manual`，`technique_binding` 的 `items` 字段与文档示例一并跟着改。放在主 mod 的代价是把两个半成品道具塞进每个玩家的创造模式物品栏，并把它们的 id 冻进存档。

**两次被自己的审计抓到的错**
1. 用裸 `JsonOps` 解 `technique_binding`，但 `technique` 是 `RegistryFixedCodec`，必须配 `RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())`——参考同文件里 `ItemMatcher.ENTRIES_CODEC` 的既有写法。
2. 断言"无 `learn_time` + `hold_animation` 要被拒绝"时写的是 `"block"`，而 `block` **就是默认值**，所以 `validate` 的第一条（`learnTime <= NO_HOLD && holdAnimation != DEFAULT`）**本就该放行**。是断言写错了，不是编解码器。改成用非默认的 `"brush"` 后通过。

## 16. 学会反馈与物品冷却（2026-09-13）

**起因**：用户发现"学习成功没有提示"，并要求加物品冷却。

**成功提示缺失**（真 bug）。`TechniqueItemService.learn` 原来只写了失败分支：

```java
if (!result.learned()) notifyLearnFailure(entity, binding.technique(), result);
```

`learned == true` 时**一行都不发**。而失败有完整的一族消息（`actionbar.mxt.technique.failed` + 五个 `failure.*`），成功什么都没有——玩家看到的现象是"右键没反应"，与"没匹配到绑定"无法区分。这类"没有崩溃、没有日志、只是少了一行"的缺陷正是审计该守住的，所以修复后把消息键的存在性也写成了断言。

补法：`learn` 拆成两条路径，成功走新增的 `notifyLearned`（`actionbar.mxt.technique.learned`，绿色，动作栏，与失败同一条通道——两者都是瞬时状态而不是记录）。

**物品冷却**。按用户要求：**用原版实现**、**长度在 config（tick）**。初期为"只在成功时计"，**后按用户要求改为无论成功失败都计**（见 §23）。

| 位置 | 改动 |
| --- | --- |
| `config/MxtServerConfig.java` | `cultivation` 页新增 `techniqueLearnCooldown`（`config.mxt.server.cultivation.technique_learn_cooldown`，默认 `60`，范围 `0..72000`，`0` 关闭） |
| `runtime/cultivation/TechniqueItemService.java` | `learn` 在判定结果**之前**调 `applyCooldown(entity, stack)` → `player.getCooldowns().addCooldown(stack, ticks)` |

走原版 `Player#getCooldowns` 而不是自建计时器的好处：热键栏的灰色扫描与 `mxt:on_cooldown` 物品条件**零成本**就能读到，也不需要新增数据组件。

**测试**（`MxtTestMod.verifyLearnFeedback`）
- 两条消息键都能解析出翻译（不返回 key 本身），且成功消息含 `%s`（否则"学会了"却不说学会了什么）；
- 配置项非负；
- 冷却**无法**在此审计里真正触发：它只对 `Player` 生效，而审计唯一能造的实体是 `Pig`。改为断言"非玩家学会时不会因为冷却而抛异常或漏学"——即 `use` 仍返回 `true` 且功法真的进了 `learnedTechniques`。这是能覆盖的部分，剩下的是客户端手感。

## 17. 长按学不会 —— 学错事件了（2026-09-13）

**起因**：用户实测"长按的碧水诀好像学不会"。

**这是个真 bug，而且 §15 的审计完全没抓到**。之前所有断言都直接调 `TechniqueItemService.use(...)`，而 `.use()` 对长按绑定**永远返回 `false` 并且什么都不做**（§15 的设计如此）——等于整套长按路径一行都没被验过。审计全绿，游戏里全坏。

**根因：学会挂在了 `Stop` 上。** 追 MC 26.1.2 的 use 周期：

```
LivingEntity.tick → updatingUsingItem (3455)
  → updateUsingItem (3503):
      --useItemRemaining <= 0  &&  !useOnRelease()  →  completeUsingItem() (3602)
          → EventHooks.onItemUseFinish(...)   ← Finish 在这里
          → useItem.finishUsingItem(...)      → Consumable.onConsume → stack.consume(1, user)  ← 物品在这里被吃掉
          → stopUsingItem() → ... onStopUsing ...
      （松手 / 中途取消）                       ← Stop 在这里
```

`Stop` 是**取消**路径（`releaseUsingItem()`），而按满走的是 `completeUsingItem()`。两者都会走到 `stopUsingItem()`，但顺序上 `Consumable.onConsume` 已经在 `Finish` 与 `stopUsingItem` 之间把 `stack.consume(1, user)` 执行完了。

于是 `Stop` 里的判定同时错两处：

1. **物品已经被吃掉了一本**（`shrink(1)`）；
2. 更致命的是，此时手上的 stack 已经空了，`ItemBindingService.technique(stack)` 解析不到绑定，`onUseStop` 直接 `return` —— **学会逻辑根本没执行**。

玩家的观感就是"长按读完了，书少了一本，功法没学会"。

**修法**

| 改动 | 说明 |
| --- | --- |
| 学会移到 `Finish` | 这是唯一同时满足"时长走完"且"物品还在"的时刻。`Finish` 从 `completeUsingItem` 触发，早于消耗 |
| 在 `Finish` 里**先** `clearHold(stack)` 再 `learn(...)` | 把 `CONSUMABLE` 摘掉，后面的 `Consumable.onConsume` 就没东西可吃了。顺序反过来仍会被吃掉 |
| `Stop` 改为只清理组件 | 中途松手是取消路径，不该学会，但必须把组件摘掉 |
| 抽出 `arm(entity, stack)` | `RightClickItem` 构造器要求真 `Player`，审计造不出来；把"是否武装"的判定抽成可单测的方法 |

**组件必须摘掉**：`CONSUMABLE` 正是原版判断"这东西能吃"的依据。留在手上，一次普通右键就会把功法书直接吃掉且什么也学不到。

**测试**（`MxtTestMod.verifyHoldLifecycle`）按真实事件顺序回放：武装 → `Finish`（断言**物品数量不变** + 功法已学会 + 组件已摘）→ 另一只实体武装后 `Stop`（断言组件已摘 + 没学会）。

**变异测试验证**：把学会改回挂在 `Stop` 上重跑，审计**确实报错**（`A read manual kept its consumable and would be eaten next click`），改回即通过。这一步是必要的——否则无法证明新断言真的在覆盖这条路径，而不是又一次"全绿但没测到"。

**与 §15 的关系**：§15 写的"学会在 `Stop`、用 `getUseItemRemainingTicks() == 0` 判断按满"是**错的**，已按本节更正。当时只读了 `Item.use` 一侧就下结论，没有把 `completeUsingItem` → `onConsume` 这条链追到底。

## 18. 长按"用一次就没了"—— `Finish` 带的是副本（2026-09-13）

**起因**：用户实测"使用了一次后并没有学会，然后就不能再次使用了"。

这是 §17 修复**没修干净**留下的：学会确实动了，但 `clearHold` 摘错了对象，书照样被吃掉，于是"用一次就没了、也不能再用"。

**根因：`Finish` 事件里的 `ItemStack` 是副本。**

`completeUsingItem`（`LivingEntity.java:3602`）：

```java
ItemStack copy = this.useItem.copy();                                  // 3609 ← 副本
ItemStack result = EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(),
                                              this.useItem.finishUsingItem(this.level(), this));  // 3610
```

第 3610 行同时做了两件事，顺序很关键：
1. **先**把 `copy` 交给 `EventHooks.onItemUseFinish` 发事件 —— 所以 `event.getItem()` 是那份副本；
2. **再**（同一个表达式里）调 `this.useItem.finishUsingItem(...)`，而它内部（`Item.java:232-235`）读的是 **`this.useItem`** 的 `CONSUMABLE`，然后 `onConsume` → `stack.consume(1, user)`。

我在事件处理器里对 `event.getItem()` 调 `remove(CONSUMABLE)`，改的是那份**马上就要被丢掉的副本**；`this.useItem`（真正在手上的那份）组件还在，于是照吃不误。玩家看到的就是"读一次，书没了，功法没学会"。

**修法**：`clearHold(entity, stack)` 改成同时清 **事件带来的 stack** 和 **`entity.getItemInHand(entity.getUsedItemHand())`**（两者不是同一对象时才处理第二个）。`onUseFinish` 也改为优先取手上的 stack。

**审计为什么又漏了**：`verifyHoldLifecycle` 里写的是
```java
ItemStack manual = armHold(reader);
TechniqueItemService.onUseFinish(new Finish(reader, manual, 0, manual.copy()));
```
事件参数直接传了**手上那个对象本身**，而 vanilla 传的是 `copy()`。两者相等让"清副本 vs 清真身"这个区别完全消失，断言却是在 `manual` 上做的——所以**必然通过**。已改成传 `handStack.copy()`，断言改读 `reader.getItemInHand(MAIN_HAND)`。

**变异测试验证**：把 `clearHold` 改回只清事件 stack，审计报 `An abandoned hold left the manual edible`；改回即通过。

**教训**：这条 bug 和 §17 是同一类——**用"我以为是那个对象"代替了"vanilla 实际传的是哪个对象"**。修 §17 时我只验证了"`Finish` 早于消耗"这个时间顺序，没有再核对一次**参数是不是同一个引用**。

## 19. 失效功法引用与 `/mxt technique repair`（2026-09-13）

**起因**：用户报告"好像数据损坏了，目前卡在了一个中间位置，学习学不了，面板也不显示"，并要求加一个一键清理无效功法数据的修复指令。

**🚨 本节最初的根因判断是错的，已订正。** 我最初写的是"`RegistryFixedCodec` 遇到无法解析的 id 会让**整个附件解码失败**并静默回退到全空"。用户提醒 `util.codec` 里有自动忽略失败的 codec，实查后发现：

```java
// CollectionCodecs.java:25-31
public static <K, V> Codec<Map<K, V>> map(Codec<K> keyCodec, Codec<V> valueCodec) {
    return AutoIgnoreMapCodec.create(keyCodec, valueCodec);
}
public static <T> Codec<List<T>> list(Codec<T> elementCodec) {
    return AutoIgnoreListCodec.create(elementCodec);
}
```

`SpiritIdentityAttachment` **本来就在用**这两个宽松 codec。`AutoIgnoreListCodec.accept`（第 46-50 行）逐元素 decode，失败的元素**只记一条 WARN 并跳过**，其余元素正常进入列表。所以**失效引用只损失它自己**，不会拖垮附件。

**实证**（`MxtTestMod.verifyStaleReferenceDecode`）：构造含 `["mxt_test:sword_manual", "mxt_test:never_existed"]` 的附件 JSON 解码，断言 `learnedTechniques().size() == 1`（好的存活、坏的消失）且附件整体解码成功。服务端日志确认：

```
[WARN] [AutoIgnoreListCodec]: Ignoring invalid list element: Failed to get element mxt_test:never_existed
```

**同时否掉了"失效引用"这个假设本身。** 既然存在失效引用必然打 WARN，就去查了用户的客户端日志：

```
（搜索 'Ignoring invalid' / 'Failed to get element' —— 零结果）
```

**没有任何 WARN**，说明用户存档里**不存在失效引用**。面板空白与书无反应**另有原因**，与该命令无关。

**我的错误性质**：看到 `RegistryFixedCodec` 是"整体失败"语义就推出了结论，**没有实读 `CollectionCodecs` 的实现**（它就在同一个工程里）。而且我在只有推测的情况下，把结论写进了 `docs/数据包格式.md` 和 javadoc，还写了"日志零报错"这种**可以被一条 grep 证伪**的具体论断。这是本轮最严重的问题：**用推断冒充已验证的事实，并写进文档固化下来。**

**保留下来的东西**：命令本身仍有价值（清理失效引用、去重、重建派生状态），但它的定位从"修你这个 bug"降级为"一个正确但与你当前问题无关的工具"。文档已改写为：看到 `Ignoring invalid list element` 才是本节的情况，看不到就别用它。

**测试**（`MxtTestMod.verifyRepairSweep`）：`resolves` 对真实功法/未定义 id 分别返回 true/false；干净列表不被改动（保证幂等、不误删）；重复项被清除；五个消息键都能解析出翻译。

**测试中踩的坑**：本想构造"未绑定 holder"模拟失效引用，但 `Holder.Reference` 构造器是 `protected`、`Registry` 只有 `createIntrusiveHolder(T)`，外部造不出来。改为直接构造 JSON 走真实解码路径——**这反而成了发现根因判断错误的关键一步**。

## 20. 长按的真实根因：服务端/客户端各算各的（2026-09-13）

**起因**：用户报告"碧水诀拿手上无法使用，第一人称会有个向下的动作，应该是刚 Start 就被取消了，聊天栏也没报错"。

**先做的事：让证据说话，而不是继续推断。** 加 `/mxt technique diagnose`，用户在游戏内实测输出：

```
手持物品：mxt_test:azure_water_manual
绑定功法：mxt_test:azure_water_manual；长按时长：60 tick；动作：brush
物品使用门槛：OK
是否已学会：no（已会功法数 1）
学习条件：PASS
是否在冷却中：no
```

**服务端每一项都是好的。** 这一条把 §17/§18 修的全部内容、以及绑定/门槛/条件全部排除掉了——问题不在逻辑，而在**客户端和服务端对同一件物品的理解不一致**。

**根因**：hold 的实现是"在点击事件里往 stack 上写 `CONSUMABLE` 组件"，而 `arm()` 开头有

```java
if (entity.level().isClientSide()) return;   // ← 只在服务端写
```

于是同一个物品，两边的回答完全不同：

| 问题 | 服务端 | 客户端 |
| --- | --- | --- |
| `Item.use`（`Item.java:207-211`）读 `CONSUMABLE` | 有 → 启动 use 周期 | **无 → 返回 `PASS`，根本不启动** |
| `Item.getUseDuration`（`:328-335`） | `consumeTicks()` = 60 | **0** |
| `Item.getUseAnimation`（`:317-326`） | `BRUSH` | **`NONE`** |

而且客户端拿到服务端的"正在使用"标记后，`LivingEntity.onSyncedDataUpdated`（`:3558-3567`）会这样补状态：

```java
} else if (DATA_LIVING_ENTITY_FLAGS.equals(accessor) && this.level().isClientSide()) {
    if (this.isUsingItem() && this.useItem.isEmpty()) {
        this.useItem = this.getItemInHand(this.getUsedItemHand());
        if (!this.useItem.isEmpty()) {
            this.useItemRemaining = this.useItem.getUseDuration(this);   // ← 客户端算出来是 0
        }
    }
```

**所以玩家看到的就是**：客户端自己没启动 use（`use` 返回 `PASS` → 普通右键挥手，即"向下的动作"），服务端却在跑一个 60 tick 的 hold；标记同步过来后客户端用 0 当时的剩余量，进度条和姿势全是坏的。**"刚 Start 就被取消"的观感，本质是客户端从头到尾就没有进入过真正的 hold。**

**这也解释了为什么之前三轮都没修对**：§17、§18 修的是**服务端**的事件顺序和组件清理，而那些确实是 bug，但**没有任何一个能修好"客户端不知道有 hold 这回事"**。

**修法：把答案搬到物品上，而不是继续在 stack 上写组件。**

| 新增/改动 | 作用 |
| --- | --- |
| `item/TechniqueManualItem`（新） | 重写 `use`/`getUseDuration`/`getUseAnimation`。vanilla 问物品的这三个问题由物品回答，**客户端和服务端跑同一段代码、读同一份数据，因此必然一致** |
| `data/item/TechniqueHoldComponent`（新） | 承载本次 hold 的 `(duration, animation)`，`use` 里由两边**各自**写入（`Item.use` 客户端也会跑） |
| `MxtDataComponents.TECHNIQUE_HOLD` | 注册为**非持久化**、但网络同步的组件：它是"此刻正在长按"的瞬时状态，落盘没有意义 |

因此 `TechniqueItemService` 里那一整套 `arm`/`holdComponent`/`clearHold`/`onItemClick` **全部删除**——不再往 stack 上写 `CONSUMABLE`，也就没有"抢在游戏吃掉它之前把组件摘掉"这场竞态。物品不是食物，**永远不会被消耗**。

**设计约束（必须让数据包作者知道）**：**声明了 `learn_time` 的绑定，其物品必须是 `TechniqueManualItem`**。即时学习的绑定不受影响，仍然可以用普通 `Item`。为避免再次出现"数据包完全合法但什么都没发生"，`TechniqueItemService.use` 在遇到"有 hold 绑定但不是该物品类"时会**打一条 ERROR 日志并点名绑定**（同一功法只报一次，不刷屏）。这是这类配置唯一能被发现的渠道。

**测试**（`MxtTestMod.verifyHoldLifecycle` 重写）：

| 步骤 | 断言 |
| --- | --- |
| 0 | 测试手册**和**主模组的玉简都是 `TechniqueManualItem`——**这条就是本该一开始就有的守卫** |
| 1 | 物品报出的 duration/animation **等于绑定里的值**（不是硬编码） |
| 2 | `Finish` 学会，且**物品数量不变**（不是食物） |
| 3 | 读完手上不留 hold 标记 |
| 4 | 中途 `Stop` 不学会、标记清掉 |
| 5 | 重复阅读不毁书 |

**变异测试验证**：把两个 override 改成"看不到 hold"（`return 0;` / `return ItemUseAnimation.NONE;`，即**精确复现客户端当时的计算结果**），审计报：

```
IllegalStateException: The manual reported a duration of 0 instead of its learn_time of 60
```

改回即通过。这证明新断言真的守在坏掉的那一环上。

**测试中发现的真实差异**：`Stop` 传的是**手上的活 stack**（`releaseUsingItem` 第 3641-3643 行先把手持 stack 读回 `useItem` 再发事件），而 `Finish` 传的是**副本**（`completeUsingItem` 第 3609 行 `this.useItem.copy()`）。两者不对称。测试里我先按 `Finish` 的习惯给 `Stop` 传了 `.copy()`，审计立刻报"标记没清掉"——说明这类不对称**必须按真实调用点写测试**，凭习惯套用就会测出一个不存在的情形。

**§15/§17/§18 的定位**：它们记录的服务端问题（学错事件、清错对象）都是真的，但**都不是玩家看到的那个现象的原因**。§18 结尾那句"下一次我会先把 `completeUsingItem` 整段反编译确认"这次做到了，也正是靠完整读 `Item.use` + `onSyncedDataUpdated` 才找到了真正的一环。

## 21. 定案：mixin 到 Item 的三个取值点（2026-09-13）

**起因**：用户提出第三个方案——**不要再用组件，直接 mixin 到"读组件的地方"**，并让我参考他之前写的 `E:\Java\Throwable`。

看过之后确认这是对的方案，而且他之前已经实现过同一形状（`ItemMixin`：`@Inject` 到 `getUseAction` / `getMaxUseTime` / `use` / `onStoppedUsing`）。**§20 我引入 `TechniqueManualItem` 是设计失误**：这个模组自己的承诺是"绑定作用于任何已注册物品"（`ItemBindingService` 类注释明写 *items already registered by Minecraft, a mod, or KubeJS*），`items` 字段也接受 tag，而 **KubeJS 注册的物品不可能继承我的类**。子类方案等于把这个承诺砍掉。

**最终形态**（`mixin/ItemMixin`，注册在 `mxt.mixins.json` 的 `mixins` 里）：

| 注入点 | 作用 |
| --- | --- |
| `Item.use` | 有长按绑定时 `startUsingItem(hand)` + `CONSUME`，自己接管这次交互 |
| `Item.getUseDuration` | 返回绑定的 `learn_time` |
| `Item.getUseAnimation` | 返回绑定的 `hold_animation` |

**故意不注入 `finishUsingItem`**：手册是普通物品、没有 `CONSUMABLE`，原版实现本身就返回原 stack，**不会被吃掉**。§18 那场"抢在 `onConsume` 之前摘组件"的竞态**因为不再使用那个组件而自然消失**——不是赢下竞态，而是没有竞态了。

**顺带删掉的东西**：`TechniqueManualItem`（删）、`TechniqueHoldComponent`（删）、`MxtDataComponents.TECHNIQUE_HOLD`（删）、`TechniqueItemService.onUseStop` 与那条"物品类不对"的 ERROR 日志（都不需要了）。`MxtItems` 的玉简和测试手册恢复为普通 `Item`。

**取值表**（`TechniqueHoldLookup`）：`getUseDuration`/`getUseAnimation` 在**渲染循环里每帧**都会被问（`ItemInHandRenderer` 的 281/455/493/520/543/568），所以不能每次查注册表。做法是**只捕获长按绑定列表**，按物品惰性匹配并记忆（匹配只看物品身份——已核对四种匹配项 `Item`/`Tag`/`Wildcard`/`Regex` 都不看组件，所以按 `Item` 记忆是安全的）。

**踩到并修掉的坑**：最初版本在事件里遍历全部已注册物品、用 `new ItemStack(item)` 预建整张表 —— **直接崩掉服务器启动**：

```
Exception caught during firing event: Components not bound yet
Failed to load datapacks, can't proceed with server load
```

追到 `Holder$Reference.components()`（`Holder.java:271-274`）：构造 `ItemStack` 会读物品 holder 的组件表，而**数据包加载阶段物品 holder 还没绑定**。所以：**不能在数据包加载期构造 ItemStack**，改为只读绑定列表、按需匹配。顺带确认 `.value()` 在那个时机是安全的（去掉 ItemStack 后即通过）。

**"什么时候捕获"**：`TagsUpdatedEvent`（服务端 `ServerDataLoad` / 客户端 `ClientPacketReceived` 都会触发，两端各自捕获）**加** `ServerStartedEvent`（服务端等数据包完全加载后再捕获一次）。因此**不需要任何"客户端注册表访问"的桥**，也就天然避开了 §20 里指出的那个坑。

> **关于那个坑**：用户那份 `ThrowableRegistry.DYNAMIC_REGISTRY_GETTER` 只从 `MinecraftServer` 取注册表，多人客户端上 `SERVER == null` → `manager == null` → **整个跳过覆盖** → vanilla 返回 0，而服务端返回数据包值，两边不一致。单人下测不出来（集成交互服务端在客户端进程里也非 null）。这是**同一个 bug 的同一形状**。MiXianTu 这边靠"两端各自从自己的同步数据捕获"避免了它。
> 另外 `ThrowableItemMixin.getUseAction` 之所以没问题，是因为它的动作只依赖一个**同步的 tag**（`isIn(THROWABLE)`），根本不需要注册表——**能让属性退化成 tag 的就不需要跨端取数**，这点值得保留借鉴。（用户要求本次不动那个项目。）

**测试**（`MxtTestMod.verifyHoldLifecycle` 重写）——这次是**真的把 use 周期跑完**：

| 步骤 | 断言 |
| --- | --- |
| 0 | 取值表里有这本手册；**即时**手册不在表里 |
| 1 | `getUseDuration` == 绑定的 `learn_time`、`getUseAnimation` == 绑定的 `hold_animation`（普通物品会答 0 / NONE，所以**这条同时证明 mixin 生效**） |
| 2 | **真实周期**：`startUsingItem` 后 tick 30 次**不能**学会（证明时长被遵守），再 tick 到 65 次**必须**学会；物品数量不变；结束后不再 `isUsingItem` |
| 3 | 已会状态下重复读两次不毁书 |

第 2 步是**第一次**真正覆盖"中间那一段"——以前几轮的测试全都是直接调 mod 自己的 handler，所以中间坏掉也全绿。

**变异测试（两次，都确认断言站在坏掉的那一环上）**：

| 变异 | 审计报出 |
| --- | --- |
| 从 `mxt.mixins.json` 移除 `ItemMixin` | `The manual reported a duration of 0 instead of its learn_time of 60` ← **正是客户端当时算出的那个 0** |
| 从 `onUseFinish` 移除 `learn(...)` | `A completed hold taught nothing` ← 周期跑完却没学会 |

**代价（说清楚）**：三处注入 vanilla `Item`，而这块区域**变动频繁**（1.21.2 的 `Consumable` 重构就是例子），每次升 MC 都要重验。好在 `mxt.mixins.json` 里 `required: true` + `defaultRequire: 1`，目标方法一旦改名/消失会**直接启动失败**，而不是静默失效。

## 22. 以服务端为准结束阅读姿势（2026-09-13）

**起因**：用户报"学习正常了，但中途动画会中断"，随后又报"进度条到 100% 好像不止三秒"，并自己用 tick query 对齐后判断"**应该就是服务端跑不满**"。

**根因：一次 use 周期被计时两次，而两次没有任何机制保持同步。**

| | 客户端 | 服务端 |
| --- | --- | --- |
| 计数器 | `LivingEntity.useItemRemaining` | 同名，各算各的 |
| 起始 | 点击时本地 `startUsingItem` | 收到包后 `startUsingItem` |
| 速率 | 客户端自己的 20 TPS | 服务端的实际 TPS |
| 驱动什么 | **姿势**（`ItemInHandRenderer:492` 的 `剩余 > 0`）与进度显示 | **真正的完成/学会** |

服务端只要跑不满 20 TPS，它的 60 tick 在真实时间里就比客户端的 60 tick 长。于是**姿势先掉、功法后到**——玩家看到"动画中途断了"，而进度条（我特意只用服务端发包）还在走。审计实测那条 `[mxt] read finished: 60 server ticks in ... ms` 就是为量这个加的。

**修法（`LivingEntityMixin`）**：注入 `LivingEntity.getUseItemRemainingTicks()`，**只在客户端、且确实还在使用中、且本地计数已 ≤ 0 时**，返回一个 1..9 的循环值而不是 0。

- 姿势因此一直挂着，直到**松手**或**服务端读完**——两者都会清掉 using 标记，姿势随之结束
- 服务端的计数**完全不动**：它才是"读完没有、学会没有、`Stop` 报多少"的依据（审计三项都钉着）
- 返回值取循环值而不是常数：允许的姿势都是短循环动作，返回常数会把姿势冻住一帧，看起来和断掉一样糟
- **而且不能是任意循环值**（见下）——必须是**把跑负的计数器按模折回正区间**，相位正好接在原版停下的地方

**一次返工：循环方式选错导致"抽抽"**。初版返回 `1 + floorMod(tickCount, 9)`，读数 `1..9` 然后 **9 → 1 直接跳回**，相位一次倒退 8 tick，于是**每 9 tick 抽一下**（用户原话："现在因为卡住了在抽抽"）。

而原版刷子的循环是 `0 → 9` 的**反向**跳变：`applyBrushTransform` 里 `scaledUsageTime = 1 - (remaining%10 - frameInterp + 1)/10`，`cos(2π·x)` 在 x 的 0/1 接缝上是连续的，所以原版那个看起来是顺滑的。

正确做法（`TechniqueItemService.loopingUseRemaining`）：`phase = floorMod(remaining, 10)`，为 0 时返回 10。因为 `useItemRemaining` 在客户端会继续递减到负数（`updatingUsingItem` 依旧每 tick `--`，只是不完成），`floorMod` 正好把它折回原版本该到达的相位：`0 → 10(相位0)`、`-1 → 9`、`-2 → 8`……**与"如果它继续数下去"完全一致，接缝为零**；且恒 ≥ 1，姿势条件不会掉。周期 10 是 vanilla 刷子循环的长度，已提为常量 `POSE_LOOP_TICKS` 并注明来源。

**顺带修掉一个显示 bug**：`updateUsingItem` 是"先发 Tick 事件、再 `--`、再完成"，所以一次阅读**最后一个事件的 `remaining` 是 1，不是 0**（审计实测 `last reported 1`）。原来算出来最大只有 `(60-1)*100/60 = 98%`——**进度条永远到不了 100%**，还会停在 98% 再显示几秒。新增 `displayPercent`：剩余 ≤ 1 按 100% 显示。另外松手时补发一条空动作栏消息，否则半截的条子会继续挂着。

**测试**：审计新增 `displayPercent(60,1)==100`，以及"最后 tick 规则不外溢"（`displayPercent(60,60)==0`、`(60,30)==50`）。**服务端不受影响由既有断言保证**：30 tick 不得学会、约 60 tick 必须学会——如果这个 clamp 泄漏到了服务端，这两条会立刻失败。

**这里的教训**：客户端姿势和服务端判定用了两个独立计时器，是 vanilla 的固有设计（吃食物同理）。任何把"时长"赋予意义的 mod 都会撞上它；靠"两边各自数 60"是默认它就同步，而它不同步。








