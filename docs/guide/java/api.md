---
title: Java 公开 API
---

# Java 公开 API

这页列的是**别的模组可以直接调用的入口**。数据包能表达的东西（定义、条件、行为、消耗）不需要 Java；只有当你要在代码里问"这个坐标的灵气是多少""替他执行这个技能""这件物品算不算这条定义"时，才用得上这里的东西。

三件事先说清楚：

- **这些入口留在各自的模块包里**（`registry`、`util`、`runtime/*`、`data`），只有**要实现**的接口在 `com.iafenvoy.mxt.api`，见[特殊公开接口](interfaces)。
- **服务端权威**：扣费、修炼、技能、伤害、阵法结算都只在服务端做，客户端只渲染与发请求。哪些入口在客户端会失效，集中在[服务端与客户端的边界](#服务端与客户端的边界)。
- 想知道某条线**为什么**长这样（而不是"有哪些方法"），看[数据包格式](../../数据包格式.md)的对应小节、[好友与敌我识别](../play/friends.md)与[网络协议与服务端权威](network)。

## 先按"要做什么"找入口

| 你要做的事 | 入口 | 在哪 |
| --- | --- | --- |
| 读一张数据包注册表里的定义 | [`MxtDatapackRegistries`](#mxtdatapackregistries) | `registry` |
| 把定义翻成显示名 | [`DefinitionText`](#definitiontext) | `util` |
| 拼一行提示框数值 | [`TooltipText`](#tooltiptext) | `util` |
| 求一个数据包数值 | [`NumberProvider`](#numberprovider) | `util.formula` |
| 判断"这件物品算不算这条定义" | [`ItemMatcher`](#itemmatcher) | `util.matcher` |
| 把注册表里的定义变成创造栏物品 | [`CreativeTabHelper`](#creativetabhelper) | `data` |
| 问一堆物品是哪一档、让自己的定义参与定档 | [`QualityService` / `QualityProvider`](#qualityservice-与-qualityprovider) | `runtime.item` / `api` |
| 问某个坐标有多少灵气 | [`AuraService`](#auraservice) | `runtime.world` |
| 读写实体身上的一条数值 | [`ResourceService`](#resourceservice) | `runtime.resource` |
| 加修炼进度、突破、设置境界 | [`CultivationService`](#cultivationservice) | `runtime.cultivation` |
| 读写一个生物的寿元 | [`LifeSpanService`](#lifespanservice) | `runtime.cultivation` |
| 执行一个技能、受理一次按键 | [`AbilityService`](#abilityservice) | `runtime.ability` |
| 自己造成一次伤害 | [`DamageCalculationService`](#damagecalculationservice) | `runtime.damage` |
| 给阵法加一种功能模块 | [`FormationActionType`](#formationactiontype-与-mxtformationactiontypes) | `data.formation` |
| 问"这一下会不会被阵法拦下" | [`FormationProtection`](#formationprotection) | `runtime.formation` |
| 问"它算不算我的人" | [`FriendService`](#friendservice) | `runtime.friend` |
| 让一个方块给丹炉供热 | [`AlchemyHeatSource`](interfaces) | `api` |
| 算一件物品的货币价值 | [`CurrencyValueService`](#currencyvalueservice) | `runtime.economy` |

## 数据定义怎么读

### `MxtDatapackRegistries`

包 `com.iafenvoy.mxt.registry`。31 张原生数据包注册表的声明与统一读取入口，`/reload` 重建与客户端同步都交给原版注册表系统，这个类**不持有任何快照**。另有 9 张**数据表**（NeoForge Registry Data Map，物品键 7 张 + 方块键 2 张）在 `MxtDataMaps`。读法就是条目自己的 `getData`：调用点直接写 `stack.getData(MxtDataMaps.X)` / `state.getData(MxtDataMaps.X)`（`ItemStack` 与 `BlockState` 自己实现 `IWithData`，`TypedInstanceExtension` 把它转给条目自己的 holder），没有别的包装方法。

按 id / holder 取值（读服务端注册表）：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `get(ResourceKey<? extends Registry<T>> key, Identifier id)` | 按 id 取定义值 | 条目不存在给 `Optional.empty()` |
| `holder(key, Identifier id)` | 按 id 取 holder | 读的是**服务端**注册表 |
| `holders(key)` | 遍历整表的全部 holder | |

显式传入注册表访问器（**客户端用这几个**）：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `holder(HolderLookup.Provider access, key, Identifier id)` | 客户端按 id 取 holder 的**唯一正确入口** | 不碰 `ServerLifecycleHooks` |
| `holders(Provider access, key)` / `holders(RegistryAccess access, key)` | 遍历整表 | |
| `get(Provider access, key, Identifier id)` | 取值 | 同上 |

**这一层不过滤任何东西**：条目在不在注册表里就是全部答案，而"停用一条定义"是加载期的事（`neoforge:conditions`，见 [`docs/数据包格式.md`](../../数据包格式)），被挡掉的条目根本不在表里。所以读了 holder 就能直接动用，没有"读到了一条被关掉的定义"这种状态。

标签查询：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `isTagged(key, Identifier id, Identifier tagId)` / `isTagged(key, Holder<T> holder, Identifier tagId)` | 在不在某个标签里 | 条目不存在返回 `false` |

裸查与规模：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `holderOrEmpty(key, Identifier id)` | 与 `holder(key, id)` 同义，但**没有服务端时不抛**，给空 | 给同时在客户端跑的读者用（例如 `CurseService.definitionState`） |
| `registry(key)` | 拿到原版 `Registry<T>` 本体 | 没有运行中的服务端时抛 `IllegalStateException` |
| `size(key)` | 该表的条目数 | 与 `registry(...)` 同一条服务端断言 |
| `registries()` | 本类登记过的全部注册表 key | |

要点：

- 类里**不缓存注册表实例**。要缓存就按**注册表实例**开键（`/reload` 不换实例，世界加载才换），参考 `DamageElements`。
- 附件里存着、而当前包已经不提供的 `Holder`（条目被 `neoforge:conditions` 挡掉，或直接删了文件）**在两次世界加载之间不会自己消失**：附件只在**世界加载**时解码，那一刻缺失的那一条会被容错列表丢掉、重新进一次世界就干净了；而 `/reload` 不重解附件。所以要问"现在还在不在"就按 id 回查注册表（`Elements.of` / `activeSpiritRoots` / `SecretRealmService.enter` 都是这么做的），别拿手里这枚 holder 当它还存在的证据。
- `newDatapackRegistries(NewRegistry)` 由启动事件调用，**不是给业务代码用的**；表与 codec 的登记在 `MxtResourceKeys`。

### `DefinitionText`

包 `com.iafenvoy.mxt.util`。把定义翻成显示名，键是 `<category>.<注册表命名空间>.<定义命名空间>.<路径>`，其中 `category` 就是注册表 id 的 path。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `name(Holder<?> holder, String category)` | 取显示名 | **本体内部推荐入口**：定义实现了 `NamedDefinition` 就优先读它自带的 `name`，否则按 holder 的 key 推键 |
| `name(Holder<?> holder)` / `name(ResourceKey<?> key)` | 同上 | category 从注册表推 / 直接用 key |
| `name(Identifier id, String category)` | 手里只有一个裸 id 时 | **硬假定 `mxt` 命名空间**，非 `mxt` 的定义别用这个重载 |
| `category(Identifier registry)` | 取某个注册表的 category | 就是它的 path |
| `key(ResourceKey<?>)` / `key(Identifier registry, Identifier id)` | 由条目 / 注册表+定义生成文本键 | |
| `key(String category, String registryNamespace, Identifier id)` | 底层拼接 | **拼定义键的唯一出口**，`ContextNameCodec` 的默认值也走它 |
| `defaultText(String category, ResourceKey<?> entry, String suffix)` | 定义没写 `name` / `description` 时的回退文本 | **回退文本的唯一出口**；`suffix` 传 `""` 是名字、传 `".description"` 是描述 |
| `resolved(Component text)` | 这段文本是"真文本"还是"没被翻译的裸键" | 读客户端语言文件，属显示层判断 |

要点：**别在别处再拼一套名字键**；键没被翻译时渲染出来就是键本身（不是空串），`resolved(...)` 就是用来判这一点的。

### `TooltipText`

包 `com.iafenvoy.mxt.util`。提示框里数值与一行的拼法，纯计算、无状态。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `number(double value)` | 最多两位小数、去掉多余的 0 | 用 `Locale.ROOT`，不受语言环境影响：`3.0` → `3`、`3.50` → `3.5`、`3.456` → `3.46` |
| `signed(double value)` | 带符号的数值 | 非负一律加 `+`（`0` → `+0`） |
| `join(List<? extends Component> parts)` / `join(Component... parts)` | 把若干片段拼成**一行** | 片段之间插的是翻译键 `TooltipText.SEPARATOR`（`tooltip.mxt.separator`），不是字面标点 |

要点：列表标点各语言不同，**别在别处硬编码「、」或「, 」**——凡是 join 过的行都必须经过这里。

### `NumberProvider`

包 `com.iafenvoy.mxt.util.formula`。一个"数值"的抽象：JSON 里的常数、exp4j 表达式字符串，或 `mxt:number_provider_type` 里的一种结构化 provider。

| 成员 | 作用 | 备注 |
| --- | --- | --- |
| `double evaluate(FormulaContext context)` | 求值 | 实现必须自己用 `assertFinite` 兜住非有限结果 |
| `MapCodec<? extends NumberProvider> codec()` | 该实现的 `MapCodec` | 新实现要同时在 `mxt:number_provider_type` 里登记，否则带类型的 codec 认不出 |
| `default boolean assertFinite(double value)` | 校验运行期结果是否有限 | 非有限时报一条含 `using 0` 的诊断并返回 `false`；**它本身不改数值**，调用方负责给兜底值 |
| `NumberProvider.CODEC` | 数据包统一入口 | 按 `double` → 表达式字符串 → `{type:...}` 的顺序尝试解码；编码侧固定写回 `{type:...}` |
| `NumberProvider.TYPED_CODEC` | 按 `"type"` 分派 | |
| `NumberProvider.FINITE_DOUBLE_CODEC` | 拒绝非有限 double | 加载期校验用 |

内建类型（`mxt:number_provider_type`）：`constant`（默认）、`expression`、`context_variable`、`sum`、`uniform`、`binomial`、`weighted_list`、`conditional`，以及交给脚本的 `js`。

要点：一个字段只写一种形状即可（数字 / 表达式字符串 / `{type:...}`），**别为某个字段另造数值格式**——`Cost` 之外的一切数值都走它。

### `ItemMatcher`

包 `com.iafenvoy.mxt.util.matcher`。"哪条定义适用于这件物品"的匹配器：`entries()` 里**任意一项命中**即命中（或语义，不是且）。

| 成员 | 作用 | 备注 |
| --- | --- | --- |
| `List<Entry> entries()` | 匹配项列表 | 实现必须给全 |
| `int priority()` | 排序权重 | **没有默认实现**：每个实现返回自己在 JSON 里声明的 `priority`；没有那个字段的匹配器返回 `DEFAULT_PRIORITY` |
| `find(Registry<T> registry, ItemStack stack)` / `find(Stream<T> matchers, ItemStack stack)` | 第一个命中 | 无命中给 `Optional.empty()` |
| `find(Stream<H> entries, Function<H, T> unwrap, ItemStack stack)` | 同上，但把 Holder 原样返回 | 调用方要留住 Holder 才能用时走这条（`ArtifactService`、`ItemAuraService`），`unwrap` 写 `Holder::value` |
| `findAll(...)`（注册表版与流版） | 全部命中 | 按 `ItemMatcher.ORDER`（`priority` **降序**）排序 |
| `DEFAULT_PRIORITY` | 定义没写 `priority` 时的值（`0`） | 三个物品条件、消耗 `mxt:item` 与框架内置的两个长按声明没有这个字段，也用它；**档位不按 Entry 类型分** |
| `ORDER` | 唯一的排序比较器（`priority` 降序） | 要自己合并多个来源、不能走 `find` 的调用方（`HoldLookup`）按它排，别各写一份 |
| `ENTRIES_CODEC` | entry 列表的 Codec | 同时认单个对象与数组两种写法 |

`ItemMatcher.Entry` 的成员：`matches(ItemStack)`、`default boolean itemLevel()`、`codec()`，以及两个 codec 常量 `SHORTCUT_CODEC`（简写：裸物品 id → `item`、物品标签 → `tag`）与 `CODEC`（先试简写，再试带 `type` 的对象）。**Entry 不带优先级**：谁赢由定义自己的 `priority` 决定。

Entry 种类（`mxt:item_matcher_entry_type`，默认 `item`）：`item`、`tag`、`wildcard`、`regex`；运行期的模块另外注册了 `spirit_storage`、`herb_tag`、`technique`、`quality`、`ingredient`（最后一个把整份原版材料当条目，是材料侧要求进 `ItemMatcher` 的通道）。

要点：

- `find` 的"第一个"是 `priority` **数值最大**的那个定义（与 `aura_zone`、`element_reaction` 同一个方向），不是注册顺序，也不是"匹配得最具体"的那个；`priority` 相同的才取决于传入流的顺序。
- `priority` 是**十张定义表自己的字段**（`artifact`、`item`/`weapon`/`pill`/`tool`/`blueprint`/`technique` 六种 binding、`spirit_herb`、`item_aura`、`currency`，默认 `0`，加载期不校验范围），所以"通用定义 + 特地点名定义"共存时由数据包写死谁先；点名的条目**不会**因此更靠前。`technique_binding` 的 `items` 是可选的，但 `priority` 一样有。`ArtifactHold` 直接回读它那件法器的字段；消耗 `mxt:item`、三个物品条件与两个框架内置的长按声明（功法阅读、灌注）没有这个字段，恒为 `DEFAULT_PRIORITY`。
- **两类表读同一个匹配器，分工不同**：binding 表（`item` / `weapon` / `pill`）把本模组的规则接到一件**已经存在**的物品上，内容表（`artifact`、`spirit_herb`、`item_aura`、`currency`）本身就是被物品选中的定义。匹配器只回答"哪一条适用"，不决定那条定义是什么，也不替它执行。
- **`itemLevel()` 是缓存安全的分界线**：它返回 `true` 表示"命中与否只由物品本身决定"，按物品开缓存的调用方**只能**缓存这类项；会读堆上的组件 / NBT 的项，以及答案来自另一条定义的项（`mxt:herb_tag` 问的是哪条 `spirit_herb` 认领这件物品）必须每个堆都问一次。
- 简写只覆盖 `item` 与 `tag`；其它实现走简写编码会抛 `IllegalArgumentException`。

## 物品目录与创造栏

### `CreativeTabHelper`

包 `com.iafenvoy.mxt.data`。**读**那条「注册表 → 可选项」目录的入口：`/picker` 界面与别的模组都走它。每一项是一行 `PickerItem(stack, names)`：`stack` 是要画的堆，`names` 是这一行能被哪些名字搜到。它只读——不往物品上写东西，也不判定谁能拿。

每条查询都要求自己传注册表访问器（`HolderLookup.Provider`，`BuildCreativeModeTabContentsEvent.getParameters().holders()` 就是它），所以客户端用客户端已同步的表、服务端用服务端的表，不存在"读到另一侧"的问题。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `itemsOf(Provider, key)` | 一张表的全部行 | `key` 是注册表 key；**没登记成分类的表给空列表**，不抛 |
| `itemsOf(Provider, key, Predicate<Holder<?>>)` | 按定义筛行 | 谓词拿到的是定义的 holder，按 id 筛与按定义本体筛是同一个入口，所以"模组 id"就是取 holder 的命名空间 |
| `itemsOfMod(Provider, key, String)` | 一张表里属于某个命名空间的行 | `mxt:aura` 的条目属于 `mxt`，`mymod:aura/…` 属于 `mymod`；物品与方块两张表里定义就是物品，于是命名空间是物品的 |
| `itemsOfMod(Provider, String)` | 跨全部分类，挑出某个命名空间的行 | 给自己做一个"我模组的东西"创造栏用它 |
| `stacksOf(...)` / `stacksOfMod(...)` | 上面四条的堆视图 | 见下 |

要点：

- **给创造栏的是 `stacksOf` / `stacksOfMod`**：它们按**原版创造栏自己的规则**（`ItemStackLinkedSet`，同物品同组件算同一件）去掉重复，而且**每一份都是拷贝**——改它不会碰到选择器里的那一行。去重不是装饰：`mxt:aura` 的每一行都是同一颗灵石当替身，把行原样塞进创造栏，原版的 `accept` 会当场抛"这个堆已经在栏里了"。行视图（`itemsOf*`）一个定义一行，不去重。
- **原版创造栏的 `accept` 还要求 `count == 1`**，本体的行都是 1；自己注册的行也请写 1。
- 查询无副作用、双端可调：它只读你传进来的那份访问器，不碰服务端单例，也不写任何附件。
- 想把这批行画成一张选择器页（而不是进创造栏），用 `ItemPickerScreen.over(title, stacks)`，见[界面与屏幕](screens)。

**目录本身在别的类里**：哪张注册表对应哪些行、每行怎么造，是 `com.iafenvoy.mxt.screen.picker.ItemPickerManager`——`categories()` / `category(Identifier)` 列分类，`registerSingle` / `register` 登记一条分类（`mxt:` 的定义由本体登记好了）。`CreativeTabHelper` 只负责读它，两个类一眼能分开。有一条限制要知道：**一张注册表只认第一次注册的目录**，对已经登记过的 key 再注册一次是**静默无效**的（分类列表也只列一次），所以要换掉一条分类的造行方式只能改那一处注册。

```java
// 自己的创造栏：让这个模组贡献过的定义（以及它自己的物品）全进来
@Override
public void buildContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() != MY_TAB) return;
    for (ItemStack stack : CreativeTabHelper.stacksOfMod(event.getParameters().holders(), "mymod"))
        event.accept(stack);
}
```

## 物品品质

### `QualityService` 与 `QualityProvider`

包 `com.iafenvoy.mxt.runtime.item`（服务类）与 `com.iafenvoy.mxt.api`（接口）。**"这一堆物品是哪一档"只有一个解析顺序**，只在 `QualityService.find` 一处，按**三层**往下取：堆上的 `mxt:quality` 组件 → **这一堆携带的定义**自己声明的档 → 数据表 `mxt:default_quality`。本模组今天有九个定义走第 2 层。字段级写法见[数据包格式](../../数据包格式.md#quality)。

`api/QualityProvider` 是给**定义类型**用的契约：一个定义类型实现它，就是在说"我的物品起步的那一档归我自己报"。

```java
public interface QualityProvider {
    Optional<Holder<ItemQuality>> defaultQuality();
}
```

**实现它本身什么都不改**——一类定义要靠 `QualityService.carry` **登记一个载体组件**才算进了第 2 层：

| 重载 | 用在 |
| --- | --- |
| `carry(Supplier<? extends DataComponentType<Holder<T>>> type)` | 组件的值**就是**那份定义，直接读 `Holder<T>` |
| `carry(Supplier<? extends DataComponentType<C>> type, Function<C, Optional<Holder<T>>> extract)` | 组件是个 record，定义包在里面，由 `extract` 取出来 |

`T extends QualityProvider`：给一个没实现这个接口的定义类型登记载体**编译不过**。

要点：

- **注册顺序只有一条**：本模组自己的载体在 `QualityService` 类加载时的静态块里登记完，所以**一定排在附属模组的载体之前**。一次查询会按登记顺序走完整个列表，先给出答案的那一个赢。
- **这是一份登记，不是"扫一遍堆上碰巧带着的组件"**：两个 provider 同时挂在一堆上时，"谁先被问到"只能由登记顺序说了算——按组件遍历的先后没有任何契约。
- **载体必须只凭这一堆就答得出**：两个重载**都不给它注册表**，因为"这件物品能不能用"那道闸门（`QualityService.check`）手里只有品质注册表。想按 id 回查别的注册表的写法在这里走不通。
- **引用失效就当作没有**：载体取出的 holder 没绑定时（当前包把那条定义删了）这一层答空，解析继续往下走，而不是给出一个死档位。
- **载体只是"被问到"的入口**：真正读的是定义自己的 `defaultQuality()`，所以一个类型实现了接口、又登记了载体之后，它的每一条定义都自动参与第 2 层。

`QualityService` 对外的其余入口：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `find(Provider access, ItemStack stack)` | 这一堆解析出的档 | 三层顺序的唯一实现；空堆给空 |
| `set(stack, quality)` / `clear(stack)` | 写 / 摘掉覆盖组件 | `clear` 之后这一堆退回**它携带的定义**那一层，再看数据表 |
| `hasOverride(stack)` | 堆上**有没有**那个组件 | 与"解析出档没有"是两个问题：没写组件也可能从定义或数据表读到档 |
| `canUse(user, stack)` / `check(user, stack)` | "能不能用"的闸门 | `check` 给出 `Failure` 枚举；绑定条件、档位自己的 `condition`、丹药次数与冷却都在这里 |
| `modifier(...)` | 取品质的三个修正之一 | 是乘数；缺失、非有限或 ≤ 0 一律按 `DEFAULT_MODIFIER`（`1.0`） |
| `displayName(quality)` | 品质这一档自己的名字，套上它自己的颜色 | **列一个档位时的统一写法**（就是 `coloredName(quality, DefinitionText.name(quality))`）；`/quality get\|set\|upgrade`、灵根/体质列表与画符结算都走它 |
| `coloredName(quality, text)` | 给一段**别的**文本套上这一档的 `color` | 品质没写颜色就原样返回 |
| `ordered()` / `ordered(Provider access)` | 按 `tooltip_order` 标签排好的全部档 | 客户端用带 `Provider` 的那个重载 |

存单堆覆盖用 `set`，别自己往组件里写；被拒绝的使用想给玩家同一句提示就调 `notifyCannotUse`（它是公开的，好让排在闸门自己的优先级之下、仍要看这次拒绝的交互也能报出来）。

## 灵气与资源

这两件事一起讲，因为最容易混：**灵气（`aura`）是身份，资源（`resource`）是数值**。字段怎么写见[数据包格式](../../数据包格式.md#aura)的 `aura` 与 `resource` 两节。

### `AuraService`

包 `com.iafenvoy.mxt.runtime.world`。"某坐标的灵气是多少"的**唯一解析器**：biome → dimension → 自定义区域 → 阵法覆写四层依次求解，再把环境池与该区块的可变存量合成最终答案。

查询（双端都能调）：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `getPositionAura(Level level, BlockPos pos)` | **坐标最终灵气**：环境池 + 该区块存量 + 周围方块的距离衰减贡献 + 阵法的 `max_bonus` 上限加成 | `/mxt aura query` 就是这个口径；内部会取 / 初始化该区块的灵气附件 |
| `getSensedAura(Level level, BlockPos pos)` | **环境浓度**：只算环境，**刻意不含区块存量** | 资源条的 `mxt:environment_concentration` 是这个口径；查不到区域定义时给空池，其余字段照带 |

写区块存量：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `consume(Level level, BlockPos pos, Map<Holder<Aura>, Double> costs)` | 从该区块存量扣一笔灵气，返回扣成功没有 | **全有或全无**：任一项非有限、为负、或存量不足都返回 `false`，并且一点不扣 |
| `change(Level level, BlockPos pos, Map<Holder<Aura>, Double> amounts)` | 直接增减该区块存量（可为负） | 返回 `void`，**失败没有任何信号**（任一值非有限就整笔静默放弃）；**缺失的灵气永远不会被隐式创建** |
| `initialize(AuraChunkAttachment chunk, Resolved resolved, BlockPos pos)` | 把一层环境池写进区块存量并打上 template 标记 | 服务端写路径 |

结果类型：`AuraResult`（一张 `Holder<Aura>` → `AuraPool(amount, maximum, regenPerTick, supplied)` 的表，外加规则、来源与 `SourceKind`）与 `Resolved(holder, definition, kind, maxBonus)`（一层解析结果，`id()` 在没有 holder 时给 `mxt:empty`）。

要点：

- **"坐标最终灵气"与"环境浓度"是两个入口**：前者含区块存量，后者不含。两者的池子都**按 `Holder<Aura>` 分开**，而 `aura` 与 `resource` 一对一，所以**没有"给我某个 `resource` 的池子"这种入口**。
- **真正能花的账面是区块存量**，只有 `consume` / `change` 能改；`getPositionAura` 给的是合成视图（里面还混着方块贡献）。
- **一次查询是"本刻快照"**：结果按 `(坐标, 游戏刻)` 记忆、整表在每个 tick 末尾丢弃（`AuraQueryCache`），所以 `consume` / `change` 之后在**同一刻**再查，读到的仍是改动前的值（新激活的阵法同理，下一 tick 才看得到）——要立刻拿到新值，就用这次调用的返回值自己记账。
- `supplied` 单独记"这份量里有多少来自脚下的方块发射器"，因为允许花掉脚下地形的消费者（比如抽取环境的阵法）必须先扣掉自己这一份，否则重复计账。
- **优先级是后者胜**：biome < dimension < 自定义区域 < 阵法覆写。dimension 绑定是"替换"biome，不是被 biome 压过——直接读那句英文注释的顺序容易读反。
- 阵法覆写只在有区域 holder 且是服务端时发 `AuraZoneEvent.Override`，被取消就保留低优先层；没有 holder 的覆写静默穿透。
- 客户端两个查询都能调，但 **dimension 标签的匹配可能和服务端不同**（dimension stem 是不同步给客户端的注册表），别把客户端结果当权威。

### `ResourceService`

包 `com.iafenvoy.mxt.runtime.resource`。把 `resource` 定义的边界（`min` / `max`）一致地施加到**初始化、变更、被动恢复**三条路径上。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `initialize(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context)` | 按 `default_value` 落户并钳到边界 | **已有值就不动**（`changed = false`）；边界解不出或默认值非有限 → `invalid` |
| `change(ResourceHolderAttachment holder, Holder<Resource> resource, double amount, FormulaContext context)` | 增减这条数值 | 越界是**钳制**而不是拒绝；内部先 `initialize`（首次访问自动落户）；结果与当前值相同就返回 `unchanged` |
| `regenerate(ResourceHolderAttachment holder, Holder<Resource> resource, NumberProvider regen, long elapsedTicks, FormulaContext context)` | 被动恢复：`regen × elapsedTicks` 之后走 `change` | **`elapsedTicks < 0` 抛 `IllegalArgumentException`**——这个类里唯一抛异常的地方 |
| `formulaContext(CultivationAttachment spirit, Holder<Resource> resource, FormulaContext base)` | 给公式挂上这条数值的上下文 | **只读友好**：修炼附件由调用方自己传（读出来用 `getExistingData`） |
| `formulaContext(LivingEntity entity, Holder<Resource> resource, FormulaContext base)` | 从实体取修炼状态再挂 | **会创建附件**（内部走 `getData(MxtAttachments.CULTIVATION)`），只读场景别用这个重载 |
| `resolveBounds(Resource definition, FormulaContext context)` | 求 `min` / `max` | 任一非有限或 `min > max` → `Optional.empty()`；要自己做只读检查就用它，别另写一套钳制 |
| `realmRank(CultivationAttachment spirit, Holder<Aura> aura)` | 该实体在**这条 aura 链**上的境界序号 | 凡人给该链的首境界序号；链上没有首境界、或当前阶段不在链上给 `-1` |

结果类型：`Result(valid, changed, value)`——**失败时 `value` 是 `NaN`，必须先看 `valid()` 再看数**；以及 `Bounds(min, max)`。

要点：

- 三个写入方法**只改传进来的那个附件**，自己不取、也不创建附件；唯一会隐式创建附件的是收 `LivingEntity` 的 `formulaContext(...)`。
- 按 id 的重载（`initialize(holder, id, context)`、`change(holder, id, amount, context)` 与一对 `formulaContext(..., id, base)`）每次都**自己按 id 去注册表取定义**，所以条目不在当前包里时会跳过；id 解析不到时**静默失败**：写入给 `invalid`，两个 `formulaContext` 原样返回 `base`。
- 这个类和 `AuraService` 都**没有自己的服务端检查**，是否只在服务端调用靠调用点自律。

## 修炼与境界

### `CultivationService`

包 `com.iafenvoy.mxt.runtime.cultivation`。纯静态工具类，修炼这条线的算术都在这。

**突破（唯一入口）**：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, Holder<Aura> aura, FormulaContext context, BooleanSupplier conditionsMet)` | 尝试突破到下一境界 | 这是本类里**唯一做服务端限定**的方法：客户端直接得到 `Failure.SERVER_ONLY`，不写日志 |
| `attempt(..., Identifier auraId, ...)` | 同上，灵气用 id 给出 | 解析不出灵气时给 `NO_NEXT_REALM` |

**进度与小境界**：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `addProgress(LivingEntity, Holder<Aura>, double amount, FormulaContext)` | 加修为进度 | 返回**实际接受量**；非法 amount 或没有下一境界给 `0.0` |
| `addProgressForChain(...)` | 同上，可传实体或 `CultivationAttachment` | 两个重载 |
| `remainingProgressForChain(CultivationAttachment, Holder<Aura>, FormulaContext)` | 还差多少到下一境界 | |
| `minorStage(Holder<Aura>, CultivationAttachment, FormulaContext)` / `minorStage(RealmStage, double progress, FormulaContext)` | 当前小境界（**从 0 起**） | 分段算术只有这一处，内部有重入守卫；没有 `minor_stages` 或数值非法给 `NaN` |

**只读与管理**：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `breakthroughStatusForChain(LivingEntity, Holder<Aura>, FormulaContext)` | 突破状态（到了没、条件满足没、是不是自动、进度区间） | 自动突破 tick 与信息面板共用同一个答案 |
| `pendingConditionsForChain(LivingEntity, Holder<Aura>)` | 还差哪些条件 | |
| `setRealm(CultivationAttachment spirit, Identifier target)` | 直接设置境界（管理员用） | 返回是否真的改了；**它没有实体参数，因此没有服务端检查** |

结果类型：`BreakthroughResult(advanced, failure, failedResource, costs)`、`BreakthroughStatus(reached, conditionsMet, automatic, minimumExperience, maximumExperience)`、`enum Failure {DISABLED, WRONG_AURA, NO_NEXT_REALM, INSUFFICIENT_PROGRESS, CONDITIONS, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CANCELLED, SERVER_ONLY}`。

要点：

- **边界不一致**：只有 `attempt` 挡客户端；`addProgress*` / `setRealm` / `minorStage` 都不检查，在客户端调会真的写附件。
- 内容自己声明的条件在**扣费之前**评估，所以条件不满足不会白花资源。
- `INVALID_FORMULA` 同时兼任"一切非资源不足的扣费失败"的归一化出口；`failedResource` 只在扣费阶段资源不足时非空。
- `Failure.DISABLED` 只有脚本桥会产出（`MxtKubeJsApi.tryBreakthrough` 在灵气 id 解析不到时），`attempt` 自己不产出它。
- `Failure.MAX_PROGRESS` 已于 2026-09-25 **删除**：进度上限不可能成为拒绝突破的理由——`threshold()` 要求某一段的 `max_experience ≥ breakthrough_exp`，所以进度被顶在 `max_experience` 时必然已经满足 `progress ≥ breakthrough_exp`，此时只会因条件、代价或取消而失败。内联的 `Failure` 分支若写了它，编译期就会报错。

### `LifeSpanService`

包 `com.iafenvoy.mxt.runtime.cultivation`。寿元账本（剩余与上限两个数，单位都是刻）的读写与唯一的耗尽出口。数值由数据包给（`realm_stage.lifespan`、`mxt:modify_lifespan`），节奏与后果由服务端配置决定。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `remaining(Entity)` / `total(Entity)` | 读两个数 | **只读，不创建附件**；没有账本答 `-1`（`UNACCOUNTED`） |
| `set(LivingEntity entity, long ticks)` | 两个数一起重写 | `ticks < 0` 拒绝；返回 `Result(changed, failure)` |
| `add(LivingEntity entity, long ticks)` | 正数续命（两个数一起涨）、负数抽寿（只减剩余） | 同上；从未被给过寿元的生物先按配置基数起算 |
| `seed(LivingEntity entity)` | 按配置的「凡人基础寿元」播种 | 开关关着、基数为 0、已经有账本时返回 `false` |
| `settle(Entity entity)` | 结算一次并判定耗尽 | 开关关着、创造 / 旁观、不是生物、没有账本时直接 `false`；**这是唯一会致死的入口** |
| `reincarnate(LivingEntity entity)` | 立刻走一遍转世重置清单（按「转世」页的开关） | 返回 `Result(changed, failure)`：`Pre` 被取消时 `failure` 为 `CANCELLED` 且什么都不做。`/mxt lifespan reincarnate`、`mxt:reincarnate`、KubeJS `MxtLifespan.reincarnate` 与 Java 附属调的是同一个方法；开关关着也照做，**不发**耗尽事件（发 `LifeSpanRebirthEvent`） |
| `display(long remaining, long total, int ticksPerYear)` | 面板与提醒共用的显示组件 | 剩余为负读作「不受限」 |

要点：

- **写入永不致死**：`set` / `add` 只记账，耗尽只由 `settle` 判定，所以扣费事务或技能的中间不会有人当场身死。
- 服务端限定：客户端调用写入得到 `Failure.SERVER_ONLY`，不写日志。
- `Result` 的 `failure` 只有 `SERVER_ONLY` 与 `INVALID_VALUE`。
- 耗尽流程发 `LifeSpanEndEvent.Pre`（可取消；在事件里写下正值＝续命）与 `Post`（带 `outcome()`）；玩家走 `DEATH` 时被转为旁观者，非玩家生物走 `mxt:lifespan` 伤害类型。**`reincarnate` 不走这两个事件**，它发的是 `LifeSpanRebirthEvent.Pre` / `Post`（`Pre` 可取消＝这次转世整件不做），因为那是明确的裁决，不该被"寿元耗尽"的语义套住。

## 技能

### `AbilityService`

包 `com.iafenvoy.mxt.runtime.ability`。纯静态工具类。**技能执行只有一条路**，所有入口都在这里。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `use(Holder<Ability> ability, Entity actor, AbilityAttachment attachment, ResourceHolderAttachment resources, long gameTime, FormulaContext context)` | 施放一个已授予的技能 | `cast_time > 0` 时只登记截止时间，结果是 `casting = true` |
| `useCarried(..., @Nullable Vec3 origin)` | 物品自带的技能 | 不要求已授予（物品即许可）；非瞬发技能给 `CARRIED_NOT_INSTANT` |
| `finishCast(Holder<Ability>, Entity, AbilityAttachment, ResourceHolderAttachment, long gameTime, FormulaContext)` | 引导到点后落地 | 没到点就原样返回引导中的结果 |
| `gate(ToggleContext context)` | 共用闸门：授予 / 冷却 / 条件 / 消耗 | **不执行效果**；由 `AbilityActivationService.activate` 在 `Toggable#gated` 为真时调用 |
| `tickChannel(Holder<Ability>, Entity, AbilityAttachment, ResourceHolderAttachment, long gameTime, FormulaContext)` | 引导每 tick 结算 | 只有服务端实体 tick 桥会调；任何失败都停止引导 |
| `stopChannel(AbilityAttachment)` | 停止引导 | |
| `cancelCast(Holder<Ability>, AbilityAttachment, long gameTime)` | 中断蓄力 | **不退费** |

返回类型：`UseResult(committed, casting, failure, failedResource, amounts)`（**三态**：已提交 / 引导中 / 失败）、`GateResult(approved, failure, failedResource)`、`PrepareResult`（`approved()` 即 `use != null`）、`PreparedUse`、`CommitResult`、`ChannelResult(state, failure, nextTick, amounts)`、`enum State {INACTIVE, WAITING, PULSED, STOPPED}`、`enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY, CARRIED_NOT_INSTANT, NO_TARGET, NOT_APPLICABLE}`。

要点：

- 凡是"按下某个开关"，服务端一律先经 `runtime/ability/AbilityActivationService`——轮盘、命令、KubeJS 与符箓都走它，**别在别处再写一套"按下某个开关"的分派**。实现了 `Toggable` 且 `gated(ctx)` 为真时它才回头调这里的 `gate`。
- **"强制施放一条任何类型的技能"只有 `use` 这一条路**：`AbilityActivationService.activate` 对不实现 `Toggable` 的技能直接 `UNAVAILABLE`，而 `/mxt ability cast`、KubeJS 的施放入口与轮盘按下时对非按键型的回落（`WheelService.press`）都直接调 `use`（`requiresGrant = true`）；物品承载的那条是 `useCarried`（`requiresGrant = false`，`cast_time > 0` 或 ChannelSource 会被 `CARRIED_NOT_INSTANT` 拒）。
- **冷却与消耗全由这条路负责**：`cooldown` 字段自己会写 `mxt:cooldown` 状态，长度也只有这一个来源（旧的可声明的 `ticks` 已随 `components` 删除）。
- 世界动作永不回滚，所以复合技能先 `prepare` 校验、再统一 `commit`。
- **生效时做什么由类型回答**（2026-09-27 重设计起）：这条链在钱付完之后只调 `AbilityEffect.run(definition.type(), actor, context, origin)`（`AbilityEffect` 是额外能力接口，`data/ability/AbilityEffect.java`；另一个静态入口 `runOn(...)` 只对一个目标生效）。实现 `ActionCarrier` 的五个类型（`mxt:active` / `triggered` / `channelled` / `aura` / `interval`）各自带四个动作字段并在自己的时机跑它们，`mxt:word` 直接实现 `AbilityEffect` 跑自己的效果枚举。根接口 `AbilityType` 只剩九个方法且**只处理 active**（`createComponents` / `isActive` / `grant` / `revoke` / `active` / `inactive` / `tick` / `activeTick` / `tickInterval`）；`triggers()` / `rolls(...)` / `damageCondition()` 属于额外接口 `TriggerSource`（只有 `mxt:triggered`），通道的 `channelInterval()` / `upkeepCosts()` 属于 `ChannelSource`（`mxt:channelled`），冷却长度 `cooldown()` 属于 `CooldownSource`（会付款的八个类型；`AbilityService` 的施放管线与共用闸门都从它取长度，取不到按 `0`），“挑一批实体、在它们身上各跑一次另一条技能”属于额外接口 `AbilityApplier`（`data/ability/AbilityApplier.java`：`reach(...)` + `payload()`，只有 `mxt:targeted`；它的 `reach` 在**付款之前**被问一次，问不到目标就以 `NO_TARGET` / `NOT_APPLICABLE` 拒掉整次发动）。类型的 `tick` / `tickInterval` / `active` / `inactive` / `activeTick` 由 `AbilityEventBridge.tickAbilities` 每 tick 那一趟统一驱动，**别在这里再插一套按类分派**。
- `grant` / `revoke` 两个钩子已在 `AbilityType` 上就位，但**运行时尚未回调**（把实体穿到台账的 8 个授予 / 撤销调用点还没做）。
- 这个类**自己不检查客户端**，也从不产生 `Failure.SERVER_ONLY`——客户端那道 `SERVER_ONLY` 是调用方（例如 KubeJS 桥 `MxtKubeJsApi`）自己返回的。直接调 `use` 在客户端会真的动手。
- 所有结果里的 `amounts` / `costs` 只用于展示与记录；**别拿它当回滚依据**。

## 伤害

### `DamageCalculationService`

包 `com.iafenvoy.mxt.runtime.damage`。**这一击的唯一结算管线**：成形在攻击方（`outgoing`），减免在受击方（`incoming`）。两层为什么必须分开、数据包里怎么写元素，见[数据包格式](../../数据包格式.md#element)的 `element` 一节。

**唯一施放入口**：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `deal(@Nullable Entity attacker, Entity target, double amount, Optional<Holder<DamageType>> damageType, @Nullable FormulaContext context)` | 本模组自己造成伤害时**唯一该调的方法**：自己建 `DamageSource`、判 `mxt:no_bonus` 直通、跑第一层、再交给原版 `hurtServer` | 客户端（目标不在 `ServerLevel`）与非有限 / 非正的金额都返回 `0.0D`；`damageType` 参数不可为 `null`，没有类型就传 `Optional.empty()`；返回的是**成形值**、未经原版减免（护甲、无敌帧），它不是"实际扣了多少血" |

**第一层：攻击方成形（纯计算，不落伤害）**：

| 方法 | 作用 |
| --- | --- |
| `outgoing(@Nullable Entity attacker, Entity target, double amount, @Nullable FormulaContext context, Set<Holder<Element>> elements)` | 基础值 × `damage_multiplier` × `element_modifier` × 自冲突 ×（攻击方 `damage_dealt_multiplier` × 这一击对目标灵根的 `overcomes`） |
| `outgoing(...)`（不传 `elements` 的简写） | 没声明伤害类型时，这一击的元素取攻击者自己的灵根元素 |
| `selfConflictMultiplier(@Nullable Entity attacker)` | 主手物品元素与在效灵根 `conflicting_elements` 相冲时的倍率；每个元素**只乘一次**（多少条灵根冲突都不叠乘） |
| `physiqueMultiplier(@Nullable Entity entity, boolean dealt)` | 在效体质的 `damage_dealt_multiplier`（`dealt=true`）/ `damage_taken_multiplier`（`dealt=false`），多条相乘；一律用**持有者自己的**公式上下文 |
| `masteryMultiplier(@Nullable FormulaContext context)` / `elementMultiplier(@Nullable FormulaContext context)` | 读 `damage_multiplier` / `element_modifier`；缺省与非法值按 `1.0`，**写 `0` 是被尊重的**（不是"没写"） |

**第二层：受击方减免（纯计算）与工具**：

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `incoming(LivingEntity target, Set<Holder<Element>> attacking, double amount)` | × 受击方 `adapted_to` × 受击方 `damage_taken_multiplier` | 主重载；**只有 `DamageEventBridge` 在 `LivingIncomingDamageEvent` 里调它一次**，别自己再调一遍（那等于二次减免） |
| `incoming(LivingEntity target, DamageSource source, double amount)` / `incoming(LivingEntity target, @Nullable Entity attacker, double amount)` | 从伤害来源或攻击者反推元素再减免 | 上面那个主重载的两个简写 |
| `adaptationMultiplier(LivingEntity target, Set<Holder<Element>> attacking)` | 受击方的 `adapted_to` | 被克制是攻击方的便宜，受击侧不再加一次 |
| `overcomeMultiplier(Set<Holder<Element>> attacking, Entity target)` | 攻击方元素对目标灵根的 `overcomes` | 每一对匹配都相乘（两个元素都克制就都算） |
| `attachmentMultiplier(LivingEntity target)` | 受击者携带的**法器**的 `attachment_multiplier` 连乘 | 只缩放这一击留下的**元素附着量**，不影响元素反应的效果 |
| `bypasses(DamageSource source)` | 这个伤害类型在不在 `mxt:no_bonus` 里 | 直通＝不乘任何因子、不留元素，但**原版自己的减免照旧**——它是"不归本模组加成口径管"，不是免疫 |
| `source(Level level, @Nullable Entity attacker, Optional<Holder<DamageType>> damageType)` | 造这一击的 `DamageSource` | **归因在这里定**（玩家 → `playerAttack`、生物 → `mobAttack`、都没有 → `generic`）；调用方自己建 source 会丢击杀归属 |

公开常量：`DAMAGE_MULTIPLIER` 与 `ELEMENT_MODIFIER`（公式上下文里的两个变量名）、`NO_BONUS`（`TagKey<DamageType>`，就是 `mxt:no_bonus`）。

### `DamageElements`

包 `com.iafenvoy.mxt.runtime.damage`。回答"**这一击是什么元素**"，并给两层提供**同一次**读取。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `strike(Level level, Optional<Holder<DamageType>> type, @Nullable Entity attacker)` / `strike(DamageSource source)` | 这一击的元素集合 | **管线与伤害条件共用的唯一规则**：伤害类型被元素认领就是那些元素，没人认领就回落到攻击者灵根 |
| `reading(Level level, ...)` / `reading(DamageSource source)` | 元素 + 来源 + 每个元素会留多少附着量 | 三个字段来自**同一次**注册表查询；减免与元素附着必须共用这一次读取，否则两个数不同步 |
| `of(RegistryAccess access, Holder<DamageType> type)` / `of(DamageSource source)` | **谁认领了这个伤害类型**（不含回落） | 没人认领给**空集** |
| `typeOf(RegistryAccess access, Holder<Element> element)` | 取该元素 `damage_types` 里第一条能解析出来的类型 | 标签形式会展开成第一个匹配元素 |
| `resolveType(RegistryAccess access, List<Either<Holder<Element>, TagKey<Element>>> elements, Optional<Holder<DamageType>> damageType)` | 决定一次声明式打击该以什么类型出行 | 空结果 = 保持它原本的读取（攻击者灵根）；声明失败只警告，**绝不让加载失败** |
| `checkDeclaration(RegistryAccess access, ...)` | 校验声明的元素确实认领了这个类型 | **首次使用时检查**，不在加载期（注册表是并行解码的，加载期检查会让同一个包时过时不过） |

类型：`Strike(elements, attachment, origin)`、`Claim(element, attachment)`，以及 `enum Origin {TYPE, ROOTS}`——`attaches()` 说明这一击会不会在目标身上留下附着，**元素反应只由 TYPE 启动**。

要点：

- 一击的元素**必须能从 `DamageSource` 读出来**：减免层手里只有它。
- 缓存按**伤害类型 `Registry` 实例**开键（元素表与伤害类型表在同一步重载，一个键同时察觉两者）；`/reload` 不换实例，所以缓存不会失效也不该失效，上限 4 张注册表。
- 同一个伤害类型被多个元素认领时**每条认领都相乘**，建索引时会打一条警告。

## 阵法

### `FormationActionType` 与 `MxtFormationActionTypes`

阵法的框架（结构、半径、消耗）在 `Formation` 上，"这个阵法干什么"由它的 `actions` 列表决定，而列表里每一项就是一个**功能模块**。

- `FormationActionType`（`com.iafenvoy.mxt.data.formation`）是模块的形状：一个 `codec()`，加一个按 JSON 的 `"type"` 字段分派的 `CODEC`（必须是 `Codec` 而不是 `MapCodec`，因为阵法持有的是模块**列表**）。
- 分派表 `mxt:formation_action_type` 是**固有注册表**（默认项 `none`），经 `NewRegistryEvent` 在代码里静态注册，**不在 `MxtDatapackRegistries` 那 31 张数据包注册表里**。
- 于是：**数据包能自由新增 `mxt:formation` 条目（模块组合与参数），但新增不了模块类型**。多加一种模块 = 一条记录 + 一次 `DeferredRegister` 注册，运行时按记录类型分派，`data` 包因此不碰世界。
- 现在合法 `type` 只有 5 个，都登记在 `MxtFormationActionTypes`：`mxt:none`（`NONE`，也是分派表默认项）、`mxt:attack`（`ATTACK`）、`mxt:buff`（`BUFF`）、`mxt:protection`（`PROTECTION`）、`mxt:range_display`（`RANGE_DISPLAY`）。
- 注册只经 `MxtFormationActionTypes.REGISTRY` 一次，**别在别处再注册一遍，也别另建第二张阵法模块表**。

### `FormationProtection`

包 `com.iafenvoy.mxt.runtime.formation`。"阵法里的护持（ward）禁不禁止这个动作"的**唯一判定点**：破坏、放置、使用、交互、攻击、爆炸、怪物破坏都问同一个问题、拿同一个答案。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `prevented(ServerLevel level, Action action, @Nullable BlockPos target, @Nullable UUID actorId)` | 这个动作在这个位置 / 这个行为者上是否被拦下 | `true` = 禁止。收的是 **UUID** 而不是玩家或实体：服务端审计在没有在线玩家时也要能跑规则；`actorId == null` 是合法的（爆炸、怪物破坏） |
| `covers(ProtectionFormationAction ward, Action action)` | 这个护持的哪个开关管这个动作 | 与数据包字段一对一（`block_break` → `BREAK`，等等） |
| `hasProtection(Formation definition)` | 这份定义里有没有护持模块 | 纯定义查询，不看世界 |
| `delegationHandsOver()` | "委托给领地插件"此刻是否真的让位 | 服务端配置「兼容 → 委派需领地保护」关着，或者领地插件确实在保护 |
| `claimsOnlyRefuses(ServerLevel level, BlockPos controller)` | `claims_only` 模式下这里是否因"没领地"而拒绝 | 没装领地插件时是**失效**（打一条警告）而不是报错——不能因操作员无法满足的原因让阵法放不下去 |
| `foreignClaimRefuses(ServerLevel level, BlockPos controller, @Nullable UUID actorId)` | 护持要立到别人已认领的地上时是否拒绝 | 需要服务端配置「兼容 → 立阵需许可」开着且装了领地插件，否则直接放行；权限本身去问领地插件，**不复刻它的规则**（第二份实现会漂移） |
| `warnIfDelegationFallsBack(Identifier id, Formation definition)` | 激活时提醒"声明说委托给领地插件，其实没有领地保护，自家标志位仍然生效" | 每个定义只提醒一次 |

`FormationProtection.Action` 是调用方与判定点之间的**唯一词汇表**：`BREAK`、`PLACE`、`INTERACT`、`EXPLOSION`、`MOB_GRIEFING`、`ENTITY_INTERACT`、`ATTACK_ENTITY`、`ITEM_USE`。新增一种动作＝这里加值 + `covers` 加分支 + 数据包标志位，三处必须同改。

要点：

- **判定同时看动作两端**：行为者位置**或**目标位置在半径内，这个护持就适用；目标为空时仍可凭行为者位置拒绝。
- **豁免顺序**：行为者为空不豁免；护持**没有主人时谁都不豁免**；行为者就是主人 → 豁免；只有护持声明了 `spare_friends` **且**服务端配置「阵法 → 敌我识别」开着，才会去问 `FriendService.identify(...) == TRUE`；任何好友源都认不出的实体一律被拦。
- 让位成立时这个护持**完全不管**（跳过，不是降级），触发它的有两条独立路径：模块自己声明 `delegate_to_claims`，或者 `claim_linkage == claims_precedence` 且控制点区块已被认领。
- 领地只按**控制点所在的那个区块**问，这样横跨边界的护持不会被两套规则同时管。
- **别在事件订阅者里再写一套护持判定**。

## 敌我识别

### `FriendService`

包 `com.iafenvoy.mxt.runtime.friend`。"**这个是不是我的人**"的唯一提问入口：友伤、护持豁免、AI 判断都走它。机制与名单命令见[好友与敌我识别](../play/friends.md)。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `isFriend(Entity judge, Entity candidate)` | 布尔化的答案 | 只有 `TRUE` 算"是"；`DEFAULT` 与 `FALSE` 都算否 |
| `identify(Entity judge, Entity candidate)` | 三态判定 | 转下面那个 |
| `identify(UUID judgeId, @Nullable Entity judge, Entity candidate)` | 三态判定，**裁判可以离线** | 先发 `FriendEvent.Relation`，没人表态才回落内置好友名单 |
| `builtin(Entity judge, Entity candidate)` / `builtin(UUID judgeId, @Nullable Entity judge, Entity candidate)` | 直接问内置好友名单，**不发事件** | 可以安全地在 `Relation` 监听器里调——这正是"名单 + 我自己追加的"能表达出来的原因 |

要点：

- **不记忆化**：每次调用都会发一次事件，而监听器有权查世界。同一个 tick 里反复问的调用方要自己存答案。
- 内置判定的顺序是：自己永远是自己的人 → 裁判没加载就查 `FriendCache`（离线镜像）→ 裁判不是玩家就是 `FALSE`（只有玩家能有名单）→ 否则读好友附件（用 `getExistingData`，**只读，不创建空附件**）。
- 这个类**自己没有端判定**，事件照发；客户端别指望它给权威答案。

### `FriendEvent`

包 `com.iafenvoy.mxt.event`。好友系统的识别钩子，**发布在 `NeoForge.EVENT_BUS` 上**（不是模组总线），而且实际被发布的是嵌套的 `FriendEvent.Relation`——订阅要点名它。

| 成员 | 作用 | 备注 |
| --- | --- | --- |
| `Relation(UUID judgeId, @Nullable Entity judge, Entity candidate)` / `Relation(Entity judge, Entity candidate)` | 构造 | 公开构造器，外部可以自己发一条 |
| `judgeId()` | 裁判 UUID | **永远有值**——数据存在服务端管理器里的好友源（队伍、阵营）因此在玩家离线时也能作答 |
| `judge()` | 裁判实体 | **离线时为空**，这是设计要求而不是错误 |
| `candidate()` | 被判定者 | |
| `setResult(TriState)` | 写下本监听器的判断 | `TRUE` = 当成我的人；`FALSE` = 不是，**不管名单怎么说**（压过包括玩家自己名单在内的一切来源）；`DEFAULT` = 交回名单 |
| `result()` / `answered()` | 读当前结论 / 是否已经有人表态 | `FriendService` 只在 `answered()` 为真时采信事件 |

要点：

- **它不是可取消事件**：要否决就 `setResult(FALSE)`。
- `result` 是**单个可变字段**：后表态的覆盖先表态的，没有聚合、也没有"只问一次"的守卫。想表达"在名单基础上再追加"，就在监听器里自己调 `FriendService.builtin(...)` 再合并。
- 别在监听器里再 post 一次 `Relation`（会绕回 `identify`）。

## 货币

### `CurrencyValueService`

包 `com.iafenvoy.mxt.runtime.economy`。物品本位货币定义的**服务端读取 API**：只算价值、给兑换报价，**不扣费**——货币不是消耗。格式见 [currency](../../数据包格式.md#currency)。

| 方法 | 作用 | 备注 |
| --- | --- | --- |
| `unitValue(Item item)` / `unitValue(ItemStack stack)` | 单件单价 | 需要当前服务端；取不到给 `OptionalLong.empty()`，不抛异常也不写日志 |
| `unitValue(Provider access, ...)`（含带持有者与 `FormulaContext` 的重载） | 显式注册表 / 带持有者版 | **空堆给 `of(0L)`**；只有带 `FormulaContext` 的那个重载把上下文一路传进 `unavailable_when` 判定与品质倍率 |
| `value(ItemStack stack)` / `value(@Nullable Entity holder, ItemStack stack)` | 整堆价值 = 单价 × 数量 | 单价查不到、或相乘越过 `Long.MAX_VALUE` 一律给 empty，**绝不截断夹取** |
| `totalValue(Collection<ItemStack> stacks)` | 一堆堆的总价值 | 任一堆不是货币、或累加溢出 → empty |
| `exchangeOffers(ItemStack input)`（以及注册表版与带持有者版） | 这个输入能换到哪些报价 | 只取**可用**定义的 `exchanges` 并摊平；没有服务端给空列表 |
| `isExchangeInput(RegistryAccess registryAccess, ItemStack input)` | 这堆能不能放进兑换输入格（**还不看数量够不够**） | 要求存在可用且 `exchanges` 非空的匹配定义 |
| `definition(Provider access, ItemStack stack)` | 只按 matcher 找货币定义 | **不判可用性**——别拿 `isPresent()` 当"能用" |
| `unavailableReason(Provider access, @Nullable Entity holder, ItemStack stack)` | 它为什么不可用 | `holder == null` 时**恒为 empty**（"没有持有者"就问不出原因）；取 `unavailable_when` 里第一条条件为真的条目 |

`ExchangeOffer(output, cost)` 是一条报价：`output()` 返回的是**拷贝**，可以直接改而不污染别的报价。

`CurrencyValue.UnavailableWhen(ItemCondition condition, Component reason)` **不是枚举**，是一个 record——"有哪些取值"由数据包的 `unavailable_when` 列表决定，`reason` 是给玩家看的可翻译文本而不是错误码。

要点：

- **`empty` 与 `0L` 是两种结论**：`empty` = 不是货币 / 没有服务端 / 溢出 / 找不到定义；`0L` = 是货币，但空堆或当前不可用；兑换那边"没有报价"表现为**空列表**。
- **不带持有者的查询会把任何写了 `unavailable_when` 的定义当成不可用**（内部要求那份列表为空）；要按条件判定就传 holder。
- 品质倍率是"物品货币价值遇见品质"的**唯一**地方：乘的是被定价那一堆自己解出的品质的 `value_multiplier`；乘积小于 1、越过 long 上界或不再有限时**保留声明面额**，而不是夹取。

## 服务端与客户端的边界

这一节把散在各处的边界收在一处——上面每个入口只讲它自己的例外。

- **只有服务端会结算**：扣费、修炼进度、突破、技能、伤害、阵法、货币价值都是服务端事实。客户端调用顶多得到"什么都没发生"的返回值。
- **但"客户端安全"不是服务类的默认属性**：本体内部的 KubeJS 桥（`MxtKubeJsApi`）在每个入口自己写 `isClientSide()` 检查并返回 `SERVER_ONLY` / `false` / `0`，且不写日志；而 `AbilityService` 与 `CultivationService`（除 `attempt`）**本身没有这道检查**。写给别的模组调用的新入口时，照 KubeJS 桥的做法在**边界**上挡一次。
- **不要在渲染线程查服务端注册表**：`MxtDatapackRegistries` 里凡是不带 `Provider` / `RegistryAccess` 的重载都读当前服务端，要么改用带访问器的重载，要么把结果缓存下来。
- **失败用结果记录，不用异常**：所有 `*Result` 记录（`UseResult`、`GateResult`、`BreakthroughResult` …）都带一个 `Failure` 枚举与可选的 `failedResource`，正常拒绝不会抛异常。
- **客户端只发意图**：新增交互时只传 id 与选择，服务端自己重新解析定义再判定，见[网络协议与服务端权威](network)。
