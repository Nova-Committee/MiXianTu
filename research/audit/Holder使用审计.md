# Holder 使用审计（26.1.2）

**日期**：2026-10-11
**范围**：`src/main/java` 全量（1237 个 `.java`，其中 `HolderHelper.id` 出现 200 次，分布在 33 个目录）；变量集中在 `runtime/`（221 文件）、`data/`（512 文件）、`attachment/`（26 文件）、`util/`（56 文件）。
**性质**：只读审计，本轮**未改任何代码**。
**触发**：用户给出前提——26.1.2 里 `Holder` 与 value / key 一一对应，不会出现两个 holder，也不会出现 holder 不在注册表，`Holder` 可以直接用 `equals` 比较。

---

## 0. 结论速览

用户给的前提**成立**，而且比"约定"更强：它是 26.1.2 原版源码的机械事实（§1 逐条核过）。据此，下面两类代码全都是**多余的**：

| # | 类别 | 判据 | 命中处数量 | 严重度 |
|---|------|------|-----------|--------|
| 1 | **`Holder` → `Identifier` → 再拿回 `Holder`** | 手里本来就有 holder，却先降成 id 再查回注册表 | 约 26 处（含 4 处"从 value 反查 holder"的整表扫描） | **HIGH** |
| 2 | **检查 holder 在不在注册表 / `isBound()`** | 从注册表拿到的 holder 必在册且必已绑定 | 约 18 处，外加 4 个派生出来的 `UNBOUND` 失败分支 | **HIGH** |
| 3 | **由 id 往返导致回退/成功判定失真** | 见 §2.12、§2.13 | 2 处 | **HIGH（有正确性风险）** |
| 4 | 比较时比 id 或比 `value()` 而不是比 holder | `Holder.equals` / `Holder.is` 就是为此存在 | 5 处 `id(a).equals(id(b))`、11 处 `id(a).equals(idVar)`、8 处 `value() == value()` | MEDIUM |
| 5 | **身份比较（`==` / `!=`）与身份键的表** | `Holder.equals` 特意兼容 `DeferredHolder`，`==` 不兼容；失败是静默的 | 5 处 holder `==`/`!=`、3 处 `IdentityHashMap` 身份键、2 个"身份就是 id"却被 `==` 比的类型 | MEDIUM |
| 6 | 用 `Identifier` 当 Map / Set 的键 | 同 id 跨注册表会撞、且丢掉类型 | 约 14 组 | MEDIUM |
| 7 | 哨兵 id（`HolderHelper.EMPTY` / `AuraService.EMPTY`） | 全仓没有路径能产生前者；后者是合法 id 且被喂给真查表 | 4 + 1 处判据、8 处显示 | MEDIUM |
| 8 | `.map(x -> x)` 空转、`unwrapKey().orElse("?")` 兜底 | 纯噪音 / 不可达 | 9 + 4 处 | LOW |

**最值得先动的四处**：

1. **`AuraService` / `AuraResult` / `AuraQueryCache`** —— 一整套 `holder → id → value → 全表扫描 → holder` 的三跳往返，外加一层专用记忆化缓存、一支诊断计数器、一份 `Stats` 字段。修掉它能删掉约 40 行真代码与 2 个公共出口（§2.1、§2.15）。
2. **`AuraCostDraft` / `ResourceCostDraft` 的账户键** —— 唯一一处有**正确性**风险：回退现场按"还能查回来的 id"记录，查不到就静默漏还原（§2.12）。**【2026-10-15 已修】**账户改成 `Map<Holder<Resource>, Double>`（`CostPayment.account()` / `account()` / `evaluation()` / `reprice(id 表)`），`ResourceTransactions.Evaluation` 与两张草稿一起改，见 §2.12 末尾。
3. **`PillUsageAttachment.dose`** —— 注释写着"Holder identity is not stable across a reload"，而这个理由是错的（§1.5 已证伪），于是有一个 O(n) 回退扫描在保护一个本来就不会 miss 的 `HashMap.get`（§3.3）。
4. **`QualityService.intrinsic`** —— 两个模式挤在 8 行里，可以塌成一行 `Optional.of(direct)`（§2.3）。

另有一处**逻辑上的裂缝**值得单列：`CreatureProfileService.apply` 在查不到 holder 时什么都没写却 `return true`（§2.13）。

还有一处**最该先改的一行**：`DatapackCache#validateRegistry` 的 AURA 调用点传了 `(a, b) -> a != b` 当"这两个不是同一样东西"的判据（`a`/`b` 是 `Holder<Resource>`）。身份比较让它在一份数据包**真的**让两份灵气档案声明同一个 resource 时也返回 `true`（当成没问题），于是**数据包错误静默漏报**（§4.5a）。

---

## 1. 语义基线：先把前提核准

这一节是后面所有判定的依据，全部对着 `build/moddev/artifacts/minecraft-patched-26.1.2.99-sources.jar` 里的源码核过。

### 1.1 `Holder` 只有两种实现

`net/minecraft/core/Holder.java`：

- `Holder.Direct<T>`（:58）—— `isBound()` 恒 `true`（:60）；`unwrapKey()` 恒 `Optional.empty()`（:100）；`is(Holder)` 走 `this.value.equals(holder.value())`（:85），`is(Identifier)` / `is(ResourceKey)` / `is(TagKey)` **一律 `false`**（:70/:75/:80）。
- `Holder.Reference<T>`（:130）—— `unwrapKey()` 恒 `Optional.of(this.key)`（:215）；`isBound()` = `key != null && value != null`（:225）；`is(Holder)` = `holder.is(this.key())`（:195）；`is(ResourceKey)` 是 `this.key() == key`（:178）**引用比较**。

### 1.2 `Holder.Reference.equals` 就是按 key 比

```java
// Holder.java:290-298（Neo 补的覆写）
public int hashCode() { return key().hashCode(); }
public boolean equals(Object obj) {
    return obj instanceof Holder<?> h && h.kind() == Kind.REFERENCE && h.getKey() == this.key();
}
```

所以 **两个不同实例、同一个注册表条目的 `Reference` 是 `equals` 的**。这一条是"不要降成 id 再比"的全部理由。

### 1.3 `ResourceKey` 是 interning 的 —— 于是 holder 相等跨 reload 也成立

```java
// ResourceKey.java:13
private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES =
        new MapMaker().weakValues().makeMap();
// ResourceKey.java:33-34
private static <T> ResourceKey<T> create(Identifier registryName, Identifier identifier) {
    return (ResourceKey<T>) VALUES.computeIfAbsent(new ResourceKey.InternKey(registryName, identifier),
            k -> new ResourceKey(k.registry, k.identifier));
}
```

同一对 `(注册表, id)` 只会有一个 `ResourceKey` 实例。于是 `Reference.equals` 里那句 `h.getKey() == this.key()` **跨 `/reload` 也成立**：只要还有一个活着的 holder 引用着那个 key，weak 引用就不会被回收，`create` 就返回同一个实例。

> **结论**：`Map<Holder<T>, V>` 用 `get` / `containsKey` 在 reload 前后是安全的，不需要任何"按 id 回退"的补丁。这是 §3.3 那个 O(n) 扫描被否掉的直接依据。

### 1.4 从注册表 / 从 codec 拿到的 holder 一定在册且已绑定

```java
// MappedRegistry.java:114-121 —— byKey 只在 bindValue 之后才放入
holder = this.byKey.computeIfAbsent(key, k -> Holder.Reference.createStandAlone(this, (ResourceKey<T>)k));
holder.bindValue(value);
this.byKey.put(key, holder);
// MappedRegistry.java:168-170 —— 查表返回的就是那个已绑定的
public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
    return Optional.ofNullable(this.byKey.get(resolve(id)));
}
```

`RegistryFileCodec.decode`（`RegistryFileCodec.java:50-70`）：

- 走 id 分支时，`lookup.get(elementKey)` 拿不到就直接报错 `"Failed to get element " + elementKey`（:65-67）——**解不出一个"不在册"的 `Reference`**，它只会解码失败。
- 走内联分支时才产生 `Holder::direct`（:61），而这条分支只在 `allowInline == true` 时存在（:59-61），且 `create(key, codec)` 两参重载默认是 `true`（:19-21）。

**模组自己的 41 张动态注册表全部用 `RegistryFixedCodec` / `RegistryCodecs.holderOrTag`（后者内部是 `RegistryFixedCodec.create`）**，所以模组定义的 holder 一律是 `Reference`、一律在册、一律已绑定。逐个核过：

```
Ability / Aura / AuraZone / Tribulation / SpiritHerb / Artifact / AlchemyFurnaceDefinition /
AlchemyWallMaterial / MedicinalProperty / ItemQuality / …  → RegistryFixedCodec.create(...)
ContractAttachment / CreatureSpiritAttachment / SecretRealmTravelAttachment / TechniqueAttachment → RegistryFixedCodec
RegistryCodecs.holderOrTag(key) → Codec.either(RegistryFixedCodec.create(key), TagKey.hashedCodec(key))
```

**唯一会碰到 `Holder.Direct` 的地方是 `SoundEvent.CODEC`**（`SoundEvent.java:23` 用两参 `RegistryFileCodec.create`，允许内联）：

```
HoldBinding.java:25   DEFAULT_HOLD_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BOOK_PAGE_TURN)
HoldService.java:39   SILENT_SOUND      = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY)
TechniqueBinding.java:44  SoundEvent.CODEC.optionalFieldOf("hold_sound", HoldBinding.DEFAULT_HOLD_SOUND)
```

这几处的 holder **没有**被 `HolderHelper.id(...)` 碰过（`HoldService.ours` 走的是 `.value() ==`，见 §4.3），所以**目前没有任何 `HolderHelper.id` 调用点真的会收到 `Direct`**。

### 1.5 `isBound()` 唯一可能为 `false` 的窗口

`Reference.isBound()` 是 `key != null && value != null`（:225-227），所以它只可能在一个 **`Reference` 建出来了但还没绑值**的时候为假。这只有一个窗口：**数据包并行加载期**。模组自己的代码已经把这件事写清楚了：

- `SpiritHerb.java:75-76`：`// Harvest identity is not checked here. Item components are still unbound while a datapack registry decodes, so building a dropped stack throws.`
- `SecretRealmGeneration.java:12`：`// parallel, so a holder read at decode time may still be unbound.`
- `HoldLookup.java:26`：`// … reads unbound component maps`

于是判定分成两类，别一刀切：

| 位置 | 是否可能为假 | 判定 |
|------|-------------|------|
| **解码期**（codec 的 `validate` / `xmap` 里） | 可能 | 合法，保留（但要像上面三处那样把理由写出来） |
| **运行期**（命令、结算、tooltip、渲染、tick） | **不可能** | 死分支，删 |

另外要分清：**`isBound()` 检测不出"定义被移出数据包"**。`/reload` 时 `RegistryDataLoader.load` 造的是全新的 `ImmutableRegistryAccess`（`RegistryDataLoader.java:239`），旧 registry 里的 `Reference` 仍然 `key != null && value != null`，`value()` 照样返回**旧值**。所以那些写着"a definition the current pack no longer provides"的 `isBound()` 判断，连它们自称的目的都达不到——`isBound()` 为真，`value()` 给旧值。

> 真正能检测"定义没了"的只有一条路：**拿新 access 按 key 再查一次**（`MxtDatapackRegistries.holder(access, key, id)` 会 `isEmpty()`）。这解释了为什么 `TechniqueCommand.resolves` / `Elements.of` / `QualityService.intrinsic` / `SecretRealmService.enter` 会去查表——但它们查表的**输入**是一个手里已经有的 holder，于是又踩进了模式一。**两者只能选一个**：要么认为 holder 永远有效（那就把查表全删），要么要检测移出（那就统一走一次新 access 查表，而不是到处 `isBound()`）。

---

## 2. 模式一：`Holder` → `Identifier` → 再拿回 `Holder`

### 2.1 `AuraService` —— 三跳往返 + 一整套为它而生的记忆化【HIGH】

这是全仓最完整的一个例子，三跳全在同一个调用里。

**跳 1：holder 降成 id。**

```java
// AuraService.java:475-480
public record Resolved(Optional<Holder<AuraZone>> holder, AuraZone definition, SourceKind kind,
                       Map<Holder<Aura>, Double> maxBonus) {
    public Identifier id() {
        return this.holder.map(HolderHelper::id).orElse(EMPTY);   // ← holder → Identifier
    }
}
```

`AuraResult` 于是只留 id：

```java
// AuraResult.java:17-20
public record AuraResult(Map<Holder<Aura>, AuraPool> aura,
                         Rules rules, double elementFitBonus,
                         double elementConflictPenalty, EntityCondition cultivateCondition, Distribution distribution,
                         Identifier source, SourceKind sourceKind) {   // ← 只有 id，holder 丢了
```

> 注意同一个 record 的 `aura` 字段**是 holder 键的**。`AuraResult` 自己就是两种形状混着用。

**跳 2：id 查回 value。**

```java
// AuraService.java:99-105
public static AuraResult getSensedAura(Level level, BlockPos pos) {
    AuraResult resolved = getPositionAura(level, pos);
    AuraZone zone = MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.AURA_ZONE, resolved.source()).orElse(null);
    if (zone == null) return new AuraResult(...);
    Map<Holder<Aura>, AuraPool> pools = pools(level, holderOf(level, zone), zone, pos, level.getGameTime());  // ← 跳 3
```

**跳 3：value 全表扫描反查 holder。**

```java
// AuraService.java:434-445
// The selected zone travels as an inline codec value that does not retain its own holder, so the holder
// is recovered by identity and memoised per level; the scan would otherwise repeat per block source.
static Holder<AuraZone> holderOf(Level level, AuraZone zone) {
    if (zone == EMPTY_ZONE || !(level instanceof ServerLevel server)) return null;
    return AuraQueryCache.holder(server, zone).orElse(null);
}

static Optional<Holder<AuraZone>> findHolder(RegistryAccess access, AuraZone zone) {
    if (zone == EMPTY_ZONE) return Optional.empty();
    return MxtDatapackRegistries.holders(access, MxtResourceKeys.AURA_ZONE)
            .filter(holder -> holder.value() == zone).findFirst().map(holder -> holder);
}
```

注释说的"the selected zone travels as an inline codec value that does not retain its own holder"是**不成立的**：这里的 `zone` 不是从 codec 解出来的，它是 `Resolved.definition()`，而**同一个 `Resolved` 里 `holder()` 就在旁边**（`staticZone` 走 `resolved(holder, kind)`，`customZone` / `formationZone` 都是 `Optional.of(zone)`；只有 `fallbackZone()` 是 `Optional.empty()`，那种情况走的是 `EMPTY_ZONE`）。

**为了这三跳，额外养了这些：**

| 位置 | 内容 |
|------|------|
| `AuraQueryCache.java:49` | `HOLDER` 记忆化表（`Map<ServerLevel, Map<AuraZone, Optional<Holder<AuraZone>>>>`） |
| `AuraQueryCache.java:63-64` | `HOLDER_HITS` / `HOLDER_MISSES` 两个计数器 |
| `AuraQueryCache.java:156-174` | `holder(ServerLevel, AuraZone)` 整个方法（含 `MAX_ENTRIES` 分片逻辑） |
| `AuraQueryCache.java:95-104` | `Stats` 的 `holderHits` / `holderMisses` 两个字段与 `stats()` 的传参 |
| `AuraQueryCache.java:78-85` | 诊断日志里的 `holder {} hit / {} miss` |
| `AuraQueryCache.java:189` | `clear()` 里的 `HOLDER.clear()` |
| `AuraService.java:436-445` | `holderOf` 与 `findHolder` 两个方法 |
| `AuraService.java:302` | `AuraService` 内部第二处同样的 `holderOf` 调用 |

**修法**：让 `AuraResult` 带上 `Optional<Holder<AuraZone>> zone`（或干脆带 `Resolved`），把 `source` / `sourceKind` 从它派生。上表八项全部可删。

**顺带核过的下游**（改 `AuraResult` 时的爆炸半径，都是**读**，没有写入方）：

- `AuraService.java:101,104,108` —— 本节的三跳
- `AuraZoneEventBridge.java:55` `previous.source().equals(current.source())`、`:89` 装进 payload、`:93` `zones.getOptional(aura.source())`
- `AuraZoneRenderer.java:58` `zones.getOptional(snapshot.source())`
- `ResourceBarOverlay.java:129` `getOptional(snapshot.source())`
- `AuraCommand.java:98`（用 `sourceKind` 查 lang）、`:116-123` `sourceName`（用 `sourceKind` 造一个假的注册表 id 再喂 `DefinitionText.text(registry, id)`）
- `MxtKubeJsEventDispatcher.java:221` `this.event.result().source().toString()`

其中后四处是**因为手里只剩 id 才必须存在**的：`zones.getOptional(id)` 是在客户端按 id 再查一遍区域定义；`sourceName` 得靠 `sourceKind` 把一个 id 的**注册表归属**猜回来。这两件事在"手里有 holder"时都不需要。

> **一个说明**：`source` 不是纯粹的 zone id——BIOME / DIMENSION 情况下它同时要能表示"哪个群系/维度"，而那时确实没有 zone holder（`fallbackZone()` 是 `Optional.empty()` + `EMPTY_ZONE`）。所以正确的修法是**两者都留**（`Optional<Holder<AuraZone>> zone` + 表示"从哪来"的群系/维度 id），而不是把 `source` 换成 holder。即便如此，上面那八项仍然是净删除。

### 2.2 `AuraLookup` —— 两个 `Holder<Resource>` 重载把自己降成 id【HIGH】

```java
// AuraLookup.java:43-53
// The value-to-aura direction, not an identity: an aura names the one value it is measured in
// (Aura#resource), and a value naming no aura is a plain counter rather than a second kind of aura.
public static Optional<Reference<Aura>> holder(@Nullable Provider access, Identifier resource) {
    if (access == null) return Optional.empty();
    return all(access).filter(aura -> HolderHelper.id(aura.value().resource()).equals(resource)).findFirst();
}

// Keeps the holder: a realm chain is keyed by the aura holder, so comparisons need the reference.
public static Optional<Reference<Aura>> holder(@Nullable Provider access, Holder<Resource> resource) {
    return holder(access, HolderHelper.id(resource));        // ← Holder<Resource> → Identifier
}
```

`find(Provider, Holder<Resource>)`（:63-65）是同一个形状。于是：

- 每个 `Holder<Resource>` 都先 `unwrapKey().map(ResourceKey::identifier)` 再进全表扫描；
- 扫描里又把 `aura.value().resource()`（本来就是 `Holder<Resource>`）再降一次 id 来比。

**正确地写**：`filter(aura -> aura.value().resource().equals(resource))`——两边都是 `Holder<Resource>`，`Reference.equals` 直接回答。整个 `HolderHelper.id` 两次调用消失，`Identifier` 重载只剩给命令/脚本用。

### 2.3 `QualityService.intrinsic` —— 两个模式挤在 8 行里【HIGH】

```java
// QualityService.java:326-334
// The tier the stack itself carries. With a registry the id is re-resolved, so an id the current pack does not
// provide answers nothing rather than a dead holder; without one the held holder is the whole answer, which is
// the same rule attachments follow - a holder outlives the pack that wrote it until the stack is decoded again.
private static Optional<Holder<ItemQuality>> intrinsic(@Nullable RegistryLookup<ItemQuality> registry, ItemStack stack) {
    Holder<ItemQuality> direct = stack.get(MxtDataComponents.QUALITY.get());
    if (direct == null) return Optional.empty();
    if (registry == null) return direct.isBound() ? Optional.of(direct) : Optional.empty();   // 模式二
    return direct.unwrapKey().flatMap(registry::get).map(holder -> holder);                   // 模式一 + 空转 map
}
```

三个问题叠在一起：`isBound()` 死分支、`unwrapKey().flatMap(registry::get)` 往返、`.map(h -> h)` 空转。

`MxtDataComponents.QUALITY` 的载荷是 `Holder<ItemQuality>`，组件被 `RegistryFixedCodec` 解出来，所以按 §1.4 它**恒在册且已绑定**。整个方法塌成：

```java
private static Optional<Holder<ItemQuality>> intrinsic(ItemStack stack) {
    return Optional.ofNullable(stack.get(MxtDataComponents.QUALITY.get()));
}
```

（`registry` 参数随之可删；调用点需一起看，本审计未展开。）

同类还有 `QualityService.java:79-81`：

```java
Holder<T> holder = extract.apply(value).orElse(null);
// A holder naming a definition the current pack no longer provides answers nothing rather than a dead
// tier, so the stack falls through to the table.
return holder == null || !holder.isBound() ? Optional.empty() : holder.value().defaultQuality();
```

`!holder.isBound()` 是死分支（理由同 §1.5），可删。

### 2.4 `Elements.of` —— holder 降 id 再查回 value【HIGH】

```java
// Elements.java:34-39
public static Set<Holder<Element>> of(SpiritIdentityAttachment spirit, Provider access) {
    return spirit.activeSpiritRoots().stream()
            .flatMap(root -> MxtDatapackRegistries.get(access, MxtResourceKeys.SPIRIT_ROOT, HolderHelper.id(root)).stream())
            .flatMap(root -> root.elementHolders().stream())
            .collect(Collectors.toUnmodifiableSet());
}
```

`activeSpiritRoots()` 返回的就是 `List<Holder<SpiritRoot>>`，每个 `root` 先降 id、再查回 `SpiritRoot` value，只为了取它的 `elementHolders()`。中间的 `Identifier` 是纯过路：

```java
.flatMap(root -> root.value().elementHolders().stream())
```

方法上方的注释（:31-33）也在为这次查表辩护：`The lookup is also what leaves out a root whose definition the current pack no longer provides.`——这正是 §1.5 说的那个二选一。**要么**接受 holder 恒有效（删查表），**要么**想保留"移出即失效"的语义（那么正确做法是在 `SpiritIdentityAttachment` 落到实体上时做一次 prune，而不是每次查询都查表）。现状是每问一次元素就跑一趟注册表。

### 2.5 `SecretRealmService.enter` —— 收了 holder，进门就降 id 再查【HIGH】

```java
// SecretRealmService.java:40-45
public static Result enter(LivingEntity traveller, MinecraftServer server, Holder<SecretRealm> definition) {
    Identifier id = HolderHelper.id(definition);
    // The token can outlive the definition it names, so the current registry is the last word on whether it
    // still exists.
    if (MxtDatapackRegistries.holder(server.registryAccess(), MxtResourceKeys.SECRET_REALM, id).isEmpty())
        return Result.rejected(Failure.DISABLED);
```

签名收的是 `Holder<SecretRealm>`，第 41 行立刻降 id，第 44 行拿它查回同一个东西。两个调用方给的都已经是 holder：

- `SecretRealmCommand.java:81` —— 来自 `ResourceArgument`（原版按注册表解析出来的 holder）
- `SecretRealmTokenItem.java:48` —— 来自物品组件 `mxt:secret_realm_token` 的 `Holder<SecretRealm>` 载荷

之后第 53 行还在用 `definition.value()`，第 59/64 行把 `definition` 传给 `SecretRealmRegistry`，说明 holder 一路都在手上。

### 2.6 炼丹：把 id 当工作货币【HIGH】**【2026-10-15 已修】**

一条链上三个转换点，起点和终点都是 holder：

```
AlchemyRecipe.minimumAura             : Map<Holder<Aura>, NumberProvider>          ← 数据包解出来的 Reference
  ↓ AlchemyResolver.nonNegativeMap    : HolderHelper.id(entry.getKey())            ← 降 id
AlchemyResolver.Evaluated.minimumAura : Map<Identifier, Double>                    （:226）
  ↓ AlchemyWorkstationService.parameters → Parameters                              （:76-78）
Parameters.minimumAura                : Map<Identifier, Double>                    （:499）
  ↓ AlchemyWorkstationService.environment
MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.AURA, entry.getKey())  ← 查回 holder（:284）
  ↓ aura.pool(holder)   ← 而 AuraResult.aura 本来就是 Holder<Aura> 键的
```

```java
// AlchemyResolver.java:178-180
private static void add(Map<Identifier, Double> into, Map<Holder<MedicinalProperty>, Double> properties, int count) {
    properties.forEach((property, amount) -> into.merge(HolderHelper.id(property), amount * count, Double::sum));
}

// AlchemyResolver.java:189-197
private static @Nullable Map<Identifier, Double> numbers(Map<Holder<MedicinalProperty>, NumberProvider> providers, FormulaContext context) {
    Map<Identifier, Double> result = new LinkedHashMap<>();
    for (Map.Entry<Holder<MedicinalProperty>, NumberProvider> entry : providers.entrySet()) {
        Double value = positive(entry.getValue(), context);
        if (value == null) return null;
        result.put(HolderHelper.id(entry.getKey()), value);
    }
    return result;
}

// AlchemyResolver.java:199-207  —— 同一形状，换成 Holder<Aura>
private static @Nullable Map<Identifier, Double> nonNegativeMap(Map<Holder<Aura>, NumberProvider> providers, FormulaContext context) { … }
```

调用点：`AlchemyResolver.java:68`（`main_requirements`）、`:75`（`minimum_aura`）。

**关键点：这里的 id 键不是编解码逼出来的。** 模组已经有能装 holder 键的 map codec——`CollectionCodecs.map(holderCodec, valueCodec)` → `TolerantMapCodec`（`CollectionCodecs.java:25-27`，`TolerantMapCodec.java:16-21`），`PillUsageAttachment` / `CurseHolderAttachment` / `ProgressionHolder` 就是这么存 holder 键的。所以把 `Evaluated` / `Parameters` 的工作形状改成 `Map<Holder<Aura>, Double>` / `Map<Holder<MedicinalProperty>, Double>` 没有序列化障碍，第 284 行的运行期查表随之消失（`environment` 是每次炼丹都跑的热路径）。

`Parameters` 的 codec 目前在 `AlchemyWorkstationService.java:508-510`，用 `Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE)`；换成 `CollectionCodecs.map(Aura.CODEC, Codec.DOUBLE)` 即可（破坏性：JSON 形状从对象变成对表，如果这个 codec 有对外可见的载荷需要同步文档）。

> **【2026-10-15 已按此修法落地，一处更正】**上面最后那句"破坏性、JSON 形状从对象变对表"是**错的**——`CollectionCodecs.map` 走的是 `TolerantMapCodec`（`BaseMapCodec`），和 `Codec.unboundedMap` 一样编成"id 字符串当键的 JSON 对象"，**形状不变、老存档直接读**。这批实际落地的与上面诊断不同的两点：
>
> 1. **`Gap.property` 也跟着换了**（诊断里没提）：`Gap` 是"缺什么/多什么"的报告记录，`evaluated.main()` 换成 holder 键之后它的 `property` 必须是 `Holder<MedicinalProperty>`，否则 `extra(...)` 里的 `required.containsKey(...)` 就永远不命中、`EXTRA` 那一类**静默消失**（`reason()` 因此永远给不出 `CONFLICT`）。`cover(...)` 随手改成泛型 `<K>`。
> 2. **`ResourceCost#id()` 顺带删掉了**（批 3 把它列为"显示层 id，留用"）：`Costs#validate` 的重复检测早就改成 holder 键（批 1），全仓最后一个调用方是测试包的 `MxtTestCommands:2581` 的 `cost.id().equals(QI)`，改成 `cost.resource().is(qiResource)` 之后这个方法就没有调用方了。报错文案改走 `HolderHelper.id(resource.resource())`。
>
> 连带改掉的：`AlchemyMixture`（`main`/`auxiliary` 两个字段 + CODEC）、测试包 `AlchemyProbes`（`MAIN` 常量变成 `property(level, MAIN)` 取一次 holder，`main(preview, property, fallback)` 助手）与 `AlchemyGateProbes`（`property(level, preview, "...")`）、`AlchemyWorkstationService#environment` 里那条**每炉每个光环都跑一次**的注册表查表。**JEI 一侧不用改**：`AlchemyJeiText` 读的是 `AlchemyRecipe` 自己的字段（本来就是 holder 键），不经过 `Parameters`。

### 2.7 `FlightAttachment.vehicle` —— 同一个 record 里两种形状【HIGH，因为自相矛盾】

```java
// FlightAttachment.java:23-32
public static final MapCodec<FlightAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.BOOL.lenientOptionalFieldOf("active", false).forGetter(FlightAttachment::active),
        Ability.CODEC.lenientOptionalFieldOf("archetype").forGetter(FlightAttachment::archetype),   // ← Holder<Ability>
        Identifier.CODEC.lenientOptionalFieldOf("vehicle").forGetter(FlightAttachment::vehicle),    // ← Identifier
        …
```

**两个字段装的是同一种东西**（都是 `mxt:ability` 注册表的条目，见 §其下），却一个用 `Holder`、一个用 `Identifier`：

- 写入侧：`FlightService.java:139` `data.start(skill, HolderHelper.id(found.ability()), …)` —— `found.ability()` 本来就是 `Holder<Ability>`。
- 读取侧：`FlightEventBridge.java:39-47`

```java
// Resolved by id rather than kept as a holder, so a mount whose definition is gone mid-flight lands.
Holder<Ability> vehicle = data.vehicle()
        .flatMap(id -> Abilities.resolve(holder.level().registryAccess(), id))
        .filter(mount -> mount.value().type() instanceof MountAbilityType)
        .orElse(null);
```

于是一个**每 tick 都跑**的实体 tick 钩子，每次都要按 id 查一次能力注册表。把字段换成 `Ability.CODEC` 之后这段就是 `data.vehicle().filter(...)`，`Abilities.resolve` 消失，`HolderHelper.id` 消失，注释里那句辩护（和 `:33` 那句 `A stored holder cannot notice its own definition leaving the pack`）也一起删——它属于 §1.5 那个二选一。

### 2.8 三份复制粘贴的 `EMPTY` 哨兵【MEDIUM】

同一个形状在三处各写了一遍：

```java
// AlchemyWorkstationService.java:58-65
public static Optional<Holder<AlchemyFurnaceDefinition>> furnaceDefinition(Provider access, ItemStack stack) {
    if (stack.isEmpty()) return Optional.empty();
    Holder<AlchemyFurnaceDefinition> stored = stack.get(MxtDataComponents.ALCHEMY_FURNACE.get());
    if (stored == null) return Optional.empty();
    Identifier id = HolderHelper.id(stored);                       // ← 降 id
    if (id.equals(HolderHelper.EMPTY)) return Optional.empty();    // ← 哨兵比较
    return MxtDatapackRegistries.holder(access, MxtResourceKeys.ALCHEMY_FURNACE, id).map(holder -> holder);
}
```

另两处：

- `AlchemyFurnaceCasingBlockEntity.java:58-61`（`ALCHEMY_WALL_MATERIAL`）
- `AlchemyFurnaceTooltipAppender.java:91-93`（同一张表）

三处都是：组件里本来是 holder → 降 id → 判 `EMPTY` 哨兵 → 按 id 查回 holder。按 §1.4，`stored` 恒在册，所以三处都可以塌成两行：

```java
Holder<X> stored = stack.get(...);
return Optional.ofNullable(stored);
```

`HolderHelper.EMPTY` 这个哨兵（`HolderHelper.java:10`）本身也值得重估：它只可能由 `Direct` holder 触发（§1.1），而模组没有任何一个 `HolderHelper.id` 调用点会收到 `Direct`（§1.4）。也就是说这个哨兵语义**目前没有任何真实触发路径**，却已经在 4 处被当成"这个 holder 是坏的"来判（下面 §3.2）。

### 2.9 `WheelSources.carriers` / `WheelContent`【MEDIUM】

```java
// WheelSources.java:86-94
public static Map<Identifier, ItemStack> carriers(LivingEntity entity, WheelSource source) {
    Map<Identifier, ItemStack> found = new LinkedHashMap<>();
    for (ItemStack stack : source.equipment(entity)) {
        if (stack.isEmpty()) continue;
        for (Holder<Ability> ability : ArtifactService.abilities(entity.level().registryAccess(), stack))
            found.putIfAbsent(HolderHelper.id(ability), stack);     // ← holder → id 当键
    }
    return found;
}
```

消费侧同一次往返回来：

- `WheelContent.java:80` `carriers.get(HolderHelper.id(ability))`
- `WheelContent.java:93` 同上
- `WheelContent.java:104` 同上
- `WheelSources.java:72` `sources(entity, HolderHelper.id(ability))`（`handOnly`）
- `WheelSources.java:43` `holder.sources().of(HolderHelper.id(ability))`

改成 `Map<Holder<Ability>, ItemStack>` 之后，1 次构造 + 4 次查询里的 `HolderHelper.id` 全部消失。这里没有正确性 bug（同一个注册表内 id 不撞），是**类型往返**的噪音。

### 2.10 锻造全线：holder → id 是默认做法【MEDIUM】

锻造模块把 id 当成了通用货币，从计划、会话历史、工作台状态一直穿透到结算：

```java
// ForgingPlan.java:42-44（从蓝图生成计划——入参是 Holder）
Map<Identifier, Integer> deltas = new LinkedHashMap<>();
for (Holder<ForgingMethod> method : blueprint.resolvedMethods(registries))
    deltas.put(HolderHelper.id(method), method.value().valueDelta());
```

```java
// ForgingService.java:58-59（打铁——入参是 Holder<ForgingMethod>）
Identifier methodId = HolderHelper.id(method);
if (!session.canStrike(methodId)) return StrikeResult.rejected(Failure.INVALID_STRIKE, null);
```

```java
// ForgingService.java:94
ForgingResultComponent result = new ForgingResultComponent(HolderHelper.id(blueprint), session.value(), …);
```

同一形状还有：`ForgingSession.java:17,94-97`（`history: List<Identifier>`）、`ForgingTableState.java:23,30,57,80`（`blueprint: Identifier`）、`ForgingPlan.java:20-29`（`finishPattern: List<Identifier>`、`Map<Identifier, Integer> deltas`）、`:87-92`（`deltaIfAllowed(Identifier)` / `delta(Identifier)`）、`:129,139-140`、`ForgingBindingService.java:44-58`（用 id 键的 map 做去重，而输入是 `Holder` 的流）、`ForgingWorkstationService.java:50-51,60-61,70-71,117-121,143-150,210-211,252-263`（对外一律 `List<Identifier>` / `Identifier` 参数，进门第一件事是查回 holder）。

判断要分两层：

- **`ForgingSession` / `ForgingTableState` 的持久化形状**用 id 是可辩护的（它进 `SavedData` / snapshot codec，且历史里的方法可能已经被移出数据包——id 能保留一段"已经不存在的历史"）。这与 §1.5 的二选一有关，属于设计选择，**不算错**。—— **【2026-10-15 已按"统一"一侧落地】**批 5 还是把它们换成了 holder 键，接受了"移出即失史"：`ForgingPlan` / `ForgingSession` / `ForgingTableState` 三个 codec 的方法一律 `Holder<ForgingMethod>`，`ForgingTableState` 构造器把重建不出来的会话**丢掉并 unlock**（原来抛异常 ⇒ `forging` 字段整个读不出来）。**这次改动留下的材料搁浅口子已在同一轮补上**（`returnStrandedMaterials`，见 [`Holder修复清单.md`](Holder修复清单.md) 的 ⚠3）。
- **但 `ForgingPlan.deltas` 是运行期派生数据**（`from(blueprint)` 刚用 `Holder` 迭代出来的），第 87-92 行再用 `HolderHelper.id(method)` 查回来就是纯往返；`ForgingBindingService` 的 id 去重 map 同理（`Set<Holder<ForgingMethod>>` 就够，`Reference.equals` 保证同条目同格）。

### 2.11 战利品条件/函数：刚解析出来的 holder 立刻降 id【MEDIUM】

```java
// HasAbilityLootCondition.java:31-32
return entity != null && MxtDatapackRegistries.holder(entity.registryAccess(), MxtResourceKeys.ABILITY, this.ability)
        .map(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).has(HolderHelper.id(ability))).orElse(false);
```

```java
// GrantAbilityLootFunction.java:48-50
if (entity != null) MxtDatapackRegistries.holder(entity.registryAccess(), MxtResourceKeys.ABILITY, this.ability)
        .ifPresent(ability -> {
            if (entity.getData(MxtAttachments.ABILITY_HOLDER).grant(HolderHelper.id(ability), this.source)
```

`this.ability` 是战利品 JSON 里的字段，**从 id 开始是合理的**（序列化边界）。但 `.map(ability -> …HolderHelper.id(ability))` 这一手是把刚查回来的 holder 又降回同一个 id——纯浪费，`AbilityAttachment.has/grant` 收的就是 id，所以**直接传 `this.ability` 即可**，整次注册表查表都可以省掉（查表只为了验证存在性，而 §3.2 说这个验证对 `Holder` 没意义；对裸 id 它确实有意义，但那时没必要先查成 holder）。

> **【2026-10-15 变更，结论反转】**`AbilityAttachment.has` / `grant` / `revoke` 现在收 `Holder<Ability>`，所以这一处（以及 `GrantAbilityLootFunction`）改成**先 `Abilities.resolve(...)` 成 holder 再传**：查表从"浪费"变成必要的一步。降 id 的那一手确实没了。

### 2.12 消耗草稿把账户做成 id 键：回退可能静默失效【HIGH，唯一一处有正确性风险】

```java
// AuraCostDraft.java:53 —— 手里有 Holder<Resource>（就是 Aura.resource），降成 id 塞进 account
this.account.merge(HolderHelper.id(cost.aura().value().resource()), value, Double::sum);

// ResourceCostDraft.java:82-85 —— commit 时再把每个 id 查回 holder 才记回退现场
for (Identifier id : this.account.keySet())
    MxtDatapackRegistries.holder(access, MxtResourceKeys.RESOURCE, id).ifPresent(resource -> {
        Audit audit = this.target.audit(resource);
        this.written.put(resource, new Value(resource, this.target.get(resource), …));
```

`account` 是 `Map<Identifier, Double>`（`CostPayment.java:29/225-232/279`、`CostDraftManager.java:47/56`）。两个后果：

1. **回退现场只记录"还能查回来"的 id**。同一个 `commit` 真写下的资源，如果那次 `holder(...)` 查不到（access 为 null、或定义被移出数据包），`written` 里就没有它，于是 `reset()`（`ResourceCostDraft.java:92-97`）**静默漏还原**。`:78-79` 的注释假设"两次查表会同样地失败"，这个假设只有在两处用同一个 access 时才成立。
2. 同一份 id 在范围外又被查了一次：`screen/wheel/content/WheelTooltips.java:52`。

**修法**：`account` 改成 `Map<Holder<Resource>, Double>`——草稿自己的 `Value` record 早就存的是 `Holder<Resource>`（`ResourceCostDraft.java:112-113`），而 aura→resource 那一步用的 holder 就是 `Aura.resource` 本身，**根本不需要降 id**。这一改让"写下的现场"与"回退的现场"天然是同一批键，`reset()` 的不确定性消失。

> **【2026-10-15 已按此修法落地】**`CostPayment.account`、`ResourceCostDraft`、`AuraCostDraft`、`CostDraftManager` 的工厂签名、`ResourceTransactions.Evaluation` 全部改成 `Holder<Resource>` 键；`commit` 里那次 `holder(access, RESOURCE, id)` 查表**整段删掉**（写下的现场就是账户自己的键）。`CostPayment.resources()` 保留 id 键契约但改成**副本**，并新增 `account()`（holder 键，草稿与 tooltip 读它）、`evaluation()`（交给 `ResourceTransactions`）、`reprice(Map<Identifier, Double>)`（监听器把 id 表交回来时解析一次）；`AbilityService` ×3、`CultivationService`、`FlightService` 里原先 `resources().clear()+putAll(...)` / `entry.setValue(...)` 的写法改成 `reprice` / `account()`（**这几处如果只改类型会静默失效**，因为它们改的是副本）。`ResourceTransactions.tryConsume` 不再需要 `Provider access`（每项一次查表没了），`Result` 仍报 id。

### 2.13 `CreatureProfileService` —— 三趟 id 往返，而且成功是假的【HIGH】

```java
// :31-36  select：每个 holder 降成 id，再查回来判断类型
return MxtDatapackRegistries.holders(access, MxtResourceKeys.CREATURE_PROFILE).map(holder -> holder.key().identifier())
        .filter(id -> MxtDatapackRegistries.get(access, MxtResourceKeys.CREATURE_PROFILE, id)
                .filter(value -> matchesType(creature, value)).isPresent())
        .min(Comparator.naturalOrder());
```

```java
// :76-80  applySelected：拿到 id 再查成 value
Identifier id = select(creature).orElse(null);
return id != null && MxtDatapackRegistries.get(creature.level().registryAccess(), MxtResourceKeys.CREATURE_PROFILE, id)
        .map(definition -> apply(creature, id, definition, FormulaContext.of(creature))).orElse(false);
```

```java
// :57-68  apply：第三趟——再按 id 查成 holder 才写附件，然后无条件 return true
MxtDatapackRegistries.holder(creature.level().registryAccess(), MxtResourceKeys.CREATURE_PROFILE, id)
        .ifPresent(profile -> {
            creature.getData(MxtAttachments.CREATURE_SPIRIT).apply(profile, intelligence, …);
            AbilityGrantService.recalculate(creature);
            definition.spawnAction().execute(creature, context);
        });
return true;                       // ← :68 即使上面 ifPresent 空跑，也报成功
```

同一个 id 在这条路上被解析了**三次**（`select` 一次、`applySelected` 一次、`apply` 一次）。

**正确性问题**：`:68` 的 `return true` 与 `:57` 的 `ifPresent` 之间没有联系——查不到 holder 时什么都没写，函数却报"应用成功"。今天靠 `applySelected` 在 `:79` 先 `get(...)` 过一道，所以实际不可达；但这是三次冗余查表**造出来**的漏洞：把 holder 一路传下去，`apply` 的成功路径就自然只剩下一条。

**修法**：`select` 返回 `Optional<Holder<CreatureProfile>>`（`.filter(holder -> matchesType(creature, holder.value()))`），`applySelected` / `apply` 都收 holder，写附件直接用那个 holder；`return true` 移进持有 holder 的分支里。

### 2.14 `CultivationMethodService.ActiveCultivation` —— 记录自己就发 holder，调用方却传 id【MEDIUM】

```java
// CultivationMethodService.java:446-457
private Identifier id() { return HolderHelper.id(this.aura.value().resource()); }      // ← 降 id
private Holder<Resource> resource() { return this.aura.value().resource(); }          // ← holder 就在旁边
```

调用点：`:331`、`:340`、`:394`、`:406` `ResourceService.change(resources, active.id(), absorbed, …)`。其中 `convert(...)` 里 `:382` 就写着 `Holder<Resource> resource = active.resource();`，`:388` 还用它取过值，**两行之后又传 id**。`ResourceService.java:57-60` 收到 id 后再 `:64-66` 查一次注册表。

**修法**：改调 `ResourceService.change(resources, active.resource(), …)`（`Holder<Resource>` 重载 `ResourceService.java:44` 已存在），删掉 `ActiveCultivation.id()`。

### 2.15 `AuraService` 的群系：`Holder<Biome>` → id → 注册表 → holder【MEDIUM】

```java
// AuraService.java:147-155
Identifier memoised = location == null ? null : AuraQueryCache.biome(server, location);
if (memoised == null) {
    memoised = HolderHelper.id(level.getBiome(pos));            // ← Holder<Biome> → Identifier
    …
    if (location != null) AuraQueryCache.cacheBiome(server, location, memoised);
}
Identifier biome = memoised;
…
Resolved biomeResult = best(level, holder -> RegistryCodecs.matches(holder.value().biomes(), biomeRegistry, Registries.BIOME, biome))
```

`RegistryCodecs.matches(…, Registry, key, Identifier)`（`RegistryCodecs.java:61-63`）内部就是 `registry.get(ResourceKey.create(key, candidate))`——**把 id 还原成 `level.getBiome(pos)` 刚刚返回过的那个 holder**。而 `AuraQueryCache.BIOME`（`AuraQueryCache.java:47,258-278`）也就在缓存这些 id。

**修法**：缓存并传 `Holder<Biome>`（`Holder` 相等按 key，做缓存键是安全的），用 holder 重载 `RegistryCodecs.matches(holder.value().biomes(), biomeHolder)`（`RegistryCodecs.java:39`）。

### 2.16 其余"手里有 holder 却降 id"的小点【MEDIUM】

| 位置 | 现状 | 修法 |
|------|------|------|
| `data/action/builtin/entity/AddResourceAction.java:35-39` | `Identifier id = HolderHelper.id(this.resource);` 然后 `ResourceService.change(…, id, …)`；id 重载在 access 为 null 时报 `INVALID`（`ResourceService.java:39-42,57-59,64-65`） | 直接调 `Holder<Resource>` 重载（`:44`/`:86`），删 `HolderHelper` import |
| `runtime/ability/AbilityEventBridge.java:240-241` | `HolderHelper.idOptional(resource).map(id -> ResourceService.initialize(holder, id, ResourceService.formulaContext(entity, id, …)).changed()).orElse(false)` | `ResourceService.initialize(holder, resource, ResourceService.formulaContext(entity, resource, …))`（`Holder` 重载 `ResourceService.java:27` 已存在）；现在这个写法在 holder 没有 key 时**整通调用连 `false` 都吞掉** |
| `runtime/artifact/ArtifactStorageContainer.java:54` + `ArtifactService.java:217,230-233` | 字段是 `Holder<Ability> ability`，`ArtifactStorageContainer` 传 `HolderHelper.id(this.ability)`，`ArtifactService.holds` 再 `HolderHelper.id(ref).equals(abilityId)` | `carried(Provider, LivingEntity, Holder<Ability>)` + `ref.is(ability)`；`carried` 全仓只有一个调用方 |
| `data/ability/type/FlightControlAbilityType.java:65` | `data.archetype().filter(skill -> skill.is(HolderHelper.id(context.ability()))).isPresent()` | `skill.is(context.ability())`（两边都是 `Holder<Ability>`） |
| `data/alchemy/AlchemyHeatSourceTooltipAppender.java:70-73` | 手里有 `Block block`，却 `BuiltInRegistries.BLOCK.getKey(block)` 转成 id 去调 id 重载 | `RegistryCodecs.matchesValue(definition.blocks(), BuiltInRegistries.BLOCK, block)`（同一形状的正确用法见 `SpiritHerbService.java:81`） |
| `data/item/SpiritBeastComponent.java:46,52,70` | 捕获时手里有实体，却把 `Optional<Identifier> entityType` 存成 `BuiltInRegistries.ENTITY_TYPE.getKey(beast.getType())`；释放时 `item/SpiritBeastBagItem.java:80` 再 `getOptional(...).orElse(null)` | 字段改 `Optional<Holder<EntityType<?>>>`（`RegistryFixedCodec.create(Registries.ENTITY_TYPE)`），写入用 `wrapAsHolder`；序列化字节不变，释放路径不再有 `orElse(null)` 空洞 |

### 2.17 附件的 id 键：一致性检查【MEDIUM / 边界】

同一件事在本仓有两种写法，建议明确选一种：

| 位置 | 现状 | 判断 |
|------|------|------|
| `attachment/CurseHolderAttachment.java:27-28` | `Map<Holder<Curse>, State>` + `SourceLedger<Holder<Curse>>` | **好形状** |
| `data/progression/ProgressionHolder.java:22` | `Map<Holder<T>, Holder<Progression>>` | **好形状**（`research/85` 定的案） |
| `attachment/PillUsageAttachment.java:30` | `Map<Holder<PillBinding>, Dose>`，但外面套了一层 id 回退 | 删那层回退（§3.3） |
| `attachment/AbilityAttachment.java:29-33` | `SourceLedger<Identifier>` + `DataStorageHolder`（id 键） | 类注释 `:20-26` 说得很清楚：**故意保留查不到的 id，好让"定义被删了也能撤销授予"**。这条理由**成立**（`Holder` 键也能保留、但 id 是更直白的存档形状），且 id 是数据包 JSON 字段的直系产物 ⇒ **NOT-A-FINDING**　**【2026-10-15：此条已作废】**用户拍板把 `AbilityAttachment.sources` 与 `DataStorageHolder` 的地址都改成 `Holder<Ability>`（`SourceLedger.codec(Ability.CODEC)` + 地址按 holder），依据是 `AGENTS.md` §4 的"附件里指向注册表的字段存 `Holder`"这条通例；代价是定义被删的授予/状态不再保留（解码时那一项丢掉并打一行日志），**来源 id 仍是 id**（`mxt:grant/...` 是 `runtime/Sources` 写的存档事实） |
| `attachment/TriggerCooldownAttachment.java:19,33-39` | `Object2LongMap<Identifier>`，唯一调用方 `TriggerRuleService.java:42-45` 手里是 `Reference<TriggerRule>`，且没有任何地方再查回来 | 边界情形：按 id 只是键类型弱一些，无实际代价。要统一就改 `Holder<TriggerRule>`，不改也说得过去 |
| `data/artifact/ForgingResultComponent.java:11,14` | `Identifier blueprint`，写入来自 `Holder<ForgingBlueprint>`（`ForgingService.java:94`），今天没有读取方再查回 | 形状问题：换 `RegistryFixedCodec` 才能回答"这张蓝图还在不在" |
| `data/artifact/ArtifactDescription.java:201` | `ItemStorageService.get(stack, HolderHelper.id(ability), …)`，而 `ability` 是从堆自己的 id 列表刚解析出来的（`ArtifactService.java:173`）；列表由 `AddAbilityAction.java:39` 写成 `List<Identifier>` | 一次性改成 `Holder<Ability>` 组件（holder codec，字节不变）能同时消掉 `:39`、`ArtifactService.java:173` 与 `:201` 三处　**【2026-10-15：前两处的一半已做】**`DataStorageHolder` 与 `ItemStorageService` 已改收 `Holder<Ability>`，`:201` 那层 id 往返没了；`ItemAbilitiesComponent`（`List<Identifier>`）+ `ArtifactService:173` 的解析仍在，属批次 5 |

---

## 3. 模式二：检查 holder 在不在注册表

### 3.1 `TechniqueCommand.resolves` / `resolvesLevel`【HIGH】

```java
// TechniqueCommand.java:198-212
// Checked by id against the live registry: a removed entry has no value to read, and asking for one would
// throw. Package-visible for the server audit, which cannot build a genuine unbound holder.
public static boolean resolves(Provider access, Holder<Technique> technique) {
    if (technique == null) return false;
    Identifier id = HolderHelper.id(technique);
    if (id.equals(HolderHelper.EMPTY)) return false;
    return MxtDatapackRegistries.get(access, MxtResourceKeys.TECHNIQUE, id).isPresent();
}

public static boolean resolvesLevel(Provider access, Holder<Progression> level) {
    if (level == null) return false;
    Identifier id = HolderHelper.id(level);
    if (id.equals(HolderHelper.EMPTY)) return false;
    return MxtDatapackRegistries.get(access, MxtResourceKeys.PROGRESSION, id).isPresent();
}
```

注释里两个断言都是错的或不可达的：

- `a removed entry has no value to read, and asking for one would throw` —— **不成立**。移出数据包后旧 `Reference` 仍然绑定着旧值（§1.5），`value()` 不抛。真会抛的是"从未绑定的 `Reference`"，而那只能来自 §1.5 的并行加载窗口。
- `which cannot build a genuine unbound holder` —— 这句是实话，而且正好说明测试探针永远造不出需要这个分支的输入。
- `id.equals(HolderHelper.EMPTY)` —— 哨兵比较，按 §2.8 无真实触发路径。

调用方都在 `TechniqueCommand` 内部：

- `:170-182` `prune(...)`：`if (!resolves(access, technique) || !seen.add(id))`，`seen` 是 `Set<Identifier>` 做去重（`Set<Holder<Technique>>` 就够）
- `:186-196` `pruneLevels(...)`：`if (removed.contains(HolderHelper.id(entry.getKey())) || !resolvesLevel(access, entry.getValue()))`，`removed` 是 `List<Identifier>` 做黑名单
- 这两个是**极少数真的想检测"定义被移出数据包"**的地方（方法名就叫 `prune` 且用于"清理陈旧记录"），所以这里**不该简单删掉**，而应该：判据保持"拿新 access 查表"（这是唯一有效的判据），但**入参别用 holder → id 的往返**——`MxtDatapackRegistries.holder(access, key, ...)` 需要 id，所以要么接受这次 id 化（此时 `EMPTY` 判断可删，改为 `id == null` 判断更直白），要么把 `removed` 改成 `Set<Holder<...>>` 并让比较走 `equals`。

### 3.2 `isBound()` 清单

运行期（死分支，可删）：

| 位置 | 代码 |
|------|------|
| `QualityService.java:81` | `return holder == null \|\| !holder.isBound() ? Optional.empty() : holder.value().defaultQuality();` |
| `QualityService.java:332` | `if (registry == null) return direct.isBound() ? Optional.of(direct) : Optional.empty();` |
| `PillService.java:68` | `if (!pill.isBound()) return Optional.of(Failure.UNBOUND);` |
| `PillService.java:91` | `if (entity.level().isClientSide() \|\| !pill.isBound()) return;` |
| `ItemBindingService.java:116` | `if (source != null && !source.isBound()) return PillResolution.unbound(source);` |
| `ItemBindingService.java:235` | `if (identity != null && identity.isBound()) PillService.registerUse(entity, identity);` |
| `SpiritHerbService.java:137` | `if (!entry.getKey().isBound()) continue;` |
| `SpiritHerb.java:98` | `if (entry.quality().isBound()) earned = entry.quality();` |
| `ItemPickerManager.java:92` | `if (!holder.isBound()) return List.of();` |
| `TechniqueTintSource.java:44` | `… holder == null \|\| !holder.isBound() ? null : holder.value() …` |
| `ItemBindingTooltipAppender.java:146` | `if (identity == null \|\| !identity.isBound()) return;` |

**其中 `ItemPickerManager.java:92` 最铁**：那个 lambda 的 `holder` 就是从注册表流里迭代出来的（同一个文件 `:78-107` 全是 `registerSingle(MxtResourceKeys.X, holder -> …)`），它不可能不在册也不可能没绑值。

**解码期（合法，保留）**：`SpiritHerb.java:75-76` 附近那条注释所描述的场合、`SecretRealmGeneration.java:12`、`HoldLookup.java:26`。这三处是"读 unbound 的东西会抛"的真实防护，**和上表混在一起最容易误删**——删的时候要按调用时机筛，不能只按 `isBound()` 字样 grep。

### 3.3 `PillUsageAttachment.dose` —— 注释的理由是错的【HIGH】

```java
// PillUsageAttachment.java:52-61
// Holder identity is not stable across a reload, so a saved key is matched by id when the instance differs.
public Dose dose(Holder<PillBinding> pill) {
    Dose direct = this.entries.get(pill);
    if (direct != null) return direct;
    Identifier id = HolderHelper.idOrNull(pill);
    if (id == null) return null;
    for (Map.Entry<Holder<PillBinding>, Dose> entry : this.entries.entrySet())
        if (entry.getKey().is(id)) return entry.getValue();
    return null;
}
```

`entries` 是 `Map<Holder<PillBinding>, Dose>`（`:30-36`），写入走 `record(...)`（`:63-68`）。

**注释那句 `Holder identity is not stable across a reload` 是错的**：`HashMap.get(pill)` 走 `hashCode()` = `key().hashCode()` 与 `Reference.equals` = `h.getKey() == this.key()`，而 `ResourceKey` 是 interned 的（§1.3），所以**新旧两个 `Reference` 即使实例不同，`get` 也会命中**。那个 O(n) 回退扫描永远不会被执行到。

`record` 里的清理也是同一形状：

```java
// PillUsageAttachment.java:63-68
public void record(Holder<PillBinding> pill, int uses, long cooldownUntil) {
    Identifier id = HolderHelper.idOrNull(pill);
    if (id != null) this.entries.keySet().removeIf(key -> key != pill && key.is(id));   // ← 同 id 的不同实例
    this.entries.put(pill, new Dose(Math.max(0, uses), cooldownUntil));
    this.markDirty();
}
```

`removeIf` 想删的是"同一个绑定的旧 holder 实例"，但 §1.3 保证 `entries.put` 本来就会覆盖同一个键（`equals` 相等 ⇒ 同一个哈希桶），所以这段 `removeIf` 也在保护一个不存在的情况。**结果是 `dose` 塌成一行 `return this.entries.get(pill);`，`record` 去掉 `removeIf` 与 `idOrNull`。**

> 这条是本轮**唯一一处"注释明确写错、并且因此产生了整段死代码"**的地方，建议优先修（顺带把注释删掉，别留下一个错误的断言）。

### 3.4 `RealmCommand.chain` —— 手工建 id→holder 索引表只为一次存在性判断【MEDIUM】

```java
// RealmCommand.java:68-82
Reference<RealmStage> target = ResourceArgument.getResource(ctx, "realm", MxtResourceKeys.REALM_STAGE);
Identifier start = HolderHelper.id(target);
Holder<Aura> aura = target.value().aura();
Identifier auraId = HolderHelper.id(aura);
// The ladder in use is the loaded one, so a stage the pack no longer provides breaks the chain here for the
// same reason the server cache refuses to index such a chain.
Map<Identifier, Reference<RealmStage>> stages = new LinkedHashMap<>();
MxtDatapackRegistries.holders(source.getServer().registryAccess(), MxtResourceKeys.REALM_STAGE)
        .forEach(stage -> stages.put(HolderHelper.id(stage), stage));
if (!stages.containsKey(start)) { … return 0; }
```

`target` 来自 `ResourceArgument`（原版按注册表解析），**它必然在册**——所以 `stages` 这张表（一次全表遍历 + 每个条目降一次 id）只为回答一个恒为 `true` 的问题。紧跟其后的 `chain.indexOf(target)` 才是真正的判据（`< 0` 表示不在链上，那才是这里想拒的情况）。

删掉 `stages` 与 `start`，用现有那句 `if (current < 0)` 一并覆盖即可。

### 3.5 由 `isBound()` 派生的整条 `UNBOUND` 机制

`isBound()` 有一个下游后果：它催生了一个"未绑定"失败态，横跨四个模块。

```
ItemBindingService.java:116   if (source != null && !source.isBound()) return PillResolution.unbound(source);
ItemBindingService.java:333   public record PillResolution(…, Optional<Pill> effects, boolean unbound)
ItemBindingService.java:338   public static PillResolution unbound(Holder<Pill> pill)
ItemBindingTooltipAppender.java:63-64   if (resolution.unbound()) → "tooltip.mxt.pill.unbound"
PillService.java:44           UNBOUND,                       ← Failure 枚举成员
PillService.java:58           if (resolution.unbound()) return Optional.of(Failure.UNBOUND);
PillService.java:68           if (!pill.isBound()) return Optional.of(Failure.UNBOUND);
QualityService.java:212       UNBOUND,                       ← 另一个 Failure 枚举成员
QualityService.java:239-240   // nothing instead of reporting UNBOUND, which is the one outcome this state must never produce.
QualityService.java:240       if (bindings.pill().unbound()) return Optional.of(Failure.UNBOUND);
QualityService.java:255       case UNBOUND -> Failure.UNBOUND;
AlchemyFailure.java:13        UNBOUND,
AlchemyWorkstationService.java:490   case UNBOUND -> AlchemyFailure.UNBOUND;
```

**注意 `QualityService.java:239-240` 那条注释**（`which is the one outcome this state must never produce`）——它其实已经把结论说出来了：**这个状态不该存在**。按 §1.4，`pill` 从组件或定义解出来时恒已绑定，所以整条 `UNBOUND` 链没有真实触发路径。

不过这里要**比 §3.2 谨慎**：`UNBOUND` 同时服务两条不同的输入——(a) `holder.isBound()`（不存在），(b) 一个**裸 id 查不到定义**（真实存在，比如丹药定义被移出数据包而堆上还留着 id）。`ItemBindingService.java:117-121` 的 `source == null` 分支就属于 (b)。所以修法是**把 (a) 与 (b) 拆开**，而不是把 `UNBOUND` 整个删掉：`isBound()` 那几处删，`Abilities.resolve(...).isEmpty()` 那种"id 查不到"保留。

### 3.6 `HolderHelper.EMPTY` / `idOrNull` 的哨兵

```java
// HolderHelper.java:10
public static final Identifier EMPTY = Identifier.fromNamespaceAndPath("", "");
// HolderHelper.java:16-22
public static Identifier idOrNull(Holder<?> holder) { return idOptional(holder).orElse(null); }
public static Optional<Identifier> idOptional(Holder<?> holder) {
    return holder.unwrapKey().map(ResourceKey::identifier);
}
```

按 §1.1/§1.4，`unwrapKey()` 对 **`Reference` 恒非空**，所以：

- `EMPTY` 只可能由 `Direct` 触发，而模组没有任何调用点会收到 `Direct`（§1.4 已逐个核过 `SoundEvent` 那几处都不经过 `HolderHelper.id`）；
- 因此 `idOrNull` 的 `null` 分支与 `id` 的 `EMPTY` 分支都是死的。

`EMPTY` 被当成"这个 holder 是坏的"来判的地方：`TechniqueCommand.java:203,210`、`AlchemyFurnaceTooltipAppender.java:92`、`AlchemyFurnaceCasingBlockEntity.java:59`、`AlchemyWorkstationService.java:63`（§2.8）。

`idOrNull` 的调用点：`Contractable.java:56`、`PillUsageAttachment.java:56,64`、`ItemPickerManager.java:98,209,225,253,271,282,290`、`ResourceBarOverlay.java:102`。其中 `ItemPickerManager` 那 6 处是**显示用的备用 id**（`idName(...)`），属于"给不起名字时拿 id 顶上"的 UI 口径，**可以保留**；`PillUsageAttachment` 与 `Contractable` 那几处属于身份比较（§3.3）。

**建议**：要么删掉 `EMPTY`（并在 `SoundEvent` 那条路径上显式处理 `Direct`——目前它并不经过这里，所以可以删），要么把"没有 key"收成一处并写清楚它只服务 `Direct`。**现状是 `EMPTY` 有 4 处把它当错误判据，而全仓没有一条路径能产生它。**

**第二个哨兵，而且这个真的会撞**：

```java
// AuraService.java:43
private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
// AuraService.java:477-479 —— Resolved.id() 在 holder 为空时返回它
public Identifier id() { return this.holder.map(HolderHelper::id).orElse(EMPTY); }
```

`AuraService.java:101` 拿这个哨兵去做**真实的注册表查询**（`MxtDatapackRegistries.get(..., AURA_ZONE, resolved.source())`）。也就是说：**只要某个数据包把 `mxt:empty` 定义成一个灵气区域，它就会被当成"没有区域"的兜底值命中。** 这是本轮唯一一处"哨兵是一个合法 id 且被喂给真查表"的地方。

**修法**：把"没有区域"表达成 `Optional.empty()`（`Resolved.holder()` 本来就是 `Optional`，`AuraResult` 加一个 `Optional<Holder<AuraZone>>` 即可，见 §2.1），两个哨兵常量都删掉；或至少让 `HolderHelper.id` 在无 key 时**大声失败**而不是返回一个合法 id。

### 3.7 其余"不可能发生的 Optional"与失效的一致性检查【MEDIUM】

**（a）`unwrapKey().orElse(...)` 兜底永远不会走。**

```java
// util/DefinitionText.java:18-20 / :34-36
public static String key(Holder<?> holder) { return holder.unwrapKey().map(DefinitionText::key).orElse("?"); }
public static String key(Holder<?> holder, String suffix) { return holder.unwrapKey().map(key -> key(key, suffix)).orElse("?"); }
```

同类：`data/IconReference.java:54`（`.orElse("?")`）、`data/forging/ForgingBindingTooltipAppender.java:81`（`blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?")`）。

```java
// runtime/talisman/TalismanProjectileEntity.java:129 / runtime/spirit/SpiritBurstEntity.java:144
Identifier id = definition.unwrapKey().map(ResourceKey::identifier)
        .orElseThrow(() -> new IllegalArgumentException("A thrown talisman must be a registry holder"));
```

`orElseThrow` 的异常**不可达**。改成收 `Holder.Reference<Talisman>` / `Holder.Reference<Aura>`（或直接 `ResourceKey<T>`）后用 `getKey().identifier()`。

> **注意**：这两处**后面**的查表（`TalismanProjectileEntity.java:137`、`SpiritBurstEntity.java:153`）**不是问题**——那里的 id 是 NBT / 同步实体数据字段，重新解析是必要的。

**（b）`idOrNull` 的 null 一半是死的。**

```java
// data/ItemPickerManager.java:209-210
Identifier id = HolderHelper.idOrNull(holder);
if (id == null || !filter.test(id)) continue;
```

`id == null` 不可能（holder 来自 `:192` 的注册表流）；`filter.test(id)` 那一半是合法的（过滤谓词的 API 就是 id）。**删掉 null 判断**。

**（c）`util/chain/Chain.indexOf` 的注释、实现、与 `ChainCache` 三方不一致。**

```java
// util/chain/Chain.java:44-47
// -1 when the entry is not on this line. Compared by id because a holder is not stable across reloads.
public int indexOf(Holder<T> node) {
    for (int index = 0; index < this.nodes.size(); index++)
        if (this.nodes.get(index) == node)          // ← 注释说按 id，实现是身份比较
```

而 `ChainCache` 用 `Holder` 当 map 键（`equals`，`ChainCache.java:17-25`）。于是**同一个 holder 的等值实例能通过 `chainOf`，却得到 `rankOf == -1` / `contains == false`**。注释里的理由（"不稳定"）也已被 §1.3 证伪。

**修法**：统一到 `equals`（即 `this.nodes.get(index).equals(node)`）并改注释；或统一到身份比较，但那样 `ChainCache` 也得跟着改——按前提应该选 `equals`。

**（d）`Costs` 的重复检测用 `Set<Identifier>`。**

```java
// data/cost/Costs.java:29-35
Set<Identifier> resources = new HashSet<>();
Set<Identifier> auras = new HashSet<>();
…
if (cost instanceof ResourceCost resource && !resources.add(resource.id())) …
if (cost instanceof AuraCost aura && !auras.add(HolderHelper.id(aura.aura()))) …
```

两个分支手里都有 holder（`ResourceCost.resource()` / `AuraCost.aura()`），且 `CostPayment` 早就用 `Holder` 键装 aura（`CostPayment.java:226`）。改 `Set<Holder<Resource>>` / `Set<Holder<Aura>>` 即可（`ResourceCost.id()` 可只在账户那一侧留用）。

---

## 4. 比较：该用 `equals` / `is`，而不是比 id 或比 value

### 4.1 `id(a).equals(id(b))` —— 两边都是 holder【MEDIUM】

```
InSecretRealmEntityCondition.java:51   HolderHelper.id(holder).equals(HolderHelper.id(candidate))
CurseFilter.java:45                    !HolderHelper.id(held).equals(HolderHelper.id(this.curse.get()))
BoundBeastsAttachment.java:70          HolderHelper.id(left).equals(HolderHelper.id(right))
CultivationModeService.java:92         HolderHelper.id(running).equals(HolderHelper.id(action))
ProgressionAdminService.java:33        HolderHelper.id(current).equals(HolderHelper.id(level))
```

全部可以直接 `a.equals(b)` 或 `a.is(b)`。

**其中 `BoundBeastsAttachment.java:70` 值得单独说**：它是"两个契约类型是不是同一个"的判据，而契约类型之间不存在跨注册表问题，所以按 id 比只是慢。更要紧的是它的注释把这个误解写成了理由：

```java
// runtime/creature/BoundBeastsAttachment.java:67-71（注释大意）
// a holder from another registry instance is not the same object even when it names the same definition
static boolean sameType(Holder<ContractType> left, Holder<ContractType> right) {
    return HolderHelper.id(left).equals(HolderHelper.id(right));
}
```

"另一个 registry 实例给出的 holder 不是同一个对象"是对的，"所以要用 id 比"是错的——`Reference.equals` 比 key（§1.2），而 key 是 interned 的（§1.3）。整段塌成 `return left.is(right);`。

### 4.2 `id(a).equals(idVar)` —— 一边 holder 一边裸 id【MEDIUM】

```
CurseCommand.java:169        HolderHelper.id(curse).equals(id)
MxtKubeJsApi.java:236        HolderHelper.id(curse).equals(id)
MxtKubeJsApi.java:256        Elements.of(entity).stream().anyMatch(element -> HolderHelper.id(element).equals(id))
MxtKubeJsApi.java:426        spirit.learnedTechniques().stream().anyMatch(technique -> HolderHelper.id(technique).equals(id))
MxtKubeJsApi.java:508,516    HolderHelper.id(root).equals(id) / HolderHelper.id(physique).equals(id)
CultivatingEntityCondition.java:33   HolderHelper.id(running).equals(asked)
ArtifactService.java:232     abilities(access, stack).stream().anyMatch(ref -> HolderHelper.id(ref).equals(abilityId))
ProgressionDamageMultiplier.java:29  anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability))
WheelSources.java:118        abilities(entity, source).stream().anyMatch(ability -> HolderHelper.id(ability).equals(id))
AbilityService.java:279      channelled.is(HolderHelper.id(ability))     ← 本该 channelled.is(ability)
```

`AbilityService.java:279` 这类尤其没道理：`Holder.is(Identifier)` 与 `Holder.is(Holder)` 都是**一次比较**，先用 `HolderHelper.id` 转成 id 再比只是多走一道 `unwrapKey` + `Optional` + 重建 `Identifier`。

`data/condition/builtin/entity/CultivatingEntityCondition.java:31` 的注释把这个误解写死了：

```java
// Asked by id: the stored holder and a freshly decoded one are not the same instance.
Identifier asked = HolderHelper.id(this.action.get());
return spirit.cultivation().map(running -> HolderHelper.id(running).equals(asked)).orElse(false);
```

`the stored holder and a freshly decoded one are not the same instance` 是对的（实例确实不同），但**结论下反了**：`Reference.equals` 比的是 `ResourceKey`（§1.2），实例不同一样相等。这一段（含注释）应删成 `spirit.cultivation().map(running -> running.is(this.action.get())).orElse(false)`。

这一类**不能一律改**：`id` / `asked` / `ability` 这些变量的来源决定了正确答案。

- `MxtKubeJsApi` / `CurseCommand` 的 `id` 来自**脚本或命令参数**（KubeJS 传 `Identifier`，命令用 `ResourceArgument` 但这里走的是裸 id 分支）——**由 holder 侧反向适配 id 是当前形状下唯一可行的**，不过更省的做法是 `holder.is(id)`（`Reference.is(Identifier)` 是 `key().identifier().equals(id)`，一次比较，不用先 `unwrapKey` 再建 Optional）。
- `CultivatingEntityCondition.java:32-33` 已在上面单列：两边都是 `Holder<Cultivation>` ⇒ 应为 `this.action.get().equals(running)`。

- `ProgressionDamageMultiplier.java:29` / `ArtifactService.java:232` / `WheelSources.java:118` 的 `ability` / `abilityId` 是**调用方从 holder 降下来的**（例如 `AbilityService.java:470` `ProgressionDamageMultiplier.of(actor, HolderHelper.id(ability))`），所以修法在上游：把参数类型换成 `Holder<Ability>`，这三处比较自然变成 `equals`。

### 4.3 `value() == value()` —— 比注册表值而不是比 holder【MEDIUM】

```
RegistryCodecs.java:40        value.map(entry -> entry.value() == candidate.value(), candidate::is)
RegistryCodecs.java:49        if (value.left().get().value() == candidate) return true;
HasTechniqueEntityCondition.java:52   known.stream().anyMatch(learned -> learned.value() == entry.value())
HoldService.java:138          component.sound().value() == SILENT_SOUND.value() || component.sound().value() == hold.holdSound(...).value()
Contractable.java:60          allowed.get().stream().anyMatch(holder -> holder.value() == context.self().getType())
ProjectileDamageCondition.java:35     this.projectile.map(holder -> holder.value() == entity.getType()).orElse(true)
BlockAuraService.java:89      anyMatch(candidate -> candidate.value() == block)
AuraService.java:444          holders(...).filter(holder -> holder.value() == zone).findFirst()
```

**`RegistryCodecs.java:39-41` 是这一类的样板，而且一行里两种写法并存**：

```java
public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Holder<T> candidate) {
    return values.stream().anyMatch(value -> value.map(entry -> entry.value() == candidate.value(), candidate::is));
}
```

左分支（`Holder`）比 `value()` 引用，右分支（`TagKey`）走 `candidate::is`——**同一个方法里对同一件事用了两套判据**。左分支应为 `candidate::is`，也就是整行 `value.map(entry -> entry.is(candidate), candidate::is)`。

`HasTechniqueEntityCondition.java:52` 同样一行两种写法：

```java
return asked.map(entry -> known.stream().anyMatch(learned -> learned.value() == entry.value()),
                 tag -> known.stream().anyMatch(learned -> learned.is(tag)));
```

`known.contains(entry)` 或 `learned.is(entry)` 即可，与右边的 tag 分支对称。

**`HoldService.java:138` 需要单独判断，不能照改**：`component.sound()` 是 `Holder<SoundEvent>`，来源是 `SoundEvent.CODEC`——**允许内联**（§1.4），所以它可能是 `Holder.Direct`。而 `Direct.is(Holder)` 是 `this.value.equals(holder.value())`（`Holder.java:85-87`）而 `Reference.is(Direct)` 是 `holder.is(this.key())` → `Direct.is(ResourceKey)` → **`false`**（`Holder.java:70-72`）。`SoundEvent` 没有覆写 `equals`，所以 `Direct.is(Reference)` 退化成引用比较。

结论：这一处**写成 `.is()` 或 `.value() ==` 都不完全对**——内联音效每次解码都是新实例，任何"相等"判断都命中不了。这里的语义需求其实是"这个 `Consumable` 是不是我们写的"，而代码已经用另一个判据在回答它（`!component.hasConsumeParticles() && component.onConsumeEffects().isEmpty()`），音效那两个比较是冗余保险。**建议保留行为但把注释写清楚**，不要顺手改成 `.is()` 以为更干净。

`Contractable.java:60` / `ProjectileDamageCondition.java:35` / `BlockAuraService.java:89` 是 `Holder<EntityType<?>>` / `Holder<Block>` 与一个值比：注册表值在同一个 registry 实例内是单例，所以 `==` 目前是对的，但它表达的是"是不是同一个条目"，用 `holder.is(BuiltInRegistries.X.getKey(value))` 更贴合原意。属于 LOW。

### 4.4 跨注册表比 id 是一个真风险（本仓已经踩过）

按 id 比较在**同一注册表内**等价于按 holder 比较，但**跨注册表不等价**——两个注册表可以有同 id 的条目。本仓已经有专门为此而生的设计：

```java
// ProgressionHolder.java:14-22
/**
 * How far one body has climbed the progressions of one sort of owner, keyed by the owner's own holder rather than by
 * its id: the sort is part of the address, so a technique and a creature profile that share an id are two owners with
 * a slot each, and each sort is kept by the attachment of its own module.
 */
public final class ProgressionHolder<T extends ProgressionOwner> extends AbstractChangeTrackable {
    private final Map<Holder<T>, Holder<Progression>> levels;
```

也就是说"按 id 会撞"这件事**已经被承认并修过一次**（`research/85`：键从裸 `Identifier` 改成 `Holder<T>`）。但 `research/85` 的表格里自己也留了一条尾巴：

> `EntitySources.held(Entity, Identifier)` 在 id 撞名时仍模糊（命令 / 脚本的裸 id 入口），未加跨注册表唯一性检查，正是本设计要免掉的东西。

所以**改造方向是明确的、且已在仓库内定过案**：内存里一律 holder 键，只在序列化/线缆/脚本边界降 id。§2、§3 那些点是这套口径还没铺到的地方。

### 4.5 身份比较（`==` / `!=`）与身份键的表【MEDIUM】

**这一类比 `value() ==` 更隐蔽，而且它踩的是同一件事的另一面。** `Holder.equals` 是**特意**写成能吃 `DeferredHolder` 的——`Holder.java:288-298` 那两句 Neo 注释（`// Neo: Add DeferredHolder-compatible hashCode()/equals() overrides`）就是为了让 `Reference.equals(DeferredHolder)` 成立：

```java
public int hashCode() { return key().hashCode(); }
public boolean equals(Object obj) {
    return obj instanceof Holder<?> h && h.kind() == Kind.REFERENCE && h.getKey() == this.key();
}
```

而 `==` **只在"同一个对象"时成立**。于是：

| 写法 | `Reference` vs 同一个 `Reference` | `Reference` vs 等值的另一个 `Reference` | `Reference` vs 等值的 `DeferredHolder` |
|------|-----------------------------------|----------------------------------------|----------------------------------------|
| `equals` / `is` | ✅ | ✅（比 key，§1.2） | ✅（Neo 特意保证） |
| `==` | ✅ | ❌ | ❌ |

模组自己的定义走 `RegistryFixedCodec`，两侧通常都是注册表发出来的同一份 `Reference`，所以 `==` **今天多半能过**——但这是"碰巧成立"，不是契约：一个 `DeferredHolder`、一次注册表实例更替、或任何"从 id 重建一个等值 holder"的路径就会让比较静默变成 `false`。而失败方式是**静默的**（校验不再报错 / 缓存不再命中 / 分支不再进入），不会有异常提醒。

**（a）holder 之间的 `==` / `!=`**

| 位置 | 现状 | 问题 | 修法 |
|------|------|------|------|
| `Chain#indexOf` | `this.nodes.get(index) == node`（注释还写着 "Compared by id"，与实现不符） | 与 `ChainCache`（按 `Holder` 建键、走 `equals`）**判据不一致** ⇒ `chainOf` 命中而 `rankOf == -1`/`contains == false` | `this.nodes.get(index).equals(node)`，并改注释 |
| `ChainWalker`（`newIdentitySet()`） | `visited` 是 `Collections.newSetFromMap(new IdentityHashMap<>())`（`AGENTS.md` §4 把它当既定口径写下来了："walker 用 `IdentityHashMap` 比引用"） | 同一条链上的两套判据（walker 身份、cache equals） | 改 `LinkedHashSet`（`Holder.equals` 正确）；**`AGENTS.md` 那一句要一起改**，否则下一个读文档的人会照抄身份比较 |
| `DatapackCache#validateRegistry` 的 AURA 调用点 | `validateRegistry(access.lookupOrThrow(AURA), Aura::resource, (a, b) -> a != b)`，`a`/`b` 是 `Holder<Resource>` | 这是**重复检测**：`if (!predicate.test(...)) LOGGER.error(...)`。真出现"两份灵气档案声明同一个 resource"时，`a != b` 会因为不是同一实例而返回 `true`（当成没问题）⇒ **数据包错误静默漏报** | `(a, b) -> !a.equals(b)`（或 `!a.is(b)`） |
| `AuraQueryCache#cachePools` / `#pools` | `zones.computeIfAbsent(location, ignored -> new IdentityHashMap<>()).put(zone, pools)`，读侧 `byZone.get(zone)` —— 键是 `Holder<AuraZone>` | 身份键 ⇒ 等值但不同实例的 holder 永远 miss（只损失性能、不返回错值），且与它旁边 `zones` 用的 `HashMap`+record 键不一致 | `new HashMap<>()` |
| `AuraQueryCache#HOLDER` | `Map<ServerLevel, Map<AuraZone, Optional<Holder<AuraZone>>>>` 用 `IdentityHashMap`，键是 **`AuraZone` record**（record 自带 `equals`） | 用身份语义覆盖了 record 自己的相等语义；配合 `findHolder` 的 `holder.value() == zone` 一整套都是"值身份" | 该表整体在 §2.1 删除；若要留，改 `HashMap` |
| `PillUsageAttachment#record` | `removeIf(key -> key != pill && key.is(id))` | 同一个键的 `!=` 与 `.is(id)` 混用 | 见 §3.3（整段删） |

**（b）"身份就是 id"的类型被 `==` 比较**

| 类型 | 现状 | 问题 | 修法 |
|------|------|------|------|
| `ContractBehavior` | 类型自带了按 id 的 `equals`/`hashCode`（`ContractBehavior.java:28-34`），但 `ContractBehaviorService` / `ContractEventBridge` 用 `behavior == ContractBehaviors.RECALL` / `== …FOLLOW` | **类型自己声明"id 是身份"，调用点却用身份比较**。今天成立只因为 `ContractAttachment#behavior()` 经 `ContractBehaviors.byId` 拿注册实例、且 `register` 是 `putIfAbsent`（内置四条先注册）。任何"从 id/payload 新建一个等值对象"的路径都会静默失效 | 改用 `equals`（`behavior.equals(ContractBehaviors.RECALL)`）；类型已经把契约写好了，照它走 |
| `WheelSource` / `WheelEntryKind` | 两个接口都有 `Identifier id()`，但**都没有 `equals`/`hashCode`**；调用点一律 `==`（`WheelMenuContent`、`WheelSelectionState`、`WheelContent`、`WheelSources`、`WheelSourceTypes`、`WheelConfigurationScreen`） | 它们是**给附属模组开放的扩展点**、按 id 注册、first-wins。因为没定义 `equals`，身份比较是唯一可行的——但这就把"页面能不能切过去"绑在了"注册表恰好返回同一个实例"上 | 给两个接口加按 `id()` 的 `equals`/`hashCode`（或让调用点比 `id()`），再把 `==` 换掉。改完 `WheelSourceTypes.CONTRACT` 之类的常量比较就不再依赖加载顺序 |

**（c）看着像、其实不是**

| 位置 | 为什么不是 |
|------|-----------|
| `ProgressionHolder#equals` / `DataStorageHolder#equals` / `ContractBehavior#equals` / `MessageChannel` 内的 `this == other` | 这是 `equals` 实现里的**快路径**，标准写法 |
| `AuraService#findHolder` 的 `zone == EMPTY_ZONE` | 与私有哨兵实例比身份，是有意的 |
| `PassiveAttributeService#modifierHolders` 的 `holders.contains(instance)` | `holders` 是 `List<AttributeInstance>`（原版对象），不是 holder |
| `BlockAuraService` 的 `Reference2ObjectMap<Block, …>` | 键是原版注册表**值**（`BuiltInRegistries` 里的单例，不被重建），不是 holder |
| `runtime/economy/PlayerTradeService`、`TriggerDispatcher#previous != subscription`、`AuraChunkTicker` 的 `IdentityHashMap<ServerLevel, …>` 等 | 比的是实体/会话对象，或按 `ServerLevel` 单例建键，与 holder 无关 |
| `screen/menu/StationMenu` 的 `this == SYSTEM_OWNER` | 哨兵实例，有意 |

> **落地顺序建议**：`DatapackCache` 那一处（`a != b`）最该先改——它是唯一一处**因为身份比较而漏报数据包错误**的地方；`Chain#indexOf` + `ChainWalker` 次之（它们是同一条链上的两套判据，且 `AGENTS.md` 需要同步）；其余是防御性对齐。

---

## 5. 建议的统一形状

1. **内存里一律用 `Holder` 当身份与键。** 记录字段、Map 键、Set 元素、参数类型都收 `Holder<T>`；`equals` / `is` 直接比。
2. **只在三个边界降成 `Identifier`**：① 数据包 JSON 与存档/网络载荷的字段；② 命令参数与 KubeJS 脚本入参；③ 显示名（且优先走 `DefinitionText.name(holder)`，它在 `Holder` 上是现成的，见 `DefinitionText.java:18-24,54-66`）。
3. **要序列化的 holder 键 map 用 `CollectionCodecs.map(holderCodec, valueCodec)`**（`CollectionCodecs.java:25-27`）。这不是新机制——`PillUsageAttachment.entries`、`CurseHolderAttachment.instances/sources`、`ProgressionHolder.levels` 已经这么做了。**没有序列化障碍**（`TolerantMapCodec` 编码成字符串键的对象，`RegistryFileCodec.encode` 在 registry ops 下走 id 字符串分支，`RegistryFileCodec.java:41-42`）。
4. **不要再用 `isBound()` 当"定义还在不在"的判据**（§1.5 已证伪它做不到）。若确实要这个语义，只有"拿当前 access 按 key 查一次"这一条路，且应该集中成一个方法，不要散落 11 处（§3.2）。
5. **重估 `HolderHelper.EMPTY` / `idOrNull`**（§3.6）：要么删掉哨兵，要么把它收成一处并写清只服务 `Direct`；现在它有 4 处当错误判据用，却没有触发路径。
6. **`HolderHelper.id(...)` 本身留着**——边界上确实需要它（命令回显、日志、翻译键、脚本载荷、`AlchemyResolver` 那种要落 JSON 的形状）。要治的是**在内存里来回穿越边界**，不是这个函数。

---

## 6. 不是问题的地方（避免误伤）

下面这些看着像模式一，但**输入本来就只是 id，手里从来没有 holder**，或者跨的是必须用 id 的边界。**不要改**：

| 位置 | 为什么不是问题 |
|------|--------------|
| 命令类的裸 id 参数（`TechniqueCommand` 的 `ResourceArgument` 分支、`CurseCommand:169`、`MxtKubeJsApi` 全部） | 参数就是 `Identifier`，KubeJS 脚本只能给 id |
| `MxtDatapackRegistries.holder(access, key, id)` 自身 | 它是边界函数，签名就该收 id |
| 网络载荷（`network/**`） | 线缆上按 id 传是常规做法；`AuraStateS2CPayload.java:24` 同一个 record 里 `source` 是 id 而 `actual` 是 `Map<Holder<Aura>, AuraPool>`，形状不统一但两边各自成立 |
| `Cost` 管线（`ResourceCostDraft:83`、`TalismanWorkstationService:380`、`WheelTooltips:52`） | `CostPayment.resources()` 的契约就是 `Map<Identifier, Double>`（数值账户按资源 id 记账），id 是它的原生货币　**【2026-10-15 部分变更】**账户本身已改成 holder 键（§2.12）；`resources()` 这个**对外契约**仍是 id 键（事件 / KubeJS / 结果），但它是 `account()` 的副本，`reprice` 是写回的唯一通道 |
| `SourceLedger` 的 **source 值**（`mxt:grant/...` 字符串） | 协议规定由 `runtime/Sources` 生成，是存档事实（`AGENTS.md` 明确"改枚举只改引用、别改字符串"） |
| `AbilityAttachment.java:29-33` + `DataStorageHolder` 的 id 键、`Abilities.resolve(...)` | 类注释 `:20-26` 写明是**故意**保留查不到的 id，好让"定义已删除的授予仍能撤销"。理由成立，且 id 是数据包 JSON 字段的直系产物。`research/85` 把进度改成 holder 键是因为那里有"同 id 跨注册表"的真问题，这里没有　**【2026-10-15：此条已作废】**两处都改成了 holder 键；`Abilities.resolve` 仍在，但调用点退回到真正的 id 边界（KubeJS 入参、action/condition 的 JSON 字段、实体 NBT） |
| `AbilityStorage*Condition` / `AbilityStorageReading` / `ModifyAbilityStorageAction` 的 id | 它们是 action/condition 的 JSON 字段 |
| `ProgressionEntityCondition.java:39`（`owner.contains(held.id())`） | 一边是 JSON 里的 id 列表，另一边是 `EntitySources.Owner.id()` |
| `ContractEntityCondition.java:57` / `ContractBellComponent` | `ContractBehavior` 是代码侧对象（按 id 认身份），不是注册表 holder |
| `ProgressionService.java:91`（`ProgressionMastery` 存 id） | 它是命令/KubeJS 面向的 DTO，没有任何地方再查回来（`ContractCommand.java:140` 只打印、`MxtKubeJsProgressionBindings.java:37` 交给脚本）⇒ 脚本 API 要保持 id 型就可留 |
| `ContractBehavior`（`ContractBehaviors.REGISTERED`） | 它是代码注册表（类 + 装载类），不是 datapack registry，id 是唯一身份 |
| `SecretRealmGeneration` / 维度 stem / 群系 id | 原版注册表且客户端拿不到，`RegistryCodecs.matchesKey(..., Identifier)` 是有意为客户端准备的（`RegistryCodecs.java:71-75` 的注释） |
| `MxtCriteriaTriggers`（`IdentifierCriterionTrigger`） | 进度条件载荷要在 advancement JSON 里写、要能比较，vanilla 惯例就是 id（`:223` 的 `trigger(player, id)` 因此合法） |
| `TriggerCooldownAttachment.cooldowns`（`Object2LongMap<Identifier>`） | 冷却按触发规则 id 记账，规则可能已被移出数据包而冷却还要留着——与 §2.10 的 `ForgingSession` 同类，属设计选择　**【2026-10-15 已改】**按用户要求改成 `Holder<TriggerRule>` 键（`TriggerRule.CODEC`，JSON 形状不变）：规则被移出数据包时那一行在解码时丢掉，而那条规则本身也不再存在，所以没有"冷却还要留着"的情形 |
| `ItemPickerManager` 的 6 处 `idOrNull` 显示备用 | UI 层"给不起名字时拿 id 顶上"，不是身份比较 |
| `Aura.java:51` / `RealmStage.java:124` 的 `toString()` | 调试文本，用 id 是对的 |

---

## 7. 附带发现

### 7.1 哨兵 id 会漏进渲染文本【LOW，但前提一旦松动就是玩家可见的 `:`】

同 §3.6 的 `HolderHelper.EMPTY`（`Identifier.fromNamespaceAndPath("", "")`），它被当**显示文本**用的地方：

```
data/aura/Aura.java:51                          this.firstRealm.map(HolderHelper::id).orElse(HolderHelper.EMPTY)
data/artifact/ArtifactDescription.java:57
data/forging/ForgingBindingTooltipAppender.java:48-49,59
data/item/ItemBindingTooltipAppender.java:73,77
data/cultivation/SpiritRootTooltipAppender.java:42
data/cultivation/PhysiqueTooltipAppender.java:41
```

按前提这些都不会发生；但如果哪天前提松动（收到了 `Direct`），提示框里会直接印出 `:`。建议在这些位置改用 `holder.key().identifier()`（拿到的是 `ResourceKey`，类型上就不可能空）或 `idOrNull` + 显式 null 分支。

### 7.2 中文注释

`DatapackCache.java:129` 有中文注释，违反 `AGENTS.md` §1.1（Java 注释一律英文）：

```java
// 遍历数据包内全部Technique（功法）定义
provider.lookupOrThrow(MxtResourceKeys.TECHNIQUE).listElements().forEach(holder -> {
```

全仓 `src/main/java` 里带 CJK 的行共 7 处，其余 6 处是**专有名词或路径**（`MxtDatapackRegistries.java:48` 的 `` `docs/数据包格式.md` ``、`SpiritHerbGrowthService.java:46` 的"药龄"、`InformationPanelScreen.java` 的 4 处界面术语"习得功法/人物信息/基本信息"），夹在英文句子里当术语用，**可以保留**；第 129 行是唯一一句纯中文句子。

---

## 8. 覆盖范围与未做

**已逐行核过（本审计的主要证据来源）**：

- 语义基线：`Holder` / `ResourceKey` / `MappedRegistry` / `RegistryDataLoader` / `RegistryFileCodec` / `SoundEvent`（原版源码）
- 注册表管道：`registry/MxtDatapackRegistries.java`、`util/HolderHelper.java`、`util/codec/RegistryCodecs.java`、`util/codec/CollectionCodecs.java`、`util/codec/TolerantMapCodec.java`、`util/DefinitionText.java`、`util/SourceLedger.java`
- 灵气：`runtime/world/AuraService.java`、`AuraResult.java`、`AuraQueryCache.java`、`runtime/aura/AuraLookup.java`、`command/server/AuraCommand.java`、`AuraZoneEventBridge` / `AuraZoneRenderer` / `ResourceBarOverlay`（消费点）
- 品质/丹药/丹药绑定：`runtime/item/QualityService.java`、`PillService.java`、`ItemBindingService.java`、`attachment/PillUsageAttachment.java`、`data/ItemPickerManager.java`、`data/ItemBindingTooltipAppender.java`
- 灵植：`data/alchemy/SpiritHerb.java`、`runtime/alchemy/SpiritHerbService.java`
- 炼丹：`runtime/alchemy/AlchemyResolver.java`、`AlchemyWorkstationService.java`、`data/alchemy/AlchemyRecipe.java`
- 功法/进度/境界：`runtime/cultivation/CultivationService.java`、`CultivationModeService.java`、`Elements.java`、`data/condition/builtin/entity/CultivatingEntityCondition.java`、`HasTechniqueEntityCondition.java`、`runtime/progression/ProgressionAdminService.java`、`ProgressionDamageMultiplier.java`、`data/progression/ProgressionHolder.java`、`runtime/DatapackCache.java`、`command/server/TechniqueCommand.java`、`RealmCommand.java`
- 附件：`CurseHolderAttachment.java`、`AbilityAttachment.java`、`FlightAttachment.java`、`ContractAttachment.java`、`SecretRealmTravelAttachment.java`、`TriggerCooldownAttachment.java`
- 飞行/契约/轮盘：`runtime/artifact/FlightService.java`、`FlightEventBridge.java`、`runtime/creature/ContractGrantService.java`、`BoundBeastsAttachment.java`、`runtime/wheel/WheelSources.java`、`screen/wheel/content/WheelContent.java`
- 锻造：`runtime/forging/ForgingService.java`、`ForgingBindingService.java`、`ForgingWorkstationService.java`、`ForgingPlan.java`、`ForgingSession.java`、`ForgingTableState.java`
- 战利品/条件/效果：`loot/HasAbilityLootCondition.java`、`GrantAbilityLootFunction.java`、`runtime/hold/HoldService.java`、`data/action/builtin/entity/ApplyEffectAction.java`、`data/condition/builtin/entity/MobEffectCondition.java`
- 世界/秘境：`runtime/world/SecretRealmService.java`、`SecretRealmRegistry.java`

**本轮的三个覆盖层**（同一套判据，三遍独立执行后交叉核对）：

1. **主审**（语义基线与上表 §2.1–§2.16、§3.1–§3.6、§4.5）：`Holder` / `ResourceKey` / `MappedRegistry` / `RegistryDataLoader` / `RegistryFileCodec` / `SoundEvent` 的原版源码，加上注册表管道、灵气、品质/丹药、灵植、炼丹、功法/进度/境界、附件、飞行/契约/轮盘、锻造、战利品、秘境。
   §4.5（身份比较）另做了一次专项扫描：全仓 `==` / `!=` 与 `holder` 同现、`getKey() ==` / `key() ==`、以及去掉 null/数字/布尔噪声后的全部 `[=!]=` 行；`IdentityHashMap` / `Reference2*Map` 的全部出现处；再逐个回读确认哪些 operand 真的是 holder。判据是 `Holder.java:288-298`（Neo 为 `DeferredHolder` 补的 equals/hashCode）。
2. **`data/**` + `util/**` + `attachment/**` + `registry/**` 全量扫描**（约 500 个文件）：先枚举这些目录里所有 `HolderHelper.id|idOptional|idOrNull|EMPTY`、`isBound()`、`unwrapKey()`、`MxtDatapackRegistries.holder|get|holders`、`listElements()`、`registry.get(`、`getResourceKey`、`getKey(`、`wrapAsHolder`、`Holder.direct`、`.value() ==` 命中，再逐个读上下文。**`registry/**` 无任何命中**（那里只声明 codec 与 key）。结论与主审一致，并补出 §2.12（消耗草稿回退）、§3.7(a)(b)(d)。
3. **`runtime/**` 全量扫描**（221 个文件）：按 `HolderHelper.*`（100 处）、`MxtDatapackRegistries.holder/get/holders`（59 处）、`isBound|unwrapKey|key().identifier()|EMPTY|idOrNull|idOptional`、`listElements()` / `value() ==` / `findFirst` 扫描、`Abilities.resolve(`、`ResourceKey.create|registryAccess().get|lookupOrThrow(...).get(` 逐条枚举后读上下文与调用方。补出 §2.13（`CreatureProfileService`）、§2.14（`CultivationMethodService`）、§2.15（群系）、§3.7(c)（`Chain.indexOf`）。

**未逐行覆盖**（只做过模式 grep，未逐个读文件）：

- `data/**` 里没有命中上述任何模式的约 400 个文件（主要是 `data/action/builtin/**` 的方块/物品/双实体动作及其 meta 动作、`data/condition/builtin/{block,bientity,item,damage}/**`、`data/resourcebar/builtin/**`、`data/secretrealm/builtin/**`、`data/timeline/**`、`data/trigger/builtin/**`、`data/talisman/type/**`、`data/storage/**`）
- `runtime/**` 里没有命中的约 130 个文件（`economy/**`、`energy/**`、`friend/**`、`perch/**`、`lightning/**`、`rift/**`、`sword/**`、`tribulation/**`、`hold/**` 大部、`world/Soul*`、`damage/{DamageCalculationService,DamageEventBridge}`、`talisman/*` 大部、`trigger/*` 大部、`ability/{AbilityActivationService,PassiveAttributeService,AbilityModifierService,AbilityFeedback}` 等）
- `screen/**`（84）、`compat/**`（78）、`render/**`（37）、`item/**`（45）、`event/**`（19）
- `network/**`：只核过携带身份字段的那几个 payload（见 §6）

**未做**：任何编译、任何实机验证（本轮只读）。所以"死分支 / 不可达"的判定依据是：§1 的原版源码事实 **+** 各处的 codec 链已逐个核对（`RegistryFixedCodec` / `RegistryCodecs.holderOrTag` / `MxtDataComponents` 里每个组件用的 codec）。§5 的改造如果动手，需要 `./gradlew compileJava compileTestModJava`，并按 `AGENTS.md` §1.6 评估是否要留一份设计稿（跨模块重构要写）。

---

## 9. 修复优先级建议

| 顺序 | 目标 | 理由 | 影响面 |
|------|------|------|--------|
| 1 | `DatapackCache#validateRegistry` 的 `(a, b) -> a != b`（§4.5a） | **一行**，而且是唯一一处**因为身份比较而漏报数据包错误**的地方 | 1 行 |
| 2 | `PillUsageAttachment.dose` / `record`（§3.3） | 唯一一处**注释写错并因此产生整段死代码**；改动小、纯删除 | 1 文件 |
| 3 | `AuraCostDraft` / `ResourceCostDraft` 的账户键（§2.12） | 唯一一处**有正确性风险**：回退现场可能漏记，`reset()` 静默失效 | 2 文件（+`CostPayment` 键类型） |
| 4 | `QualityService.intrinsic` + `:81`（§2.3） | 两个模式挤在 8 行；塌成一行 | 1 文件 + 调用点 |
| 5 | `isBound()` 清单里**运行期**的 11 处（§3.2） | 纯删死分支；但要按时机筛，别碰解码期那 3 处 | 9 文件 |
| 6 | `CreatureProfileService`（§2.13） | 三趟 id 往返，且 `apply` 的成功是假的 | 1–2 文件 |
| 7 | `AuraService` / `AuraResult` / `AuraQueryCache`（§2.1、§2.15） | 收益最大（八项基础设施一起消失）；爆炸半径含 6 个下游与一个网络 payload | 约 8 文件 |
| 8 | `Chain#indexOf` + `ChainWalker` 身份集 + `ChainCache` 对齐（§3.7c、§4.5a） | 是一条**真实的一致性裂缝**（`chainOf` 命中而 `rankOf == -1`）；`AGENTS.md` 要同步改 | 2 文件 + 文档 |
| 9 | 其余身份比较与身份键（§4.5a/b：`AuraQueryCache` 的 `IdentityHashMap`、`ContractBehavior`、`WheelSource`/`WheelEntryKind`） | 防御性对齐，失败方式都是静默的 | 约 8 文件 |
| 10 | §2.16 那 6 个独立小点 + `AuraLookup` 的 `Holder<Resource>` 重载（§2.2）、`Elements.of`（§2.4）、`FlightAttachment.vehicle`（§2.7）、`SecretRealmService.enter`（§2.5）、`CultivationMethodService`（§2.14） | 各自独立的小改，删掉的是运行期/每 tick 的查表 | 各 1–2 文件 |
| 11 | 比较改 `equals`/`is`（§4.1–§4.3） | 面广但每处都短；`RegistryCodecs:40` 与 `HasTechniqueEntityCondition:52` 是"一行两种写法"的样板，建议先修这两个定调 | 约 20 文件 |
| 12 | 炼丹 id 工作货币（§2.6）、锻造 `deltas`/去重表（§2.10）、`WheelSources.carriers`（§2.9） | 需要动 record/codec 形状，可能破坏数据包或存档；建议单独一轮并配文档 | 约 10 文件 |
| 13 | `HolderHelper.EMPTY` / `AuraService.EMPTY` 收口（§3.6、§7.1）、`UNBOUND` 机制拆分（§3.5）、`unwrapKey().orElse("?")` 类（§3.7a）、`Costs` 的 `Set<Identifier>`（§3.7d） | 是前面几步的下游清理，放在最后一起做 | 约 20 文件 |

**建议第 1–6 步合成一轮**（都是"删死代码 / 改一行"，不改任何序列化形状，风险最低），第 7–10 步合成第二轮，第 12 步单独一轮（会动数据包/存档形状，需要文档同步）。

> **【2026-10-15 全部落地】**这张优先级表已经走完，逐条勾选记录在 [`Holder修复清单.md`](Holder修复清单.md)（六个批次全清）。两处与上表不同：第 12 步（炼丹 §2.6 与锻造 §2.10）实际**没有动 JSON/存档形状**（holder 键与 id 键都编成同一个 id 键对象，见 §2.6 末尾的更正），第 13 步的 `Costs` 重复检测（§3.7d）在第 1 批就顺手做了。
