# `mxt:disabled` 标签的删除

**基准**：本仓库工作树（2026-09-27，紧接「符箓容量改倍率」那一轮之后）；对着 `src/main/java/com/iafenvoy/mxt/registry/MxtDatapackRegistries.java` 与 `docs/数据包格式.md` 核过。

**结论**：`mxt:disabled` 这套"标签停用"机制**整体删除**（用户 2026-09-27 点名："这玩意要靠约定不稳定，而且 neoforge 的 resource condition 够用"）。
停用一条定义改成**加载期**的事：写在定义文件自己的 `neoforge:conditions` 里，条件不成立的条目**根本不进注册表**。

## 1. 删掉的是什么

- 固定标签 `mxt:disabled`（文件固定在 `data/mxt/tags/mxt/<注册表名>/disabled.json`，标签 ID 自带 `mxt` 命名空间）。
- `MxtDatapackRegistries` 里那层过滤与判据：`DISABLED_TAG`、`isDisabled(key, id)`、`isDisabled(key, holder)`，以及 `get(key, id)` / `get(Provider, key, id)` / `get(key, holder)` / `get(Provider, key, holder)` / `holder(...)` / `holders(...)` 六处 `holder.is(disabled)` 过滤。
- `Elements.enabled(Holder)` / `enabled(Optional)` 与 `Elements.matches(...)`、`SpiritRoot.names(...)` 里那半条"被停用的元素不算元素"的规则；`Elements` 现在只回答"这个实体带着哪些元素"。
- 只为了报告"这条被标签停用了"而存在的失败值：`CurseService.DefinitionState.DISABLED` 与 `ApplyFailure.DISABLED`、`TechniqueService.Failure.DISABLED`、`ContractService.Failure.DISABLED`、`QualityUpgradeService.Failure.DISABLED`，以及 `ItemQualityService` 里那个只做停用判断的 `enabled(...)` 私有助手（连同三处 `.filter(...)` 一起删）。
- 11 条只服务这条路的文案键（`command.mxt.identity.unknown`、`command.mxt.aura.unknown_type` / `unknown_element`、`command.mxt.ability.unknown`、`command.mxt.talisman.unknown`、`command.mxt.formation.bind.unknown`、`command.mxt.tribulation.unknown`、`command.mxt.quality.chain.disabled`、`command.mxt.quality.failure.disabled`、`contract.mxt.failure.disabled`、`actionbar.mxt.technique.failure.disabled`；两份 lang 各 11 条，键数 899 → 888）。
- 测试包里三个 `data/mxt/tags/mxt/{element,spirit_root,curse}/disabled.json`。

## 2. 为什么

1. **它是约定，不是机制**：条目进得进注册表、被别人指向、被存档持有全都照旧，只有"读到之后要不要认它"由一堆散在各服务里的 `isDisabled` 决定。少写一处就是一个静默的不一致（`research/21_元素系统设计补完.md` §D1 记录的正是元素侧漏过滤那次）。
2. **它让"存在"有两种意思**：`mxt:disabled` 里的条目在注册表里、能补全、能被别人安全指向，但玩法上等于不存在。调用方要同时记住"在不在"和"算不算"，`Element` / `Technique` / `ItemQuality` / `QualityChain` / `SpiritRoot` / `ContractType` 六个模块各写了一遍这套判断。
3. **NeoForge 已经提供了同一条语义，而且更强**：`RegistryLoadTask.read` 被 NeoForge 改成 `ConditionalOps.createConditionalCodec(...)` 解每个条目，条件不成立时记一个 `SKIPPED_ELEMENT_MARKER` 把该条目跳过（只留一条 `Skipping loading registry entry … as its conditions were not met` 的 DEBUG 日志，不算加载错误），`ResourceManagerRegistryLoadTask` 的 ops 也换成 `ConditionalOps` 以便条件里能读上下文（`neoforge-26.1.2.99` 的 `patches/net/minecraft/resources/{RegistryLoadTask,ResourceManagerRegistryLoadTask}.java.patch`，逐行核过）。因为 `mxt:` 的 35 张表都是 `RegistryDataLoader.RegistryData`，这套对它们一视同仁，不需要任何运行时判据。

## 3. 现在的行为

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "example_addon" }
  ],
  "default": 0.0
}
```

- 可用条件：`never` / `always`（无字段）、`mod_loaded`（`modid`）、`registered`（`registry` 默认 `minecraft:item`、`value`）、`and` / `or`（`values`）、`not`（`value`）、`feature_flags_enabled`（`flags`）。
- 条件成立时 `neoforge:conditions` 在交给定义 Codec 之前被剥掉，正常字段照常读；不成立时该条目被跳过（DEBUG 日志 `Skipping loading registry entry … as its conditions were not met`）。
- **按标签判断的条件（`tag_empty`）不能用在这里**：这一层解码时标签还没绑定，`ICondition.IContext.TAGS_INVALID` 会直接抛 `UnsupportedOperationException`。配方、战利品表那一层可以用。
- **没有中间状态**：条件不成立的条目等同"定义不存在"，指向它的 Holder 引用会一起解码失败（必填引用直接让加载失败，容错列表丢掉那一项）。"留着定义但不生效、别人还能引用"这条路今天不存在——想要它就等于想要回标签，所以不做。

行为差异逐条：

| 场景 | 旧（标签） | 新（条件） |
| --- | --- | --- |
| 包作者停用一条定义 | 写 `data/mxt/tags/mxt/<表>/disabled.json` | 写进定义文件自己的 `neoforge:conditions` |
| 别人指向它 | 照常解析，读到时被过滤掉 | 引用它的定义**跟着解码失败** |
| 附件里已存的引用 | 仍在，`isDisabled` 判为不生效 | 同一次会话里仍在（`/reload` 不重解附件）；**世界加载**时附件重新解码，缺失的那一条会被容错列表丢掉，所以下次进世界就干净了。要问"还在不在"得按 id 回查注册表 |
| `/reload` 后 | 立刻按标签生效 | 旧引用不会自己消失（附件不重解）；按 id 回查的地方立刻生效（`Elements.of` / `activeSpiritRoots` / `SecretRealmService.enter`）。重新进一次世界则把缺失的引用连同附件一起清掉 |
| 命令补全 | 停用条目**会**出现在补全里、执行时被拒 | 不进注册表就不进补全，不存在"补全里有、执行时被拒" |
| 灵根 / 体质 | 与"关闭但持有"的开关是两件事 | 仍然是两件事（开关是附件里的 `disabled_spirit_roots` / `disabled_physiques`，没动） |
| 诅咒实例 | 停用与删除都冻结 | 只有"定义不在注册表里"会冻结（`DefinitionState` 只剩 `ACTIVE` / `UNKNOWN`） |

## 4. 落点

- 核心：`registry/MxtDatapackRegistries.java`（删过滤；`rawHolder` 更名 `holderOrEmpty`，因为"裸查"这个说法只对标签有意义）。
- 元素：`runtime/cultivation/Elements.java`、`data/cultivation/SpiritRoot.java`、`runtime/damage/DamageElements.java`、`runtime/damage/DamageCalculationService.java`、`runtime/element/ElementReactionService.java`、`runtime/spirit/SpiritBurstService.java`、`runtime/cultivation/CultivationAffinity.java`、`runtime/cultivation/ItemElements.java`、`runtime/creature/CreatureProfileService.java`、`data/condition/builtin/entity/{HasElement,ElementAttachment,AuraElement,HasSpiritRoot,HasTechnique}EntityCondition.java`、`loot/HasSpiritRootLootCondition.java`、`screen/**`、`compat/jei`、`render/AuraZoneRenderer.java`。
- 各模块：`CurseService` / `CurseTriggerSubscriptions` / `TechniqueService`（连 `known(...)` 一起删）/ `ContractService` / `QualityUpgradeService` / `QualityChainService` / `ItemQualityService` / `AbilityEventBridge` / `FlightEventBridge` / `SecretRealmService`（这条**保留**存在性检查，只是不再有标签那一半）。
- 命令：`AbilityCommand` / `AuraCommand` / `FormationCommand` / `MxtCommand` / `PhysiqueCommand` / `QualityCommand` / `SpiritRootCommand` / `TalismanCommand` / `TribulationCommand`（每处删掉一条 `isDisabled` 拒绝分支；`SpiritRootCommand` / `PhysiqueCommand` 的 `unknown(...)` 助手随之删除）。
- 测试包：三个 `disabled.json` 标签删除；新增 `mxt_test:condition_gated` 元素（文件里写 `{"type": "neoforge:never"}`）作为替代机制的实机证据，`/mxt_test element` 第 4 条腿改成断言"被条件挡掉的条目不在注册表、普通条目在"；`inert` 元素与 `inert_root` 现在都是**正常内容**，所以第 6b / 第 8 条腿的期望值跟着改（`inert_root` 授予会被 `conflicting_elements` 挡住）。
- 文档：`docs/数据包格式.md`（「文件位置」的停用一节重写为条件表）、`docs/guide/datapack/overview.md`、`docs/guide/java/api.md`、`docs/guide/play/commands.md`、`docs/guide/kubejs/api.md`、`docs/guide/datapack/examples.md`、`docs/物品灵气数据包.md`、`AGENTS.md` §4 两条、两份 README 的 FAQ、文档站中英对应页。

## 5. 不做 / 推迟

- **不做兼容读**：老包里那份 `disabled.json` 现在是个没人读的标签文件（原版标签系统照常加载它，只是没有任何服务看它），被它列出的定义会**全部恢复生效**。项目未发布，不留迁移层；包作者要恢复旧效果就把条件写进定义文件。
- **不做"引用不受影响"的替身**：想让"停用"不影响别人解析，就必须让条目仍然进注册表——那正是被删掉的语义，不再另造一个。
- 测试包里 `mxt_test:reload_fixture`（灵根）与 `mxt_test:curse_disabled_probe`（诅咒）两个夹具原本只为这条机制存在，现在没有任何探针引用；**保留未删**，等下次清理夹具时一并处理（`mxt_test:scenario/registry_reload_missing` 标签同理）。
- `MxtDatapackRegistries.isTagged(...)` 两个重载在本轮之前就已经零调用（它是通用标签查询，不是停用机制的一部分），**保留未删**，留待"零引用工具方法要不要清"一并拍板。

## 6. 验证

- `.\gradlew.bat compileJava compileTestModJava processTestModResources --console=plain`。
- 两份 lang 键集合一致（本轮各减 11 条）。
- 测试包全部 JSON 解析通过；`git diff --check` 干净。
- 文档站仓库：`pnpm run check:i18n` / `check:links` / `check:mermaid` + `pnpm run build`。
- **实机未跑**（未获准）：跑 `/mxt_test element` 时应看到 `condition-gated element absent=true plain element present=true OK`，汇总 `element probe: OK`。
