# Holder 修复清单（可勾选）

**配套**：[`Holder使用审计.md`](Holder使用审计.md)（语义基线与逐条论证看那份）。本清单只列**调用点**，按**类名 + 方法名**定位，
**不给行号**。
**用法**：把 `[ ]` 改成 `[x]` 即完成。批次内按类名字母序，方便按类搜。

> **本轮复核（AI 逐条对代码查过；`compileJava` + `compileTestModJava` BUILD SUCCESSFUL）**
> 六个批次全部重查了一遍，**「确认不改」的条目已按你的要求从本清单删掉**（判据留在审计报告 §6，需要时回查）。
>
> | 批次 | 结论 |
> | --- | --- |
> | 批 1 | ✅ 全部完成（最后一条 javadoc 也补了） |
> | 批 2 | ✅ 全部完成（`SecretRealmService#enter` 与两条误勾本轮一并修掉） |
> | 批 3 | ✅ **本轮完成**：改反的解析分支修回来了，`UNBOUND` 整条链 + 3 份 lang 键 + 探针那一腿一起清掉 |
> | 批 4 | ✅ **全部完成**（2026-10-15 收掉最后 13 条） |
> | 批 5 | ✅ **全部完成**（2026-10-15（三）收掉最后的炼金一组 7 条） |
> | 批 6 | ✅ 全部完成 |
>
> **本轮验证**：`compileJava` + `compileTestModJava` BUILD SUCCESSFUL；两份 lang 键集合一致（**1177** 个键，删掉那 3 个键之后 zh/en 同步）。
>
> **2026-10-15 追加**：按你的要求把 `AbilityAttachment` 的 id 账本改成 `Holder<Ability>`（连带 `DataStorageHolder`、`AbilityStorage` 与全部调用点；见批次 5 的说明）。这两条原本在下面那张「不改」单子上，**现在改成"做完"**。
>
> **已被删掉的「不改」条目**（别再找它们）：`AuraCommand` 的 `source_kind` lang 处、`MxtKubeJsEventDispatcher#getSource`、
> `SecretRealmRegistry#load` / `#aliases`、`ForgingMenu#methodId`、`IconReference#toString`、`ChequeTableMenu`、
> `RegistryCodecs#matchesKey`、`PillUsageAttachment`、`Abilities#resolve`（`AbilityAttachment` 与 `DataStorageHolder` 原本也在这张单子上，2026-10-15 按你的要求改成 holder 键 ⇒ 已从「不改」变成「做完」，见批 5），
> 以及批 3 的 6 处"显示层 id"（`ArtifactDescription#describe`、`ForgingBindingTooltipAppender` 的 `advancedId` ×2、
> `ItemBindingTooltipAppender#appendGranted` ×2、`SpiritRootTooltipAppender`、`PhysiqueTooltipAppender`、`ResourceCost#id`）
> —— 理由：**显示层用 id 是合法口径**，`HolderHelper.id` 的 `EMPTY` 兜底只对 `Holder.Direct` 可达，注册表给的 holder 到不了。
> 另见原 **附 A**（整表已删，理由同样在审计报告 §6）。

---

## ✅ 本轮修掉的两处（原 ⚠1 / ⚠2，均已落地）＋ ⚠3（批 5 的收尾）

### ⚠1 → 已修 `ItemBindingService#resolvePill`

原来那一段把**解析分支删掉了、只留下 `unbound`**（于是凡"说出了自己是哪颗丹"的堆一律走 `unbound`：`PillService#check` 直接 `Failure.UNBOUND`、提示框打红字、`effects()` 恒空、`registerUse` / `apply` 都不再发生），而编译是绿的 —— 只有实机跑丹药探针才会发现。现已恢复：

```java
Holder<Pill> source = declared != null ? declared : identity == null ? null : identity.value().pill();
if (source == null)
    return component == null ? PillResolution.none()
            : PillResolution.componentOnly(component.applyTo(Pill.defaults()));
Pill effects = component == null ? source.value() : component.applyTo(source.value());
return identity == null ? PillResolution.componentOnly(source, effects) : PillResolution.bound(identity, source, effects);
```

**选的口径是"删链"**（依据你的前提：holder 与 key/value 一一对应，注册表给不出"有 key 没 value"的引用），所以 `unbound` 这一整条链连带那一腿探针都清了 —— 见批次 3。

### ⚠2 → 已修 `SecretRealmService#enter`

判据放回了唯一入口，而且**不再走 id 往返**（用 holder 自己的 key 问当前注册表）：

```java
if (definition.unwrapKey().flatMap(server.overworld().registryAccess()::get).isEmpty())
    return Result.rejected(Failure.DISABLED);
```

同时清掉了 `MxtDatapackRegistries` 那个没人用的 import。这条判据**不属于** `isBound()` 那一类无效检查：holder 在**定义被移出数据包之后仍然绑着旧值**，只有拿当前 access 重新查一次表才答得出"当前包还给不给"。

### ⚠3 → 已修 锻造搁浅材料（批 5 改 `ForgingTableState` 时留下的口子）

批 5 把 `blueprint/plan/session` 换成 holder 键之后，`ForgingTableState` 的构造器改成"重建不出计划就把会话丢掉并 unlock"，**但没有动 `consumed`**。那一支原来是抛异常的（codec 构造器里抛 ⇒ `forging` 整个字段读不出来，材料一样没，而且更响），所以是**同一个角落、从"只有存档损坏才够得着"变成"数据包删一个 `forging_method` 就够得着"**——但结果是**材料搁浅**：`active()` 从此恒为 false，而 `returnLocked` 只在 `cancel`/`fail` 里跑（两者都要求 `active()`），于是 `consumed` 既交不出去，又会在下一炉 `lock(...)` 时被**覆盖**。

已补：`ForgingWorkstationService#returnStrandedMaterials(ForgingSurface)` 把搁浅的材料塞回输入槽（复用 `insertIntoInputs`），塞不下的留在 `ForgingTableState.consumed` 里并打一条 warn（区块加载时没有玩家可以给）；调用点是 `ForgingTableBlockEntity#setLevel` —— 那一刻 `level` 已就位、状态也已加载完，而容器读写不依赖 level（`loadAdditional` 里 `level` 还是 null，所以不能放那里）。`ForgingTableState#retainConsumed(List)` 是那条"塞不下的留着"的唯一写入口。

**实机才能看到这一处**（编译看不见）：需要放一次桌子、装材料、开一炉、再把那张蓝图冻结的某个 `forging_method` 从包里删掉、然后重载区块。

---

## 批次 1 — 删死代码（不动签名、不动序列化，最低风险）

判据：`isBound()` 对**从注册表 / 从 `RegistryFixedCodec` 拿到**的 holder 恒为 `true`（见审计报告 §1.1、§1.4）。**唯一例外**
是解码期（codec 的 `validate` / `xmap` 内）——本批次**不含**那三处。**本轮复查：全仓 `src/` 已无任何 `isBound()` 调用，本批全部落地。**

| 勾   | 类#方法                                                              | 现状                                                                                                                                                 | 改成                                                                                                         |
|-----|-------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| [X] | `PillUsageAttachment#dose`                                        | `entries.get(pill)` 命中后还有 `idOrNull` + O(n) `key.is(id)` 回退扫描                                                                                      | 删回退，只留 `return this.entries.get(pill);`；同时删方法上那条 "Holder identity is not stable across a reload" 注释（理由是错的） |
| [X] | `PillUsageAttachment#record`                                      | `Identifier id = HolderHelper.idOrNull(pill);` + `removeIf(key -> key != pill && key.is(id))`                                                      | 两行都删，直接 `entries.put(pill, new Dose(...))` + `markDirty()`                                                 |
| [X] | `ItemPickerManager`（静态块里 `register(ALCHEMY_FURNACE, …)` 的 lambda） | `if (!holder.isBound()) return List.of();`                                                                                                         | 删整行（holder 来自 `provider.lookup(...).listElements()`）                                                       |
| [X] | `ItemPickerManager#registerOrderedQualities`                      | `if (id == null \|\| !filter.test(id)) continue;`                                                                                                  | `null` 那一半不可能（`idOrNull` 对注册表 holder 不为 null）⇒ 只留 `!filter.test(id)`                                       |
| [X] | `ItemBindingTooltipAppender#appendWeapon`                         | `if (identity == null \|\| !identity.isBound()) return;`                                                                                           | `if (identity == null) return;`                                                                            |
| [X] | `ItemBindingService#onUseFinish`                                  | `if (identity != null && identity.isBound())`                                                                                                      | `if (identity != null)`                                                                                    |
| [X] | `PillItem#getName`                                                | `if (pill != null && pill.isBound())`                                                                                                              | `if (pill != null)`                                                                                        |
| [X] | `PillService#usageFailure`                                        | `if (!pill.isBound()) return Optional.of(Failure.UNBOUND);`                                                                                        | 删整行（连带批次 3 的 `UNBOUND`）                                                                                    |
| [X] | `PillService#registerUse`                                         | `if (entity.level().isClientSide() \|\| !pill.isBound()) return;`                                                                                  | `if (entity.level().isClientSide()) return;`                                                               |
| [X] | `QualityService#carry`（`carry(Supplier, Function)` 里的 lambda）     | `holder == null \|\| !holder.isBound() ? Optional.empty() : holder.value().defaultQuality()`                                                       | 去掉 `\|\| !holder.isBound()`                                                                                |
| [X] | `QualityService#intrinsic`                                        | `registry == null ? (direct.isBound() ? … : empty) : direct.unwrapKey().flatMap(registry::get).map(h -> h)`                                        | `return Optional.ofNullable(stack.get(MxtDataComponents.QUALITY.get()));`；`registry` 参数连同调用点一起清            |
| [X] | `SpiritHerbService#potency`                                       | `if (!entry.getKey().isBound()) continue;`                                                                                                         | 删整行                                                                                                        |
| [X] | `TechniqueTintSource#calculate`                                   | `holder == null \|\| !holder.isBound() ? null : holder.value()`                                                                                    | `holder == null ? null : holder.value()`                                                                   |
| [X] | `Chain#indexOf`                                                   | 注释说 "Compared by id"，实现是身份比较 `this.nodes.get(index) == node`；而 `ChainCache` 按 `Holder`（`equals`）建键 ⇒ `chainOf` 能命中、`rankOf`/`contains` 却为 -1/false | 统一到 `equals`：`Objects.equals(this.nodes.get(index), node)`，并改注释（**已改**）                                    |
| [X] | `Costs#validate`                                                  | `Set<Identifier> resources` / `Set<Identifier> auras` 做重复检测                                                                                        | 改 `Set<Holder<Resource>>` / `Set<Holder<Aura>>`（**已改**，报错文案保留 `HolderHelper.id`）                           |
| [X] | `CultivationMethodService#convert`                                | `Identifier actionId = HolderHelper.id(action);` 全方法未被读（`:90` 用的是 `action::equals`）                                                                | 删整行（**已改**）                                                                                                 |
| [X] | `Abilities#resolve`                                               | 尾部 `.map(holder -> holder)` 空转                                                                                                                     | 删 `.map(...)`                                                                                              |
| [X] | `AlchemyFurnaceTooltipAppender#loadedWall`                        | `… .map(holder -> holder)` 空转                                                                                                                      | 删 `.map(...)`（整段另见批次 2）                                                                                    |
| [X] | `AlchemyFurnaceCasingBlockEntity#material`                        | `… .map(holder -> holder)` 空转                                                                                                                      | 删 `.map(...)`（整段另见批次 2）                                                                                    |
| [X] | `AlchemyWorkstationService#furnaceDefinition`                     | `… .map(holder -> holder)` 空转                                                                                                                      | 删 `.map(...)`（整段另见批次 2）                                                                                    |
| [X] | `AlchemyWorkstationService#environment`                           | `… .map(found -> found)` 空转                                                                                                                        | 删 `.map(...)`                                                                                              |
| [X] | `AuraService#findHolder`                                          | 尾部 `.map(holder -> holder)` 空转                                                                                                                     | 该方法整体在批次 4 删除（**已删**）                                                                                        |
| [X] | `SpiritHerbService#find`                                          | 尾部 `.map(holder -> holder)` 空转                                                                                                                     | 删 `.map(...)`（**改为显式拓宽转型**，见批 2 说明，别再删）                                                                      |
| [X] | `SpiritHerbService#findBlock`                                     | 尾部 `.map(holder -> holder)` 空转                                                                                                                     | 同上                                                                                                         |
| [X] | `SpiritHerb#qualityFor`（javadoc）                                       | javadoc 里那句 "a rung whose tier is unbound is skipped"                                                                                             | **本轮已删**（`unbound` 这个状态已经不存在）                                                                              |

---

## 批次 2 — 单行等价改写（比较、哨兵、id 往返）

> **本轮完成情况**：全部改完，编译通过（`compileJava` + `compileTestModJava`）。上一轮复核剩下的 3 条也一并收掉了：
> - `CultivationMethodService#ActiveCultivation#id()`：四处 `active.id()` 改成 `active.resource()`（走 `ResourceService.change` 的 `Holder<Resource>` 重载），`id()` 方法与 `HolderHelper` import 一起删。
> - `FlightControlAbilityType`（`archetype()` 过滤处）：`skill.is(HolderHelper.id(context.ability()))` → `skill.is(context.ability())`，`HolderHelper` import 删掉。
> - `SecretRealmService#enter`：判据放回入口，见上面 ⚠2（改用 holder 自己的 key 查，不再走 id 往返）。
> - **改了口径（已落地，视为完成）**：`SpiritBurstEntity#setAura` / `TalismanProjectileEntity#setTalisman` —— 没有把形参收成 `Holder.Reference`/`ResourceKey`，而是**保留 `Holder<T>` 形参、把不可达的 `unwrapKey().orElseThrow(...)` 换成 `HolderHelper.id(...)`**，并把"NBT 里只有 id、所以必须是注册表 holder"写成了方法注释。等价且更小，两条都在。
> - **改了口径**：`SecretRealmService#enter`（另见 ⚠2）—— 原建议是把存在性判据挪到 `SecretRealmTokenItem`；实际两处都没有了。
> - **属批次 4**：`AuraService#getSensedAura`（**已完成**）。
> - **更正一条判断**：`Abilities#resolve` / `AlchemyWorkstationService#environment` / `SpiritHerbService#find` / `#findBlock` 的 `.map(h -> h)` **不是空转**——`MxtDatapackRegistries.holder` 返回 `Optional<Reference<T>>`、`holders` 返回 `Stream<Reference<T>>`，而目标类型是 `Optional<Holder<T>>`，`Optional` 不变所以这个 `map` 在做 `Reference → Holder` 的拓宽。前两处随 `holder()` 改成返回 `Optional<Holder<T>>` 后已可去掉；`SpiritHerbService` 那两处源自 `holders()`（返回类型不能改，别处要 `Reference`），所以保留为**显式转型** `.map(holder -> (Holder<SpiritHerb>) holder)`，别再删。
> - **顺带修掉两处已断的调用点**（不是本批条目，但不修编译不过）：`MxtKubeJsApi#tryBreakthrough`（`CultivationService.attempt` 的 `Identifier` 重载已被删除）、`ResourceService#resource` 的返回类型（随 `holder()` 拓宽而调整）。

| 勾   | 类#方法                                                  | 现状                                                                                                                                                                                                       | 改成                                                                                                                                                                                    |
|-----|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [x] | `AbilityService#cooldownOf`                           | `channelled.is(HolderHelper.id(ability))`                                                                                                                                                                | `channelled.is(ability)`                                                                                                                                                              |
| [x] | `ArtifactService#holds`                               | `HolderHelper.id(ref).equals(abilityId)`（`abilityId` 是 `Identifier` 参数）                                                                                                                                  | 参数换 `Holder<Ability>` ⇒ `ref.is(ability)`（喂它的 `ArtifactStorageContainer#carrier` 手里是 `Holder<Ability>` 字段）                                                                            |
| [x] | `ArtifactStorageContainer#carrier`                    | `ArtifactService.carried(access, viewer, HolderHelper.id(this.ability))`                                                                                                                                 | 传 `this.ability`（`carried` 全仓唯一调用方，签名一起改）                                                                                                                                             |
| [x] | `BoundBeastsAttachment#sameType`                      | `HolderHelper.id(left).equals(HolderHelper.id(right))`，注释用 "holder from another registry instance is not the same object" 当理由                                                                            | `return left.is(right);` 并把那条注释删掉（`Reference.equals` 比 key）                                                                                                                           |
| [x] | `Contractable#acceptsContractByTag`                   | `holder.value() == context.self().getType()`                                                                                                                                                             | `holder.is(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(context.self().getType()))`（或用 `getKey`）                                                                                        |
| [x] | `CultivatingEntityCondition#test`                     | 注释 "the stored holder and a freshly decoded one are not the same instance" + `HolderHelper.id(running).equals(asked)`                                                                                    | `spirit.cultivation().map(running -> running.is(this.action.get())).orElse(false)`，删 `asked` 与注释                                                                                      |
| [x] | `CultivationModeService#startNamed`                   | `HolderHelper.id(running).equals(HolderHelper.id(action))`                                                                                                                                               | `running.is(action)`                                                                                                                                                                  |
| [x] | `CultivationService#attempt`（私有重载）                    | `!HolderHelper.id(target.aura()).equals(HolderHelper.id(aura))`                                                                                                                                          | `!target.aura().is(aura)`                                                                                                                                                             |
| [x] | `CultivationService#next`                             | `Identifier cultivationId = HolderHelper.id(aura);` + `.filter(v -> HolderHelper.id(v.value().aura()).equals(cultivationId))`                                                                            | `.filter(v -> v.value().aura().is(aura))`，删 `cultivationId`                                                                                                                           |
| [x] | `CurseFilter#matches`                                 | `!HolderHelper.id(held).equals(HolderHelper.id(this.curse.get()))`                                                                                                                                       | `!this.curse.get().is(held)`                                                                                                                                                          |
| [x] | `HasTechniqueEntityCondition#holds`                   | 左分支 `learned.value() == entry.value()`，右分支却是 `learned.is(tag)`（一行两种判据）                                                                                                                                   | `learned.is(entry)`（或 `known.contains(entry)`）                                                                                                                                        |
| [x] | `HoldService#ours`                                    | `component.sound().value() == SILENT_SOUND.value() \|\| … == hold.holdSound(...).value()`                                                                                                                | **不要改成 `.is()`**：`SoundEvent.CODEC` 允许内联 ⇒ 可能是 `Holder.Direct`，内联实例每次解码都是新的，任何"相等"都命中不了。当前判据已由 `!hasConsumeParticles() && onConsumeEffects().isEmpty()` 承担，这两个音效比较是冗余保险 ⇒ 保留行为、把注释写清楚 |
| [x] | `InSecretRealmEntityCondition#matches`                | `value.map(holder -> HolderHelper.id(holder).equals(HolderHelper.id(candidate)), candidate::is)`                                                                                                         | `value.map(holder -> holder.is(candidate), candidate::is)`（两分支相同，或直接复用 `RegistryCodecs#matches`）                                                                                      |
| [x] | `ItemBindingService#declaration`                      | `.filter(binding -> HolderHelper.id(binding.technique()).equals(HolderHelper.id(technique)))`                                                                                                            | `.filter(binding -> binding.technique().equals(technique))`（**已改**，`equals` 与 `is` 等价）                                                                                                |
| [x] | `ItemBindingService#technique`                        | `.filter(binding -> HolderHelper.id(binding.technique()).equals(HolderHelper.id(taught)))`                                                                                                               | `.filter(binding -> binding.technique().equals(taught))`（**已改**）                                                                                                                        |
| [x] | `ProgressionAdminService#setLevel`                    | `HolderHelper.id(current).equals(HolderHelper.id(level))`                                                                                                                                                | `current.is(level)`                                                                                                                                                                   |
| [x] | `ProgressionDamageMultiplier#of`                      | `.anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability))`，参数是 `Identifier ability`                                                                                                              | 参数换 `Holder<Ability>` ⇒ `.anyMatch(unlocked -> unlocked.is(ability))`；上游 `AbilityService` 有 holder                                                                                    |
| [x] | `ProjectileDamageCondition#test`                      | `this.projectile.map(holder -> holder.value() == entity.getType())`                                                                                                                                      | `holder.is(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()))`                                                                                                             |
| [x] | `RegistryCodecs#matches`                              | `value.map(entry -> entry.value() == candidate.value(), candidate::is)` ← **样板，先改这个定调**                                                                                                                  | `value.map(holder -> holder.is(candidate), tag -> candidate.is(tag))`（**已改**）                                                                                                          |
| [x] | `RegistryCodecs#matchesValue`                         | `if (value.left().get().value() == candidate) return true;`                                                                                                                                              | 保留（这是"手里只有值"的入口），注释已重写说明它服务 `Holder.Direct` / 无 holder 的场合                                                                                                                             |
| [x] | `BlockAuraService#matches`                            | `candidate.value() == block`                                                                                                                                                                             | 已改走 `matchesValue`（值身份是它的本意）                                                                                                                                                           |
| [x] | `WheelSources#granted`                                | `HolderHelper.id(ability).equals(id)`                                                                                                                                                                    | `ability.is(id)`（`id` 是线缆入参，签名不动）                                                                                                                                                     |
| [x] | `CurseCommand#heldByName`                             | `HolderHelper.id(curse).equals(id)`                                                                                                                                                                      | `curse.is(id)`（`id` 是命令入参）                                                                                                                                                            |
| [x] | `MxtKubeJsApi#findCurseHolder`                        | `HolderHelper.id(curse).equals(id)`                                                                                                                                                                      | `curse.is(id)`（`id` 来自脚本）                                                                                                                                                             |
| [x] | `MxtKubeJsApi#hasElement`                             | `HolderHelper.id(element).equals(id)`                                                                                                                                                                    | `element.is(id)`                                                                                                                                                                      |
| [x] | `MxtKubeJsApi#hasTechnique`                           | `HolderHelper.id(technique).equals(id)`                                                                                                                                                                  | `technique.is(id)`                                                                                                                                                                    |
| [x] | `MxtKubeJsApi#foundSpiritRoot`                        | `HolderHelper.id(root).equals(id)`                                                                                                                                                                       | `root.is(id)`                                                                                                                                                                         |
| [x] | `MxtKubeJsApi#foundPhysique`                          | `HolderHelper.id(physique).equals(id)`                                                                                                                                                                   | `physique.is(id)`                                                                                                                                                                     |
| [x] | `AddResourceAction#execute`                           | `Identifier id = HolderHelper.id(this.resource);` 再调 `ResourceService.change(…, id, …)`（id 重载在无 access 时报 INVALID）                                                                                       | 改调 `Holder<Resource>` 重载，删 `HolderHelper` import                                                                                                                                      |
| [x] | `AbilityEventBridge#initializeResource`               | `HolderHelper.idOptional(resource).map(id -> ResourceService.initialize(holder, id, …)).orElse(false)`（holder 无 key 时整个调用连 false 都被吞）                                                                    | `ResourceService.initialize(holder, resource, ResourceService.formulaContext(entity, resource, …))`                                                                                   |
| [x] | `FlightControlAbilityType`（`archetype()` 过滤处）         | `skill.is(HolderHelper.id(context.ability()))`                                                                                                                               | `skill.is(context.ability())`（**本轮已改**，`Holder` 重载两边等价）                                                                                                                                |
| [x] | `AlchemyHeatSourceTooltipAppender#find`               | `BuiltInRegistries.BLOCK.getKey(block)` 转成 id 去调 id 重载                                                                                                                                                   | `RegistryCodecs.matchesValue(definition.blocks(), BuiltInRegistries.BLOCK, block)`（正确用法见 `SpiritHerbService#findBlock`）                                                               |
| [x] | `AuraLookup#holder(Provider, Holder<Resource>)`       | `return holder(access, HolderHelper.id(resource));`                                                                                                                                                      | 直接用 holder 过滤：`all(access).filter(aura -> aura.value().resource().is(resource)).findFirst()`；id 重载只留给真·id 调用方                                                                         |
| [x] | `AuraLookup#holder(Provider, Identifier)`             | `HolderHelper.id(aura.value().resource()).equals(resource)`                                                                                                                                              | `aura.value().resource().is(resource)`                                                                                                                                                |
| [x] | `AuraLookup#find(Provider, Holder<Resource>)`         | 转 id 再调 id 重载                                                                                                                                                                                            | 同上去掉往返                                                                                                                                                                                |
| [x] | `Elements#of(SpiritIdentityAttachment, Provider)`     | `flatMap(root -> MxtDatapackRegistries.get(access, SPIRIT_ROOT, HolderHelper.id(root)).stream())`                                                                                                        | `flatMap(root -> root.value().elementHolders().stream())`；同时改掉方法上那段"查表是为了滤掉移出数据包的根"的注释                                                                                                |
| [x] | `RealmCommand#chain`                                  | 手工建 `Map<Identifier, Reference<RealmStage>> stages`（全表遍历 + 每项降 id）只为判 `stages.containsKey(start)`                                                                                                        | 删 `stages` 与 `start`；`target` 来自 `ResourceArgument` 必然在册，真正的判据是紧随其后的 `chain.indexOf(target) < 0`                                                                                      |
| [x] | `SecretRealmService#enter`                            | 收 `Holder<SecretRealm>`，原本 `HolderHelper.id(definition)` 再 `holder(...).isEmpty()` 判 `DISABLED`                                                                                 | **本轮已把判据放回入口**：`definition.unwrapKey().flatMap(access::get).isEmpty()` ⇒ `Failure.DISABLED`（不再走 id 往返，见 ⚠2）                                                                               |
| [x] | `CreatureProfileService#select`                       | 每个 holder 降成 id 再查回来判类型                                                                                                                                                                                  | 返回 `Optional<Holder<CreatureProfile>>`，`.filter(holder -> matchesType(creature, holder.value()))`                                                                                     |
| [x] | `CreatureProfileService#applySelected`                | `Identifier id = select(...)` 后再 `get(..., id)` 成 value                                                                                                                                                  | 收 holder 往下传                                                                                                                                                                          |
| [x] | `CreatureProfileService#apply`                        | 三趟里的第三趟：再按 id 查成 holder 才写附件；且 `ifPresent` 空跑时仍 `return true`（成功是假的）                                                                                                                                     | 收 `Holder<CreatureProfile>` 直接写；把 `return true` 放进持有 holder 的分支，删掉这次查表                                                                                                                |
| [x] | `AlchemyFurnaceTooltipAppender#loadedWall`            | `id = HolderHelper.id(stored)` → `id.equals(EMPTY)` 判空 → `holder(..., id)` 查回 → `.map(h -> h)`                                                                                                           | `return Optional.of(stored);`（`stored == null` 已判过）                                                                                                                                   |
| [x] | `AlchemyFurnaceCasingBlockEntity#material`            | 与上一行逐字相同                                                                                                                                                                                                 | `return Optional.of(stored);`                                                                                                                                                         |
| [x] | `AlchemyWorkstationService#furnaceDefinition`         | 与上两行同形状（`ALCHEMY_FURNACE`）                                                                                                                                                                               | `return Optional.of(stored);`                                                                                                                                                         |
| [x] | `TechniqueCommand#resolves`                           | `HolderHelper.id(technique)` → `id.equals(HolderHelper.EMPTY)` 判空 → `get(..., id).isPresent()`；注释断言 "a removed entry has no value to read, and asking for one would throw"（不成立：移出数据包后旧 `Reference` 仍绑旧值） | 保留"查表"这一判据（它是唯一有效的），但删 `EMPTY` 判断、把入参改成 holder→按 key 查一次集中到一处；注释重写                                                                                                                    |
| [x] | `TechniqueCommand#resolvesLevel`                      | 同上（`PROGRESSION`）                                                                                                                                                                                        | 同上                                                                                                                                                                                    |
| [x] | `TechniqueCommand#prune`                              | `Set<Identifier> seen` 做去重 + `!resolves(...)`                                                                                                                                                            | 去重改 `Set<Holder<Technique>>`；`resolves` 按上面的口径保留                                                                                                                                      |
| [x] | `TechniqueCommand#pruneLevels`                        | `removed.contains(HolderHelper.id(entry.getKey()))`，`removed` 是 `List<Identifier>`                                                                                                                       | `removed` 改 `Set<Holder<...>>` ⇒ `removed.contains(entry.getKey())`                                                                                                                   |
| [x] | `DefinitionText#key(Holder)` / `#key(Holder, String)` | `unwrapKey().map(...).orElse("?")` 的兜底不可达                                                                                                                                                                | `unwrapKey().orElseThrow()`（已走私有 `keyOf(Holder)`），`"?"` 删掉                                                                                                                             |
| [x] | `ForgingBindingTooltipAppender#blueprintName`         | `blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?")`                                                                                                                              | 同上，`orElse("?")` 删掉                                                                                                                                                                   |
| [x] | `SecretRealmCommand#describe`                         | `record.definition().unwrapKey().map(...).orElse("?")`                                                                                                                                                   | 同上                                                                                                                                                                                    |
| [x] | `TechniqueCommand#diagnose`                           | `value.technique().unwrapKey().map(...).orElse("?")`                                                                                                                                                     | 同上                                                                                                                                                                                    |
| [x] | `SpiritBurstEntity#setAura`                           | 原：`aura.unwrapKey().map(ResourceKey::identifier).orElseThrow(() -> new IllegalArgumentException(...))`；异常不可达                                                                                               | **已按更小的改法落地**：保留 `Holder<Aura>` 形参，改 `HolderHelper.id(aura)`，并把"必须是注册表 holder（NBT 里只有 id）"写成注释。后续按 id 的查表是 NBT 字段，保留                                                       |
| [x] | `TalismanProjectileEntity#setTalisman`                | 同上（`"A thrown talisman must be a registry holder"`）                                                                                                                                                      | 同上（**已落地**）                                                                                                                                                                            |
| [x] | `SpiritBeastComponent#capture`（+ 记录字段）                | 手里有实体，却把 `Optional<Identifier> entityType` 存成 `getKey(beast.getType())`；释放时 `SpiritBeastBagItem` 再 `getOptional(...).orElse(null)`                                                                       | 字段改 `Optional<Holder<EntityType<?>>>`（`RegistryFixedCodec.create(Registries.ENTITY_TYPE)`），写入用 `wrapAsHolder`；序列化字节不变，释放路径不再有 `orElse(null)` 空洞（**已落地**）                                       |
| [x] | `ItemPickerManager#registerOrderedQualities`          | `Identifier id = HolderHelper.idOrNull(holder);`                                                                                                                                                         | 见批次 1（只删 null 一半）；现在是 `HolderHelper.id(holder)` + `!filter.test(id)`（**已落地**）                                                                                                           |
| [x] | `AuraService#getSensedAura`                           | `Identifier id` 往返链的中段                                                                                                                                                                                   | **已完成**：`Holder<AuraZone> zone = resolved.source(); if (zone == null) return resolved.copy(Map.of());`（见批次 4）                                                                           |
| [x] | `CultivationMethodService#ActiveCultivation#id()`     | `HolderHelper.id(this.aura.value().resource())`，而同记录 `resource()` 就返回那个 holder；调用点 4 处                                                                                              | **本轮已删 `id()`**，四处调用点改 `active.resource()`（`ResourceService` 的 `Holder<Resource>` 重载）                                                                                                    |

---

## 批次 3 — 派生机制清理（`UNBOUND` 与哨兵显示）

**`UNBOUND` 曾经同时服务两种来源**：(a) `holder.isBound()`（不存在）；(b) 一个**裸 id 查不到定义**。
**本轮结论**：`PillResolution` 的三种读法（`none` / `componentOnly` / `bound`）都从 holder 来，唯一的 `unbound(...)` 生产者就是 `resolvePill` 里那个改反的分支；而真实读取路径给不出"有 key 没 value"的引用（注册表查表给的是 bound 引用，不在册的 id 是**解码报错**，reload 删掉定义之后旧引用仍绑着旧值）。(b) 在这条链上并不存在 ⇒ **整条链已删干净**，包括唯一能造出那种 holder 的探针腿。

> **✅ 本轮已完成**：先修回改反的解析分支（⚠1），再删 `unbound` 链、3 份 lang 键与 `PillProbes` 的 `unbound_pill` 腿。

| 勾   | 类#方法                                                    | 现状                                                                                    | 改成                                                                                                                         |
|-----|---------------------------------------------------------|---------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| [x] | `ItemBindingService#resolvePill`                        | `if (source != null) return PillResolution.unbound(source);` —— **改反了**（见 ⚠1）              | **本轮已修**：恢复解析分支（`source == null` 才走 `none` / `componentOnly(component.applyTo(defaults))`，否则按 `component == null` 是否叠加、`identity == null` 是否记账，回到 `bound` / `componentOnly(Holder, Pill)`） |
| [x] | `ItemBindingService#PillResolution`（record）             | `boolean unbound` 字段 + `static PillResolution unbound(Holder<Pill>)`                  | **本轮已删**字段与工厂（`bound(...)` / `componentOnly(Holder,Pill)` 重新有调用方）；javadoc 里 `{@code unbound}` 那句也删了                                                     |
| [x] | `PillService#Failure`                                   | 枚举成员 `UNBOUND`（带 `//FIXME::`）                                                         | **本轮已删**；`check` 里那句 `if (resolution.unbound()) …` 与注释里"An explicit missing binding refuses"一句一起删                                                                                 |
| [x] | `QualityService#Failure`                                | 枚举成员 `UNBOUND` + `fromPill` 里的 `case UNBOUND ->`                                  | **本轮已删**两处；`check` 里那句 `if (bindings.pill().unbound()) …` 与上面两行注释一起删                                                                        |
| [x] | `ItemBindingTooltipAppender`（`resolution.unbound()` 分支） | `if (resolution.unbound()) builder.accept(translatable("tooltip.mxt.pill.unbound"))`  | **本轮已删**分支，只剩 `resolution.effects().ifPresent(...)`                                                                                                     |
| [x] | `AlchemyFailure`                                        | 枚举成员 `UNBOUND`，唯一生产者是 `AlchemyWorkstationService#mapQuality` 的 `case UNBOUND ->` | **本轮已删**枚举成员与那个 `case`（`mapQuality` 的 switch 现在只剩四个分支）                                                                                                        |
| [x] | `AuraService#Resolved#id()` + `AuraService.EMPTY` 常量    | `holder.map(HolderHelper::id).orElse(EMPTY)`，`EMPTY = mxt:empty`                      | **已完成**：`Resolved` 现在是 `(zone, kind, maxBonus)`，`id()` 与 `mxt:empty` 都没了；`EMPTY_ZONE` 是私有的"默认规则"兜底（不是 id），保留                                                                       |
| [x] | `HolderHelper#EMPTY` / `#idOrNull` / `#idOptional`      | 外部对 `HolderHelper.EMPTY` 的引用**已经清零**；常量本身仍留在类里做 `id()` 的兜底，`idOrNull` 只剩 8 处 UI 显示用法                      | **按"保留但收敛"落地**：`EMPTY`/`idOptional` 只服务 `Holder.Direct`，`idOrNull` 的 8 处都是 UI 层（`ItemPickerManager` ×6、`ResourceBarOverlay`、`Contractable`），没有再当"holder 坏了"的判据                                 |
| [x] | `Aura`#toString                                         | `this.firstRealm.map(HolderHelper::id).orElse(HolderHelper.EMPTY)`                    | **已完成**：`this.firstRealm.map(holder -> HolderHelper.id(holder).toString()).orElse("-")`                                   |

**删链时连带清掉的东西（本轮都做了）**：三份 lang 键 `tooltip.mxt.pill.unbound`（那个分支）、`actionbar.mxt.item.cannot_use.unbound`（`QualityService#notifyCannotUse` 按 `failure.name()` 拼键）、`screen.mxt.alchemy.failure.unbound`（随 `AlchemyFailure.UNBOUND`），zh/en 各删 3 个、键集合仍一致（**1177**）；`PillProbes` 的 `unbound_pill` 腿与 `unboundPill` 助手（`Holder.Reference.createStandAlone` 是唯一能造出"有 key 没 value"的写法）也删了，原地留了一条说明；`pill_color` 腿里引用它的那句注释改掉了。

**本轮按你的要求删掉的 6 条「不改」**（显示层 id）：`ArtifactDescription#describe`、`ForgingBindingTooltipAppender` 的 `advancedId` ×2、`ItemBindingTooltipAppender#appendGranted` ×2、`SpiritRootTooltipAppender#register`、`PhysiqueTooltipAppender#appendPhysique`、`ResourceCost#id`。理由：`HolderHelper.id` 的 `EMPTY` 兜底只对 `Holder.Direct` 可达，提示框/选择器里的 holder 都来自注册表，那两个冒号漏不进界面；真要"类型上不可能空"可以写 `holder.key().identifier()`，但那是打磨不是修问题。`ResourceCost#id` 另见批次 5（账户若改 holder 键，它可能整个消失）。

---

## 批次 4 — 结构性改动（收 holder、删中间层；动内部签名，不动序列化）

> **本轮复核**：**22 项已完成**，**12 项还在**，**3 项挪到批次 5**（见下），**8 条「不改」已删**。
>
> **2026-10-15 全部收完**：Aura 收尾（`staticZone` 的群系 memo 改成 `Holder<Biome>`、`AuraQueryCache#biome`/`#cacheBiome` 缓存 holder、`AuraCommand#sourceName` 直接用 `DefinitionText.text(zone)`）、Flight 三条（`FlightAttachment.vehicle` 改 `Ability.CODEC`、`FlightService` 传 `found.ability()`、`FlightEventBridge` 直接 filter holder 并删掉 `Abilities.resolve`）、Wheel 五条（`WheelSources#carriers`/`#carrier` 与 `WheelContent` 三处都收 holder）、`ArtifactService#abilities`（组件装 holder 后不再解析）、`ForgingService#strike` 与 `ForgingPlan`/`ForgingSession`/`ForgingTableState`（见批 5 说明）**全部落地**。**批 4 到此全清**，表里剩下的 `[ ]` 格子按上面这串逐条对应，不再逐格勾。
>
> 已完成（新增 4 条，其余同上一轮核对）：`AuraResult`（现在装的是可空 `Holder<AuraZone> source`，不是原建议的 `Optional<...> zone`——等价，`sourceKind` 保留）、`AuraService#getSensedAura`、`#holderOf`（已删）、`#findHolder`（已删）、`#applyBlockDistance`（已收 holder）、`AuraQueryCache#holder`（已删）、**`AuraQueryCache` 的 `HOLDER` 表 / `HOLDER_HITS` / `HOLDER_MISSES` / `Stats` `stats()` 全部已删**（上一轮报的"只读不写"死代码已经清掉了；只剩 `:49` 那句孤立注释，跟着下面 biome 那一条一起删）、**`ForgingBindingService#methods` / `#blueprints`（已是 `LinkedHashSet<Holder<…>>`）**、`AuraService#Resolved#id()`（已删）、`AuraZoneEventBridge#onEntityTick`、`AuraZoneRenderer`、`ResourceBarOverlay#collectAuraHud`；**2026-10-15 又收掉 `ArtifactDescription#appendStorage` 与 `ItemStorageService#get` / `#set`**（`DataStorageHolder` 的地址改成 `Holder<Ability>` 之后，它们直接收 holder，那层 id 往返没了）。
>
> 「不改」已删：`AuraCommand` 的 `source_kind` lang 处、`MxtKubeJsEventDispatcher#getSource`（脚本边界）、`SecretRealmRegistry#load` / `#aliases`（存档字段，判据唯一有效）、`IconReference#toString`、`ChequeTableMenu`、`RegistryCodecs#matchesKey`、`ForgingMenu#methodId`（菜单按 int 注册表 id 同步，那层 holder→id 就是载荷边界）。
>
> **挪到批次 5 的 3 条**：`ForgingPlan#of`、`ForgingPlan#deltaIfAllowed` / `#delta`，以及 `ForgingService#strike` 里"session 改成收 holder"的那一半 —— `ForgingPlan` **有 CODEC**（`finish_pattern` 是 id 列表、`deltas` 是 id 键 map，经 `ForgingTableState.CODEC` 落盘），`deltas` 换 holder 键就是**动序列化形状**，不能放在"不动序列化"的这一批。

| 勾   | 类#方法                                                                            | 现状                                                                                                                                                                       | 改成                                                                                                                                                                                   |
|-----|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [x] | `AuraResult`（record）                                                            | 只有 `Identifier source` + `SourceKind sourceKind`，holder 被丢掉；而同一 record 的 `aura` 字段是 holder 键的                                                                            | 加 `Optional<Holder<AuraZone>> zone`（**实际落地为可空 `Holder<AuraZone> source`**，`sourceKind` 作为"从哪来"保留）                                                                                        |
| [x] | `AuraService#getSensedAura`                                                     | `get(..., AURA_ZONE, resolved.source())` 查回 value，再 `pools(level, holderOf(level, zone), zone, …)` 扫回 holder                                                             | 直接用 `AuraResult.source()` ⇒ `zone == null` 时 `resolved.copy(Map.of())`                                                                                                                  |
| [x] | `AuraService#holderOf`                                                          | 整方法（value → holder）                                                                                                                                                      | 删                                                                                                                                                                                    |
| [x] | `AuraService#findHolder`                                                        | 整方法：`holders(...).filter(holder -> holder.value() == zone).findFirst()` 全表扫描                                                                                             | 删                                                                                                                                                                                    |
| [x] | `AuraService#applyBlockDistance`（调用 `holderOf` 处）                               | 同一趟往返                                                                                                                                                                    | 收 holder 参数                                                                                                                                                                          |
| [x] | `AuraQueryCache#holder`                                                         | 整方法（`HOLDER` 记忆化）                                                                                                                                                        | 删                                                                                                                                                                                    |
| [x] | `AuraQueryCache`（`HOLDER` 表、`HOLDER_HITS`/`HOLDER_MISSES`、`clear()` 里一行、诊断日志一行） | 全部只服务 `findHolder` 的扫描                                                                                                                                                   | **已全删**                                                                                                                                                                              |
| [x] | `AuraQueryCache#Stats` / `#stats()`                                             | `holderHits` / `holderMisses` 两个字段与传参                                                                                                                                    | **已删**                                                                                                                                                                               |
| [x] | `AuraZoneEventBridge#onEntityTick`                                              | `previous.source().equals(current.source())`；`zones.getOptional(aura.source())` 按 id 查区域                                                                                 | 已改成 holder 比较 + 载荷可选（客户端拿 holder）                                                                                                                                                    |
| [x] | `AuraZoneRenderer`（引用 `snapshot.source()` 处）                                    | 客户端按 id 查区域定义                                                                                                                                                            | 同上一行                                                                                                                                                                                 |
| [x] | `ResourceBarOverlay#collectAuraHud`                                             | `getOptional(snapshot.source())`                                                                                                                                         | 同上一行                                                                                                                                                                                 |
| [x] | `AuraService#Resolved#id()`                                                     | 见批次 3                                                                                                                                                                    | 已删（`Resolved(Holder<AuraZone> zone, SourceKind kind, Map<Holder<Aura>,Double> maxBonus)`）                                                                                             |
| [x] | `ForgingBindingService#methods`                                                 | `Map<Identifier, Holder<ForgingMethod>> unlocked` 做去重，输入是 holder 流                                                                                                       | **已是 `Set<Holder<ForgingMethod>> = new LinkedHashSet<>()`**                                                                                                                           |
| [x] | `ForgingBindingService#blueprints`                                              | 同上（`Holder<ForgingBlueprint>`）                                                                                                                                           | **已是 `Set<Holder<ForgingBlueprint>>`**                                                                                                                                                |
| [ ] | `AuraService#staticZone`                                                        | `memoised = HolderHelper.id(level.getBiome(pos))` → id 存进 `AuraQueryCache.BIOME` → `RegistryCodecs.matches(…, BIOME, biome)` 内部再 `registry.get(ResourceKey.create(...))` | 换成缓存/传 `Holder<Biome>`，用 holder 重载 `RegistryCodecs.matches(collection, biomeHolder)`；**顺手删掉 `AuraQueryCache:49` 那句"HOLDER 表"的孤立注释**（`//FIXME::` 已标在 `BIOME` 上）                              |
| [ ] | `AuraQueryCache#biome` / `#cacheBiome`                                          | 缓存 `Identifier`（群系）                                                                                                                                                      | 改缓存 `Holder<Biome>`（`Holder` 相等按 key，做键安全）。**与上一行是同一处改动，一起做**                                                                                                                         |
| [ ] | `AuraCommand#sourceName`                                                        | 靠 `sourceKind` 把一个 id 的**注册表归属猜回来**（`Identifier.fromNamespaceAndPath(MOD_ID, switch(kind){...})`）再喂 `DefinitionText.text(registry, id)`（`//FIXME::` 已标）                  | holder 可用时走 `DefinitionText.text(holder)`；只剩群系/维度时需要保留一个分支（那两种情况本来就没有 zone holder）                                                                                                   |
| [ ] | `FlightAttachment`（`vehicle` 字段）                                                | 同一种东西（`mxt:ability` 条目）存了两种形状：`archetype` 用 `Ability.CODEC`（holder），`vehicle` 用 `Identifier.CODEC`（`//FIXME::Optional<Holder<Ability>>` 已标）                                  | `vehicle` 改 `Ability.CODEC`（`Optional<Holder<Ability>>`）；两者一致（写出来的仍是 id 字符串，字节形状不变）                                                                                                    |
| [ ] | `FlightService`（调 `data.start(...)` 处）                                          | `data.start(skill, HolderHelper.id(found.ability()), …)`                                                                                                                 | 传 `found.ability()`。**与上下两条是同一处改动**                                                                                                                                                    |
| [ ] | `FlightEventBridge#onEntityTick`                                                | 每 tick `data.vehicle().flatMap(id -> Abilities.resolve(access, id))` 查回 holder                                                                                           | `data.vehicle().filter(mount -> mount.value().type() instanceof MountAbilityType)`；删 `Abilities.resolve` 与那两条 "a stored holder cannot notice its own definition leaving the pack" 注释 |
| [ ] | `ArtifactService#abilities`                                                     | 从堆上的 id 列表解析出 holder（`component.abilities().forEach(id -> Abilities.resolve(access, id).ifPresent(granted::add))`）                                                                | 上游改 `ItemAbilitiesComponent` 装 holder（见批次 5）后可直接读 holder                                                                                                                             |
| [x] | `ArtifactDescription#appendStorage`                                             | 签名已经收 `Holder<Ability> ability`，内部却 `ItemStorageService.get(stack, HolderHelper.id(ability), …)`                                                                         | **2026-10-15 已改**：直接传 `ability`                                                                                                                                       |
| [x] | `ItemStorageService#get` / `#set`                                               | 只收 `Identifier`                                                                                                                                                          | **2026-10-15 已改**：收 `Holder<Ability>`；`DataStorageHolder` 的地址也改成 holder（见批次 5）                                                                                  |
| [ ] | `ForgingService#strike`                                                         | `Identifier methodId = HolderHelper.id(method);` 后 `session.canStrike(methodId)` / `session.strike(methodId)`；`ForgingSession` 的 `history` 与 `finishPattern` 都是 id                     | 内部签名收 holder（`session` 的这两个方法），**id 只在 `ForgingSession` 的存档边界转一次**；`ForgingPlan.deltas` 那部分见批次 5                                                                                        |
| [ ] | `WheelSources#carriers`                                                         | `Map<Identifier, ItemStack> found` + `putIfAbsent(HolderHelper.id(ability), stack)`                                                                                          | `Map<Holder<Ability>, ItemStack>`                                                                                                                                                    |
| [ ] | `WheelSources#carrier`                                                          | `carriers(entity, source).get(abilityId)`，形参是 `Identifier abilityId`                                                                                                         | 随上一行收 `Holder<Ability>`                                                                                                                                                                |
| [ ] | `WheelContent#pool`                                                             | `carriers.get(HolderHelper.id(ability))`                                                                                                                                 | `carriers.get(ability)`                                                                                                                                                              |
| [ ] | `WheelContent#options`                                                          | 同上                                                                                                                                                                       | 同上                                                                                                                                                                                   |
| [ ] | `WheelContent#derived`                                                          | 同上                                                                                                                                                                       | 同上                                                                                                                                                                                   |

---

## 批次 5 — 动序列化形状（数据包 / 存档 / 网络；需文档同步）

**前提**：`CollectionCodecs.map(holderCodec, valueCodec)`（→ `TolerantMapCodec`）**已经能装 holder 键的 map**，用例现成：
`PillUsageAttachment#entries`、`CurseHolderAttachment#instances`/`#sources`、`ProgressionHolder#levels`。
所以这一批没有机制障碍，只有形状变更与文档成本。

**本轮复核**：`SpiritBeastComponent`、`AuraStateS2CPayload` **已完成**；「不改」条目已删（现在只剩 `PillUsageAttachment` —— 它是"正确的 holder 键形状样板" —— 与 `Abilities#resolve` —— 它是 id 边界函数）。

> **2026-10-15（二）又完成一批**：`AuraCostDraft` / `ResourceCostDraft` / `CostPayment` / `CostDraftManager` / `WheelTooltips#costs`（**资源账户改成 `Holder<Resource>` 键**，`ResourceTransactions.Evaluation` 一起改；`commit` 里那次查表整段删掉，`reset()` 的不确定性消失；`resources()` 保留 id 键但变成副本，新增 `account()` / `evaluation()` / `reprice(...)`——详见审计 §2.12 末尾的落地记录）、`ItemAbilitiesComponent` + `AddAbilityAction`、`ForgingResultComponent`、`ForgingTableState` / `ForgingSession` / `ForgingPlan`（锻造那三个 codec 形状：方法一律换成 `Holder<ForgingMethod>`，**代价是"移出即失史"**——`ForgingTableState` 现在把重建不出来的会话**丢掉并打一行日志**，而不是抛异常让方块实体载入失败）、`TriggerCooldownAttachment`。
>
> **2026-10-15（三）批 5 收尾**：炼金一组 7 条全部落地（`AlchemyMixture` / `AlchemyResolver` / `AlchemyWorkstationService#Parameters` + `#environment` 改 holder 键），连带测试包的两个探针（`AlchemyProbes` / `AlchemyGateProbes`）与 `ResourceCost#id()` 的删除（`Costs#validate` 的报错文案改走 `HolderHelper.id(...)`，`MxtTestCommands` 的 `cost.id().equals(QI)` 改成 `cost.resource().is(qiResource)`）。**JSON 形状不变**，所以文档站的丹方页无需改动。**批 5 到此全清。**

> **2026-10-15 已完成（原本判为「不改」）**：`AbilityAttachment` 与 `DataStorageHolder` 的 id 键按你的要求改成 `Holder<Ability>`。
> 做法：`SourceLedger.codec(Ability.CODEC)`（`SourceLedger` 本身已是泛型的，**值仍是来源 id**）、`DataStorageHolder` 的地址由 `Identifier` 换成 `Holder<Ability>`（`Entry` 的 JSON 键仍叫 `"id"`，**存档与线缆形状不变、老存档直接读**）、`AbilityStorage` 的九个方法收 holder；调用点（`AbilityService` / `AbilityEventBridge` / `AbilityGrantService` / `AbilityModifierService` / `PassiveAttributeService` / `AbilityCommand` / 战利品 / 动作 / 存储条件 / 轮盘 / KubeJS / 物品宿主 / 测试包）一并改完。
> **顺带解决的**：`AbilityEventBridge` 的 `tickAbilities` / `finishDueCasts` / `syncAbilitySubscriptions`、`WheelSources.abilities`、`AbilityModifierService.resolve` 里的 `Abilities.resolve(台账键)` 全部消失（`keys()` 直接就是 holder）。
> **代价（已写进类注释）**：定义被移出数据包时，账本里那一项与它名下的状态值在**解码时被丢掉**（各打一行日志），不再保留"查不到的 id"——这正是原「不改」理由的反面。
> **仍按 id 的**：`SourceLedger` 的**值**（`mxt:grant/...` 来源 id）、命令参数 / action 与 condition 的 JSON 字段、实体 NBT 里的字符串、`Abilities.resolve` 本身，以及 `WheelSources#carriers` 那张页内缓存（`Map<Identifier, ItemStack>`，仍是批 4 未做项）。
>
> **2026-10-15（三）再收一处**：`mxt:modify_ability_storage` 与六个 `mxt:ability_storage_*` 条件都改收 `Holder<Ability>`（`Ability.CODEC`），**JSON 键统一成 `ability`**；`AbilityStorageReading.of` 因此收 holder、不再 `Abilities.resolve`，那个只服务"id 查不到"的 `Optional` 也去掉。**属破坏性字段改名**：文档站（`data-storage` / `technical/ability` / `entity_action_types` 中英）与测试包 18 个夹具同步改完。**代价**：写/读一个当前包没有的技能 id 现在是**加载期失败**，不再是静默答 `false`（与 `grant_ability` / `has_ability` / `mxt:targeted` 一致）。改完 `Abilities.resolve` 全仓只剩 6 处，都在真正的 id 边界（KubeJS 入参 ×3、轮盘按键载荷 ×2、轮盘条目判定 ×1）。

> **一个必须留在这一批里的口径**（原附 A 的唯一硬结论）：`Cost` 管线的 `Map<Identifier, Double>` 是**资源账户的原生货币**——
> `CostPayment.resources()` 这个对外契约、`ResourceTransactions.Evaluation`、事件 `Post(resources)`、KubeJS 的 `Result`
> 全都按资源 **id** 记账。这一批改的是**账户本身**（草稿持有的那张表），**不是**这个对外契约；两者要在同一轮里一起定，
> 别只改草稿的表而让 `Evaluation` 还是 id 键。

| 勾   | 类#方法                                                             | 现状                                                                                                                     | 改成                                                                                                                    |
|-----|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| [ ] | `AuraCostDraft#load`                                             | `account.merge(HolderHelper.id(cost.aura().value().resource()), value, Double::sum)` ← 手里就是那个 `Holder<Resource>`       | 账户改 `Map<Holder<Resource>, Double>`（`Aura.resource` 直接给 holder），`ResourceCost#id()` 可能随之消失                              |
| [ ] | `ResourceCostDraft#commit`                                       | `for (Identifier id : account.keySet()) holder(access, RESOURCE, id).ifPresent(...)` 才记回退现场；查不到就**静默漏还原**，`reset()` 失效 | 账户改 holder 键 ⇒ 写入与回退天然同一批键，`reset()` 不再依赖"两次查表同样失败"                                                                   |
| [ ] | `ResourceCostDraft#reset`                                        | 依赖 `written` 的完整性                                                                                                      | 同上                                                                                                                    |
| [ ] | `CostPayment`（账户字段与传参）                                           | `Map<Identifier, Double> account`（`:29`、`:225-232`、`:279`、`resources()`）                                              | 改 `Map<Holder<Resource>, Double>`（**与上面那条对外契约的口径一起定**）                                                                  |
| [ ] | `CostDraftManager`（工厂传参与账户）                                      | `Map<Identifier, Double>`                                                                                              | 同上                                                                                                                    |
| [ ] | `WheelTooltips#costs`                                            | `payment.resources().forEach((id, amount) -> holder(registries, RESOURCE, id)…)`                                       | 账户改 holder 键后直接 `(resource, amount)`                                                                                  |
| [x] | `AlchemyResolver#numbers`                                        | `Map<Holder<MedicinalProperty>, NumberProvider>` → `Map<Identifier, Double>`                                           | **2026-10-15 已改**：保留 holder 键（工作形状），`HolderHelper` import 删掉了                                                                                                                         |
| [x] | `AlchemyResolver#nonNegativeMap`                                 | `Map<Holder<Aura>, NumberProvider>` → `Map<Identifier, Double>`                                                        | **同上**，改 `Map<Holder<Aura>, Double>`                                                                                                                    |
| [x] | `AlchemyResolver#add`                                            | `into.merge(HolderHelper.id(property), …)`                                                                             | **已改**：`into.merge(property, …)`                                                                                                                    |
| [x] | `AlchemyResolver#extra`                                          | `actual`/`required` 都是 id 键                                                                                            | **已改**：两个都换成 `Map<Holder<MedicinalProperty>, Double>`；`Evaluated` 的 `main`/`auxiliary` 与 `Gap.property` 一起换                                                                                                                    |
| [x] | `AlchemyResolver`（`Evaluated.minimumAura`）                       | `Map<Identifier, Double>`                                                                                              | **已改** `Map<Holder<Aura>, Double>`（`cover` 也随之改成泛型 `<K>`）                                                                                         |
| [x] | `AlchemyWorkstationService#environment`                          | `Parameters.minimumAura` 是 id 键 ⇒ 每格 `holder(level.registryAccess(), AURA, entry.getKey())` 查回（**热路径**）                | **已改**：直接 `aura.pool(entry.getKey())`，那条运行期查表整段删掉（`MxtDatapackRegistries` / `MxtResourceKeys` import 一起清）                                                     |
| [x] | `AlchemyWorkstationService#Parameters`（record + CODEC）           | `Map<Identifier, Double>` × 3，用 `Codec.unboundedMap(Identifier.CODEC, DOUBLE)`                                         | **已改** `CollectionCodecs.map(MedicinalProperty.CODEC / Aura.CODEC, Codec.DOUBLE)`；**JSON 形状不变**（holder 键与 id 键都编成"id 字符串当键的对象"），老存档直接读                                                |
| [ ] | `ItemAbilitiesComponent`（record + CODEC）                         | `List<Identifier> abilities`                                                                                           | 改 `List<Holder<Ability>>`（holder codec，JSON 形状不变：仍是 id 字符串数组）。**批 4 的 `ArtifactService#abilities` 等这条**                |
| [ ] | `AddAbilityAction#execute`                                       | `this.abilities.forEach(ability -> ids.add(HolderHelper.id(ability)))`                                                 | 组件改 holder 后直接收 holder ⇒ 删这一行                                                                                          |
| [ ] | `ForgingResultComponent`（record + CODEC）                         | `Identifier blueprint`，写入来自 `Holder<ForgingBlueprint>`；今天无读取方再查回                                                       | 视语义决定：要让"蓝图没了"可判 ⇒ `RegistryFixedCodec.create(MxtDatapackRegistries.FORGING_BLUEPRINT)`；否则保留但注明是有意的 id 载荷             |
| [ ] | `ForgingTableState` / `ForgingSession` / `ForgingPlan`（codec 形状） | `history` / `finishPattern` / `deltas` / `blueprint` 一律 id（`ForgingPlan.CODEC` 是 `unboundedMap(Identifier.CODEC, INT)`）    | **设计选择**：历史/计划里可能留着已被移出数据包的方法，id 能保留"已不存在的历史" ⇒ 可保留 id。若要统一，改 holder 键并接受"移出即失史"（批 4 挪来的 `ForgingPlan#of` / `#deltaIfAllowed` / `#delta` 就是这一步的一半） |
| [ ] | `TriggerCooldownAttachment`（字段 + `isOnCooldown` / `record` 等）    | `Object2LongMap<Identifier>`，唯一调用方 `TriggerRuleService#dispatch` 手里是 `Reference<TriggerRule>`，且无任何地方查回                 | 可选：改 `Object2LongMap<Holder<TriggerRule>>`。**代价**：规则定义被移出数据包时冷却记录的处理语义要重新想                                            |
| [x] | `SpiritBeastComponent`（记录字段 + CODEC）                             | `Optional<Identifier> entityType`                                                                                      | 已改 `Optional<Holder<EntityType<?>>>`（字节不变，见批次 2）                                                                         |
| [x] | `AuraStateS2CPayload`（record + CODEC + STREAM_CODEC）             | `Identifier source` 与 `Map<Holder<Aura>, AuraPool> actual` 并存（同一 payload 两种形状）                                         | 已改 `Optional<Holder<AuraZone>> source`（`AuraZone.CODEC.optionalFieldOf` + `ByteBufCodecs.optional(holderRegistry(...))`），客户端不再按 id 查区域 |

---

## 批次 6 — 身份比较（`==` / `!=`）与身份键的表

**为什么单列**：`Holder.equals` 是**特意**写成能吃 `DeferredHolder` 的（`Holder.java:288-298` 的 Neo 覆写：`equals` 里判 `h.kind() == REFERENCE && h.getKey() == this.key()`），而 `==` 只在同一个对象时成立。模组定义走 `RegistryFixedCodec`，两侧通常都是注册表发出的同一份 `Reference`，所以 `==` 今天多半能过——**但失败是静默的**（校验不再报错、缓存不再命中、分支不再进入）。

> **本轮复查：全部完成**（下表 20 条逐条对过代码）。
> - `DatapackCache#realmLines`：`(a, b) -> !a.is(b)`（这处原本是唯一"因身份比较而漏报数据包错误"的，已修）。
> - `Chain` / `ChainWalker`：`Chain#indexOf` 是 `Objects.equals`；`ChainWalker` 的 4 个身份集是 `LinkedHashSet`，`IdentityHashMap` 私有助手已删；`AGENTS.md` §4 里那句"walker 用 `IdentityHashMap` 比引用"已同步改成 `equals` 口径。
> - `AuraQueryCache`：`HOLDER` 表与 `POOLS` 的内层 `Map<Holder<AuraZone>, …>` 都换成了 `HashMap`（外层按 `ServerLevel` 的身份键保留，那是正确的）。
> - `ContractBehaviorService#select` / `ContractEventBridge`：`==` → `ContractBehaviors.X.equals(behavior)`。
> - `WheelSource` / `WheelEntryKind`：**改法有调整**——Java **不允许在接口里声明 `equals`/`hashCode`**（接口不能覆盖 `Object` 的方法），所以改成在两个接口上加 `default boolean is(对方的接口)`（按 `id()` 比，与 `Holder.is(...)` 同形），再把 8 处 `==` 改成 `.is(...)`。
> - `research/85`：核过全文，没有依赖身份比较的表述。
> - **仍未纳入任何批次的两处**（性质与 `WheelEntryKind` 相同，等你决定）：`DataStorageHolder` 的 `address.kind() != kept` / `== kind`（`DataStorageKind` 是代码侧的类实例、不是枚举）；`BlueprintBinding:30` / `ToolBinding:30` 用 `map(HolderHelper::id).distinct()` 去重，`Holder.equals` 已经能做同一件事。

| 勾 | 类#方法 | 现状 | 改成 |
|----|---------|------|------|
| [x] | `DatapackCache#validateRegistry`（AURA 的调用点） | `validateRegistry(access.lookupOrThrow(AURA), Aura::resource, (a, b) -> a != b)`，判据是 `if (!predicate.test(...)) LOGGER.error(...)` ⇒ 两份档案声明同一个 resource 时因"不是同一实例"返回 `true`，**数据包错误静默漏报** | `(a, b) -> !a.is(b)`（**已修**） |
| [x] | `Chain#indexOf` | `this.nodes.get(index) == node`（注释写 "Compared by id"，与实现不符） | `Objects.equals(this.nodes.get(index), node)` + 改注释 |
| [x] | `ChainWalker`（`newIdentitySet()`） | `visited` = `Collections.newSetFromMap(new IdentityHashMap<>())`；`AGENTS.md` §4 把这句当既定口径写下来了 | `LinkedHashSet`（**已改**，`AGENTS.md` 那句也改了） |
| [x] | `AuraQueryCache#cachePools` | `zones.computeIfAbsent(location, ignored -> new IdentityHashMap<>()).put(zone, pools)` —— 键是 `Holder<AuraZone>` | `new HashMap<>()`（**已改**） |
| [x] | `AuraQueryCache#pools` | `byZone.get(zone)` 走上面那张身份表 | 随上一行一起生效 |
| [x] | `AuraQueryCache`（`HOLDER` 字段） | `IdentityHashMap` 的键是 `AuraZone` **record** | 该表已随批 4 整体删除 |
| [x] | `ContractBehaviorService`（`behavior == ContractBehaviors.RECALL`） | `ContractBehavior` **自带按 id 的 `equals`**，调用点却用 `==` | `behavior.equals(...)`（**已改**） |
| [x] | `ContractEventBridge`（`behavior == ContractBehaviors.FOLLOW`） | 同上 | 同上 |
| [x] | `WheelSource`（接口本身） | 有 `Identifier id()`，**没有** `equals`/`hashCode` | 加 `default boolean is(WheelSource)`（接口不能声明 `equals`） |
| [x] | `WheelEntryKind`（接口本身） | 同上 | 同上 |
| [x] | `WheelMenuContent`（`page.source() != source`） | 用 `==` 比 `WheelSource` | `.is(...)` |
| [x] | `WheelSelectionState`（`pages.get(index).source() == source`） | 同上 | 同上 |
| [x] | `WheelContent#derived`（`source == WheelSourceTypes.CONTRACT`） | 同上 | 同上 |
| [x] | `WheelSources#granted`（`kind == WheelEntryKinds.ABILITY`） | 用 `==` 比 `WheelEntryKind` | 同上 |
| [x] | `WheelSourceTypes`（`kind == WheelEntryKinds.BEHAVIOR`） | 同上 | 同上 |
| [x] | `WheelConfigurationScreen`（`slot.kind() == WheelEntryKinds.AURA`） | 同上 | 同上 |
| [x] | `PillUsageAttachment#record` | `key != pill && key.is(id)` 混用 | 见批 1（整段删） |
| [x] | `AGENTS.md` §4（"walker 用 `IdentityHashMap` 比引用"） | 文档把身份比较写成既定口径 | 随 `ChainWalker` 一起改 |
| [x] | `research/85_进度记录按所有者分类设计.md`（若描述到身份比较） | 核对是否依赖身份语义 | 已核对，无依赖 |

---

## 附 A — **已删除**

原来那张"确认不改"表整表删掉了（理由与逐条论证在 [`Holder使用审计.md`](Holder使用审计.md) §6 "NOT-a-finding"）。**只有一句必须留在这里**：`Cost` 管线的 `Map<Identifier, Double>` 是资源账户的原生货币，批次 5 改的是"账户本身"，`CostPayment.resources()` 这个对外契约要在同一轮里一起定。

---

## 附 B — 按类名速查

| 类                                                                   | 出现在             |
|---------------------------------------------------------------------|-----------------|
| `Abilities`                                                         | 批 1             |
| `AbilityEventBridge`                                                | 批 2             |
| `AbilityService`                                                    | 批 2             |
| `AddAbilityAction`                                                  | 批 5             |
| `AddResourceAction`                                                 | 批 2             |
| `AuraCostDraft`                                                     | 批 5             |
| `AlchemyFailure`                                                    | 批 3             |
| `AlchemyFurnaceCasingBlockEntity`                                   | 批 1、批 2         |
| `AlchemyFurnaceTooltipAppender`                                     | 批 1、批 2         |
| `AlchemyHeatSourceTooltipAppender`                                  | 批 2             |
| `AlchemyResolver`                                                   | 批 5             |
| `AlchemyWorkstationService`                                         | 批 1、批 2、批 5     |
| `AuraCommand`                                                       | 批 4             |
| `AuraLookup`                                                        | 批 2             |
| `AuraQueryCache`                                                    | 批 4、批 6        |
| `AuraResult`                                                        | 批 4             |
| `AuraService`                                                       | 批 1、批 4         |
| `AuraZoneEventBridge` / `AuraZoneRenderer`                          | 批 4             |
| `ArtifactDescription`                                               | 批 3、批 4、批 5     |
| `ArtifactService`                                                   | 批 2、批 4         |
| `ArtifactStorageContainer`                                          | 批 2             |
| `BlockAuraService`                                                  | 批 2             |
| `BoundBeastsAttachment`                                             | 批 2             |
| `Chain` / `ChainCache`                                              | 批 1、批 6        |
| `Contractable`                                                      | 批 2             |
| `Costs`                                                             | 批 1             |
| `CostPayment` / `CostDraftManager`                                  | 批 5             |
| `CreatureProfileService`                                            | 批 2             |
| `CultivatingEntityCondition`                                        | 批 2             |
| `CultivationMethodService`                                          | 批 1、批 2         |
| `CultivationModeService`                                            | 批 2             |
| `CultivationService`                                                | 批 2             |
| `CurseCommand` / `CurseFilter`                                      | 批 2             |
| `DefinitionText`                                                    | 批 2             |
| `Elements`                                                          | 批 2             |
| `FlightAttachment` / `FlightEventBridge` / `FlightService`          | 批 4             |
| `FlightControlAbilityType`                                          | 批 2             |
| `ForgingBindingService`                                             | 批 4             |
| `ForgingPlan` / `ForgingService` / `ForgingTableState` / `ForgingSession` | 批 4、批 5   |
| `ForgingBindingTooltipAppender`                                     | 批 2             |
| `ForgingResultComponent`                                            | 批 5             |
| `HasTechniqueEntityCondition`                                       | 批 2             |
| `HoldService`                                                       | 批 2（**特殊，见说明**） |
| `HolderHelper`                                                      | 批 3             |
| `InSecretRealmEntityCondition`                                      | 批 2             |
| `ItemAbilitiesComponent`                                            | 批 5             |
| `ItemBindingService`                                                | 批 1、批 2、批 3     |
| `ItemBindingTooltipAppender`                                        | 批 1、批 3         |
| `ItemPickerManager`                                                 | 批 1、批 2         |
| `ItemStorageService`                                                | 批 4             |
| `MxtKubeJsApi`                                                      | 批 2             |
| `PillItem`                                                          | 批 1             |
| `PillService`                                                       | 批 1、批 3         |
| `PillUsageAttachment`                                               | 批 1             |
| `ProgressionAdminService` / `ProgressionDamageMultiplier`           | 批 2             |
| `ProjectileDamageCondition`                                         | 批 2             |
| `QualityService`                                                    | 批 1、批 3         |
| `RealmCommand`                                                      | 批 2             |
| `RegistryCodecs`                                                    | 批 2             |
| `ResourceCost` / `ResourceCostDraft`                                | 批 3、批 5         |
| `ResourceBarOverlay`                                                | 批 4             |
| `SecretRealmCommand` / `SecretRealmRegistry` / `SecretRealmService` | 批 2、批 4         |
| `SpiritBeastComponent` / `SpiritBeastBagItem`                       | 批 2、批 5         |
| `SpiritBurstEntity`                                                 | 批 2             |
| `SpiritHerb`                                                        | 批 1             |
| `SpiritHerbService`                                                 | 批 1             |
| `TalismanProjectileEntity`                                          | 批 2             |
| `TechniqueCommand`                                                  | 批 2             |
| `TechniqueTintSource`                                               | 批 1             |
| `TriggerCooldownAttachment`                                         | 批 5             |
| `WheelContent` / `WheelSources` / `WheelTooltips`                   | 批 2、批 4、批 5、批 6 |
| `WheelEntryKind` / `WheelSource`                                   | 批 6             |
| `WheelMenuContent` / `WheelSelectionState` / `WheelSourceTypes`     | 批 6             |
| `WheelConfigurationScreen`                                          | 批 6             |
| `ChainWalker`                                                       | 批 6             |
| `DatapackCache`                                                     | 批 6             |
| `ContractBehaviorService` / `ContractEventBridge`                   | 批 6             |
| `AGENTS.md` §4（"walker 用 `IdentityHashMap` 比引用"）                  | 批 6（文档，随 `ChainWalker` 一起改） |

---

## 剩下的动手顺序（批 1 / 2 / 3 / 4 / 5 / 6 全部完成）

**六个批次到此全清**，清单里没有待办项了。收尾时记得：

- 已按 `AGENTS.md` §2 跑 `./gradlew.bat compileJava compileTestModJava --console=plain`（BUILD SUCCESSFUL）。本轮没改 lang，两份键集合仍一致（1177）。
- **实机验证仍未做**：按 §1.4 需先问。锻造那一处新增的"加载时归还搁浅材料"（`ForgingWorkstationService#returnStrandedMaterials` + `ForgingTableBlockEntity#setLevel`）只有实机放一次桌子、装一批材料、开一炉、再删掉那张蓝图冻结的 `forging_method` 并重载区块才看得到；炼金一侧建议顺带跑一次 `MxtTestCommands` 的炼丹探针（`AlchemyProbes` / `AlchemyGateProbes`），它们覆盖了本轮改动的每一个 holder 键读取点。
- **建议实机跑一次丹药探针**（`runTestServer`，需先问你）：⚠1 那处改反的分支是编译看不见的，`PillProbes` 现在删掉了 `unbound_pill` 腿，其余丹药腿（`component_only` / `overlay_keeps_limits` / `cooldown_max_uses` / `persistent_usage` / `pill_color`）正好覆盖"解析分支回来了"这件事。
