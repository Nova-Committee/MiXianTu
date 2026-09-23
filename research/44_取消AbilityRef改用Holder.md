# 取消 `AbilityRef`：能力一律以 `Holder<Ability>` 传递

> **状态：已落地（2026-09-23，同日第四次变更）。** 触发原话：「AbilityRef可以去掉了，Holder完全可以胜任」。本文记录这次收缩：删掉 `data/ability/AbilityRef.java`，解析层 `AbilityRefs` 改名 `Abilities` 并直接返回 holder。
>
> **基准**：`43_技能冷却改用原版物品渲染.md` 落地后的工作树——`data/ability/{AbilityRef,AbilityRefs,Ability,ToggleContext}.java`、`runtime/ability/**`、`runtime/{artifact,wheel,talisman}/**`、`event/Ability*Event.java`、`screen/wheel/content/**`、`command/AbilityCommand.java`、`compat/kubejs/**`、`network/ServerNetworkHandler.java`、`src/test-mod/**`。MC 26.1.2 / NeoForge 26.1.2.99。

## 0. 一句话

能力**不再有自己的包装类型**：`Holder<Ability>` 就是身份——`holder.value()` 是定义，`HolderHelper.id(holder)` 是注册表 id；`AbilityRefs` 改名 `Abilities`，两个 `resolve` 直接回 `Optional<Holder<Ability>>` / `List<Holder<Ability>>`。

## 1. 为什么（`Holder` 到底"胜任"在哪）

`AbilityRef` 是 `record AbilityRef(Identifier id, Ability definition)`，也就是把 `Holder` 已有的两半抄了一份：

| 要什么 | `AbilityRef` | `Holder<Ability>` |
| --- | --- | --- |
| 定义 | `ref.definition()` | `holder.value()` |
| 注册表 id | `ref.id()`（构造时 `HolderHelper.id(holder)` 取一次） | `HolderHelper.id(holder)`（全仓取 id 的唯一入口，已有 171 处调用） |
| 它是不是"某个注册表条目" | 看不出来，只是个 record | 是 `Holder.Reference`，`unwrapKey()` 直接给出 `ResourceKey` |

合并（`41`）与取消内联（`42`）之后，**能力的身份永远就是它自己那条 `mxt:ability` 注册表条目的 id**，不再有"派生 id"这种第二来源。于是包装层只剩两个代价：每个 API 边界都要写一次 `AbilityRef` 这个名字与一层解包，以及**同一份能力可以存在两个不来自同一个 holder 的 `AbilityRef` 实例**（record 的结构相等让它"碰巧对"，但比较的是抄下来的 id 与定义对象，而不是"是不是同一个注册表条目"）。

## 2. 落地改动（按文件）

- **删除** `data/ability/AbilityRef.java`。
- **`data/ability/AbilityRefs.java` → `Abilities.java`**：`resolve(Provider, Identifier)` → `Optional<Holder<Ability>>`（保留 `null` 守卫：命令与 KubeJS 传来的 id 可能为 null），`resolve(Provider, List<Either<Holder<Ability>, TagKey<Ability>>>)` → `List<Holder<Ability>>`（内部就是 `RegistryCodecs.resolve(...)`）。
- **签名**（约 20 个文件）：`AbilityService`（`prepare`/`use`/`useCarried`/`finishCast`/`gate`/`cooldownOf`/`tickChannel`/`cancelCast`/`useComposite`/`withAbilityScaling` + `PreparedUse` / `CompositeStep` 两个私有 record）、`AbilityActivationService`、`AbilityEventBridge`、`AbilityModifierService`、`ToggleContext`、`AbilityUseEvent` / `AbilityTriggeredEvent`（含 `ability()` 的返回类型）、`FlightService` / `FlightEventBridge`、`ArtifactService`（`abilities()` / `abilityIds()` / `storageSlots()` / `Upkeep`）、`ArtifactDescription`、`ArtifactUpkeepService`、`TalismanService`、`WheelSources` / `WheelService` / `WheelEntryKind`、`WheelContent` / `AbilityWheelEntry`、`StorageAbilityType` / `FlightAbilityType`、`AbilityCommand`、`MxtKubeJsApi` / `MxtKubeJsEventDispatcher`、`ServerNetworkHandler`、`loot/**`（`HasAbilityLootCondition` / `GrantAbilityLootFunction` 等原本就按 id 走账本，未改）。
- **取 id 变成显式**：`HolderHelper.id(holder)`（`AbilityService` 里 20 余处、`WheelSources`、`FlightService`、`WheelService`、`ServerNetworkHandler`、`StorageAbilityType`、`FlightAbilityType`、`ArtifactService#abilityIds` 的 `.map(HolderHelper::id)`）。
- **`ArtifactService#abilities`** 返回 `List<Holder<Ability>>`；`Upkeep(ability, type)` 的第一个字段变成 holder；`ArtifactUpkeepService` 读 `upkeep.ability().value().costs()`。
- **`CompositeStep` 丢掉了重复的 `definition` 字段**：它本来就持有 holder，`step.ability().value()` 就是那条定义（否则就等于把 §1 说的"抄一份"又在私有 record 里重演一次）。
- 测试包探针（`MxtTestCommands`）：`AbilityRef.of(...)` 的四处改成直接传 holder，`List<AbilityRef>` / `AbilityRef x = Abilities.resolve(...)` 全部改成 holder；**不新增腿**（这是纯类型收缩，行为不变，没有新的可断言事实）。

## 3. 没有变的东西

- **存档、网络、数据包格式一点没动**：`AbilityRef` 从不落盘、从不上网络——`AbilityAttachment.cooldowns` / `channelled_ability` / 各 `DataStorage` 与 `WheelSlot` 一直只存 `Identifier`。所以这次改动没有迁移、没有破坏性影响。
- **KubeJS 事件里的 `getAbility()` 仍然返回 id 字符串**（`HolderHelper.id(event.ability()).toString()`），脚本侧无感。
- **`HolderHelper.id(...)` 对无 key 的 holder 返回 `HolderHelper.EMPTY`（`"" : ""`）**——与 `AbilityRef.of(...)` 当时的实现逐字一致，所以"永远解析不出 id"这种情况的行为没有变化。

## 4. 验证

- `./gradlew.bat compileJava compileTestModJava --console=plain` → **BUILD SUCCESSFUL**。
- `src/` 内 grep `AbilityRef` → **0**；未使用 import 扫描 → **0 条**。
- 两份 lang 键集合一致（**740**，本次未改 lang）。
- 文档三处与文档站中英已同步（`AGENTS.md` §4、`docs/guide/java/wheel.md` 的接口注释、`docs/模块实现审计.md` 四处当日记录追加第三次变更标注、文档站 `java/interfaces.md` 中英各一处）。
- **未实机**：这是纯类型收缩，没有任何运行期行为变化；真要跑，`/mxt_test artifact` / `artifacts` / `wheel` / `verify` 应打印与改动前逐字相同的 `OK`。

## 5. 开放项

1. **`Abilities` 这一层要不要也去掉**：它的两个方法都是纯委托（`MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id)` 与 `RegistryCodecs.resolve(values, access, MxtResourceKeys.ABILITY)`），留下来的唯一理由是 `null` id 守卫与"id / `#tag` 两半同一条路径"。若把 null 容忍下沉到 `MxtDatapackRegistries.holder(...)`（现在传 null 会抛），这一层就可以整体删除、各调用点直接写注册表入口。属于"注册 vs 删除"的选择，留给用户拍板。
2. 探针没有新增腿：本次没有新的可断言行为（若要断言"取 id 的路径唯一"，那属于静态检查，不是探针的活）。
3. `research/41` 里 §2.2 的 `AbilityRef` 形状（含 `inline`/派生 id 的推演）现在是纯历史层，读的时候对照 `42` 与本稿。
