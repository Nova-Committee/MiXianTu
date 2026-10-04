# 70 数据表与 ItemMatcher 替代评估

> **基准**：工作树（2026-10-04）；代码事实对着 `ItemMatcher` 的全部实现与消费点逐处读过，NeoForge 侧对着
> `neoforge-26.1.2.99-sources.jar` 与 `minecraft-patched-26.1.2.99-sources.jar` 读过，并对着官方文档
> <https://docs.neoforged.net/docs/resources/server/datamaps/>（26.1 版）复核过。
> **状态**：**已落地**（2026-10-04）。用户拍板：**六张"可迁"的全部迁移**（`item_aura` / `currency` / `item_binding` / `weapon_binding` / `tool_binding` / `blueprint_binding`），并明确"不用考虑有些物品匹配不能匹配 `ItemStack` 的问题"——即**接受**§5.1 那条能力损失。**同日追加**：用户指出数据表的键可以是**任何注册表**，于是方块键的 `block_aura` 与 `heat_source` 也迁了（见 §11）。八张表全部建成 `AdvancedDataMapType`，各带自己的 merger（priority 表用共享的 `PriorityMerger`，方块灵气用累加口径），注册表总数 39 → 31；**§0 的结论、§7 的"不建议整体替代"与 §8 的路线都由此作废，保留原文作为决策记录**。实现形状以代码（`registry/MxtDataMaps.java`）与 [`docs/数据包格式.md`](../docs/数据包格式.md) 的「数据表」一节为准；它的前身是 [`69`](69_品质匹配与数据表设计.md) 拆出来的一节。

## 11 同日追加：方块键的两张（2026-10-04）

用户指出 **`DataMapType` 的 `R` 可以是任何注册表**，所以"方块 → 数据"的表同样适合。盘下来只有两张方块键的表（`alchemy_wall_material` 不是：它靠炉壁物品上的 `mxt:alchemy_wall_material` 组件认领）：

| 表 | 值 | 合并口径 | 为什么合适 |
| --- | --- | --- | --- |
| `mxt:block_aura` | `Map<Holder<aura>, AuraValue>`（就是那张灵气表） | **累加**：`BlockAuraService.AuraMerger` 复用 `AuraChunkAttachment.merge`（原逻辑从 `private` 提到 `public static`，一处实现） | 方块灵气本来就是累加量；迁完 `BlockAuraService` 不再建"方块 → 定义列表"的索引，每格一次 `Holder#getData` |
| `mxt:heat_source` | `{max_temperature, heating_per_tick, priority}` | 共享的 `PriorityMerger` | 原 `AlchemyHeatService` 那套"按方块注册表实例开键 + `ServerCache.invalidate()` + 标签展开 + priority 比较"整段删除 |

连带：`data/aura/BlockAura.java` 删除（值就是那张表）、`HeatSource` 去掉 `blocks`、Jade 的方块灵气提示改读数据表、picker 的数据表分类放宽成"任意注册表 + 一行怎么变成一个 `ItemStack`"（方块分类用 `block.asItem()`）。测试包两份 JSON 移到 `data/mxt/data_maps/block/`。

## 0 结论

用户给出的判据是对的，而且它一条就能切开全部问题：

> **`ItemMatcher` 匹配的是 `ItemStack`，数据表匹配的是 `Holder`。**

由此：

- **凡是"匹配这件事本身需要看堆"的地方，永远不能换**——数据表的键只能是注册表条目（物品 id 或 `#标签`），
  它对同一个物品的两堆东西一视同仁。这类地方包括：`HoldBinding` 家族（`SpiritChargeHold` 认的是
  "任何带 `mxt:spirit_storage` 的堆"）、`mxt:item_matcher` 系列条件、`mxt:item` 代价、组件里的条目表。
- **十张 `ItemMatcher` 注册表里，只有 3 张适合迁**（`item_aura`、`tool_binding`、`blueprint_binding`），
  另有 3 张"能迁但要补偿"（`currency`、`item_binding`、`weapon_binding`），4 张**不能迁**
  （`artifact`、`pill_binding`、`spirit_herb`、`technique_binding`）。
- **不建议整体替代**：真正该用数据表的是**新**的"条目 → 数据"表（如 [`69`](69_品质匹配与数据表设计.md) 的
  `mxt:default_quality`）与那三张"纯数据"表；`ItemMatcher` 表与数据表回答的是两个问题
  （"这堆物品属于哪个概念" vs "这个条目附带什么数据"）。

## 1 那条轴：`ItemStack` vs `Holder`

| | `ItemMatcher` | 数据表 |
| --- | --- | --- |
| 判定入口 | `boolean matches(ItemStack)`（`util/matcher/ItemMatcher.java:91`） | `Holder#getData(DataMapType)`（按键查表） |
| 键是什么 | 一条**条目**（见下 7 种） | 注册表条目 id 或 `#标签`，加载期展开成那个条目的 `Holder` |
| 能看堆吗 | **能** | **不能** |
| 概念有 id 吗 | 有（注册表条目） | 没有（值是内联数据） |
| 有名字吗 | 有（`DefinitionText`） | 没有 |
| 冲突口径 | `priority` 最高者胜，同分按注册表顺序 | merger（默认后到者覆盖）+ `replace` |
| 要 `Provider` 吗 | 要（`find` 的调用点都带 access） | **不要** |

`ItemMatcher.Entry` 的 7 种条目，按"看不看堆"分两类：

| 条目 | 看什么 | `itemLevel()` |
| --- | --- | --- |
| `item`（`ItemEntry`） | 物品 id | true |
| `tag`（`TagEntry`） | 物品标签 | true |
| `wildcard`（`WildcardEntry`） | 物品 id 的 `*` / `?` 通配 | true |
| `regex`（`RegexEntry`） | 物品 id 的正则 | true |
| `technique`（`TechniqueEntry`） | 堆上有没有 `mxt:technique` 组件 | **false** |
| `spirit_storage`（`SpiritStorageEntry`） | 堆上有没有 `mxt:spirit_storage` 组件 | **false** |
| `herb_tag`（`HerbTagEntry`） | 这堆被哪条 `spirit_herb` 认领、元素/材料标签 | **false** |

由此推出四条硬边界，后面每一处判定都用它们：

1. **堆级匹配能力归零**：数据表写不出"任何带这个组件的堆"，也写不出通配/正则。
2. **没有身份**：值不能被别的定义以 `Holder` 引用，不能当存档键，不能反向查找。
3. **没有 priority**：一个条目一条值；要"多条并存"就得把值做成列表（`listMerger` 追加）再在读取侧排序。
4. **只能从 `Holder` 查，而且必须是 reference holder**（`Direct` holder 不行，官方文档明说）——
   有堆的地方写 `stack.getItemHolder()`；反过来拿 `Item` 本体是查不到的。

数据表**多给**的四样（这也是它值得存在的理由）：标签键 + 加载期展开、`replace`/`remove`/merger 的合并语义、
**免 `Provider`**、可选的客户端同步。

## 2 数据表机制（全文）

### 2.1 是什么

NeoForge 的"**注册表条目 → 值**"数据包表面，用来取代原版写死在代码里的那些 map（它的内置表
`neoforge:oxidizables` / `furnace_fuels` / `compostables` / `vibration_frequencies` … 就是干这个的）。
官方文档的说法很贴切：**标签是"条目 → 布尔"，数据表是"条目 → 对象"**；而且像标签一样是**追加**而不是覆盖。

### 2.2 文件位置

```
data/<mapNamespace>/data_maps/<registryNamespace>/<registryPath>/<mapPath>.json
```

- `<mapNamespace>` / `<mapPath>` 是**数据表 id** 的命名空间与路径，`<registryNamespace>` 在是 `minecraft` 时省略。
  例：挂在 `minecraft:item` 上的 `mxt:default_quality` → `data/mxt/data_maps/item/default_quality.json`。
- ⚠️ **第一段命名空间必须是"表"的命名空间，不是内容包自己的**：加载器用文件名推出一个 `Identifier`
  （`FileToIdConverter.fileToId`）再查已注册的表类型，而类型是按它自己的 id 注册的。写错地方会留一条
  `Found data map file for non-existent data map type` 的 warn。
- 一个内容包想加值，是**往 `data/mxt/data_maps/item/` 里再丢一个文件**，不是起自己的命名空间——这正是它必须有合并语义的原因。

### 2.3 JSON 形状

```json
{
  "replace": false,
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "example" }],
  "values": {
    "mxt:blank_talisman": "example:tier_3",
    "#example:papers": { "value": "example:tier_2", "replace": true },
    "example:herb": {
      "neoforge:conditions": [{ "type": "neoforge:loaded", "id": "example:other" }],
      "value": "example:tier_4"
    }
  },
  "remove": ["example:legacy_item", "#example:retired"]
}
```

- `values`：键是**条目 id 或 `#标签`**；值由表自己的 codec 决定，可以**裸写**，也可以写
  `{"value": …, "replace": true}`（`replace` 表示"绕过 merger 直接压"）。
- 顶层 `replace`：清空此前所有值（文档口径：**只给整合包作者用**，模组不要发）。
- `remove`：条目/标签数组，**在追加之后跑**，所以"先给 `#标签`、再排除个别条目"是官方模式；
  用 `AdvancedDataMapType` 时还能写成对象，**按 key 拆解删除**（如文档的 `MapRemover(key)`）。
- `neoforge:conditions`：**文件级与值级都支持**（值的 codec 外面就套着条件 codec）。

### 2.4 值 codec 是注册表感知的

`DataMapLoader extends ContextAwareReloadListener`，解码用

```java
new ConditionalOps<>(getRegistryLookup().createSerializationContext(JsonOps.INSTANCE), context)
```

即**带着服务器完整 `HolderLookup.Provider` 的 `RegistryOps`**。所以值里可以直接放 `Holder<ItemQuality>`、`Holder<Block>`
这类指向**数据包注册表**的引用。**值必须不可变**（官方文档原话 "Data map entries must be immutable"，推荐 record）——
因为**值不会被复制到标签上**，给 `#tag` 写值等于给标签里每个条目各存一份**同一个对象引用**。

### 2.5 合并与冲突

- 同一条目被多包给值：走 merger。`DataMapValueMerger.defaultMerger()` = **后到者覆盖**；
  内建还有 `listMerger()` / `setMerger()` / `mapMerger()`（官方文档："值本身是集合或类集合的，强烈建议自定义 merger"）。
- 自定义 merger 的签名会给出**两个值各自的来源**（`Either<TagKey<R>, ResourceKey<R>>`）与所属注册表，
  所以"具体条目压过标签条目"这类规则写得出来。
- **remover** 同理：默认整体删除，自定义可以只删值里的一部分（返回 `Optional.empty()` 才是全删）。

### 2.6 同步

- `DataMapType.builder(...).synced(networkCodec, mandatory)`；同步载荷
  （`RegistryDataMapSyncPayload`）用 `buf.registryAccess().createSerializationContext(JsonOps.INSTANCE)` 编解码，
  所以同步 codec 同样能引用注册表，也可以是"字段更少"的 codec（客户端不需要的字段不传）。
- `mandatory = true` 会让**不认识这张表的客户端（含原版客户端）连不上**；自带客户端的模组选 `false`。
- 一条硬限制（`RegisterDataMapTypesEvent.register`）：给**未同步的数据包注册表**注册**同步**数据表会抛。

### 2.7 读取与生命周期

- 读：`Holder#getData(DataMapType)`（`IWithData#getData`，没有就是 `null`）；整张表用
  `IRegistryExtension#getDataMap(DataMapType)`。**都不需要 `Provider`**，所以客户端渲染、配方匹配里也能读。
- 注册：**mod 事件总线**的 `RegisterDataMapTypesEvent`。
- 加载完发 `DataMapsUpdatedEvent`（cause 为 `SERVER_RELOAD` 或 `CLIENT_SYNC`；单机房主与内存连接**不发**
  `CLIENT_SYNC`）——缓存失效点。`/reload` 会整表重建。

### 2.8 官方文档复核（2026-10-04）

| 证实的能力 | 备注 |
| --- | --- |
| 值是任意复杂 codec（记录、嵌套对象、列表、映射；文档例子 `{"amount": 12, "chance": 1}`） | "复杂值"是支持的，但**复杂 ≠ 有身份** |
| 值必须不可变、推荐 record | 见 §2.4 |
| 内建 merger 三种 + 自定义 merger 能拿到来源 | 见 §2.5，priority 的替代方案 |
| remover 可拆解删除；removals 在 additions 之后 | 见 §2.3 |
| 文件级与值级 `neoforge:conditions` | 见 §2.3 |
| 可挂**静态注册表与数据包注册表** | 我们的 `mxt:quality` 注册表也能挂（但同步限制见 §2.6） |
| **只能通过 `Holder` 查，且 `Direct` holder 不行** | 见 §1 第四条 |
| 像标签一样"追加"而非覆盖 | 与 §2.5 一致 |

（另注：官方文档里 `AdvancedDataMapType` 的例子写成两个泛型，我们这份 26.1.2.99 源码里是三个
（`R` / `T` / `VR extends DataMapValueRemover<R, T>`）；以源码为准。）

## 3 目前用到 `ItemMatcher` 的全部地方

| 组 | 内容 | 位置 |
| --- | --- | --- |
| **A. 数据包注册表（10 张）** | `item_aura`、`currency`、`artifact`、`spirit_herb`、`item_binding`、`weapon_binding`、`pill_binding`、`tool_binding`、`blueprint_binding`、`technique_binding` | `data/**`，注册在 `MxtDatapackRegistries` |
| **B. `HoldBinding` 家族** | `HoldBinding extends ItemMatcher`（`data/item/HoldBinding.java:23`）：`ArtifactHold`（包一份 `artifact`）、`TechniqueHold`（包条目）、**`SpiritChargeHold`（条目就是 `SpiritStorageEntry`，纯堆级）** | `runtime/artifact/ArtifactHold.java:26`、`runtime/cultivation/TechniqueHold.java:19`、`runtime/spirit/SpiritChargeHold.java:24`；由 `ArtifactHoldService:75`、`TechniqueItemService:81`、`SpiritChargeService:60` 注册进 `HoldLookup` |
| **C. 物品条件** | `mxt:item_matcher`、`mxt:item_id`、`mxt:item_tag`（`MxtItemConditions`） | `data/condition/builtin/item/**`；测试包就有 `mxt:herb_tag` 的用例（`data/probe/mxt/ability/reader_herb.json`） |
| **D. 代价** | `ItemCost`（`mxt:item`）本身 `implements ItemMatcher`，`ItemCostDraft.reserve` 逐条 `matches` | `data/cost/builtin/ItemCost.java:21`、`data/cost/ItemCostDraft.java:31-43` |
| **E. 组件里的条目表** | 阵法盘 `allowed`、灵植 `growth.seeds`、法器 `items`、绑定表的 `items`… | `data/item/FormationPlateComponent.java:77`、`runtime/alchemy/SpiritHerbService.java:49-55` |
| **F. 工具方法** | `ItemMatcher.matches/find/findAll`（`findAll` **全仓零调用**） | `util/matcher/ItemMatcher.java:43-77` |

**本体（`src/main/resources`）在这 10 张表里一个文件都没有**（内容不进本体），所以迁移只影响测试包与内容包——
但文档（仓库 + 文档站）与编辑器 schema 是全都要跟着改的。

## 4 逐条判定

判定用的三个问题：**① 成员匹配需要看堆吗？② 概念的身份被别处用了吗？③ 有多条并存 + priority 的需求吗？**

| # | 表 / 用法 | ① 堆级 | ② 身份被用 | ③ priority | picker 已注册 | 判定 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `item_aura` | 能力上可（实际只用 id/标签） | 否（`Holder<ItemAura>` 只在调用期；`ItemAuraComponent` 只存 `remain`） | 有字段 | 是 | ✅ **可迁**（最干净） |
| 2 | `tool_binding` | 同上 | 否（`ForgingBindingService:34`） | 有 | 是 | ✅ 可迁（picker 需新增一类 provider，见 §5.4） |
| 3 | `blueprint_binding` | 同上 | 否（`ForgingBindingService:40`） | 有 | 是 | ✅ 可迁（同上） |
| 4 | `currency` | 同上 | 否（`CurrencyValueService:142`） | 有 | 是 | ⚠️ 可迁：`unavailable_when` 是 `ItemCondition`（能放进值里） |
| 5 | `item_binding` | 同上 | 否（`ItemBindingService:250`） | 有 | 是 | ⚠️ 可迁，但**永久失去"任意堆算某概念"的能力**（见 §5.1） |
| 6 | `weapon_binding` | 同上 | 否（`ItemBindingService:102`） | 有 | 是 | ⚠️ 同上 |
| 7 | `artifact` | 同上 | **是**：`Holder<Artifact>` 被品质解析（`ItemQualityService.definitionDefault`）与轮盘条目（`AbilityWheelEntry:144`）引用 | 有 | 是 | ❌ |
| 8 | `pill_binding` | 同上 | **是**：`Holder<PillBinding>` 是**存档键**（`PillUsageAttachment` 的 `Map<Holder<PillBinding>, Dose>`，"这族物品"共用一个上限与冷却） | 有 | 是 | ❌ |
| 9 | `spirit_herb` | 同上 | **是**：`Holder<SpiritHerb>` 存进灵田方块实体，且要按种子反查（`SpiritHerbService.findSeed`） | 有 | 是 | ❌ |
| 10 | `technique_binding` | 同上 | **是**：要**按功法反查**（`ItemBindingService.declaration:149-155` 用 `binding.technique()` 过滤），且测试包 5 个文件**都没有 `items` 字段** | 有 | 否 | ❌ |
| B | `HoldBinding` 家族 | **是**（`SpiritChargeHold` = `SpiritStorageEntry`） | 运行时 holder 瞬态 | 有 | 否 | ❌ |
| C | 物品条件 ×3 | **是**（7 种条目全可写） | 不适用 | 默认 | 否 | ❌（不是表） |
| D | `mxt:item` 代价 | **是** | 不适用 | 默认 | 否 | ❌（不是表；且只按条目匹配） |
| E | 组件里的条目表 | **是** | 不适用 | — | 否 | ❌（组件是堆数据） |

## 5 不能替的四类（附证据）

### 5.1 堆级匹配本身

`SpiritChargeHold` 的成员就是 `SpiritStorageEntry.INSTANCE`——它认的是"**任何带 `mxt:spirit_storage` 组件的堆**"。
数据表要表达这件事，就得预先给"世界上每一件可能带这个组件的物品"都写一条值，而那是开放集合。
同理：`mxt:technique`（手册）、`mxt:herb_tag`（"被哪条灵植定义认领的堆"）、`wildcard` / `regex`（id 模式）。
**这是最硬的一条**，也正是"`ItemMatcher` 匹配 `ItemStack`"的直接后果。

### 5.2 身份被持久化

- `PillUsageAttachment`（附件、进存档）以 `Holder<PillBinding>` 为键：一族物品**共享**一份服用上限与冷却。
  数据表的值没有身份，"两族物品共用一条限制"表达不了。
- `SpiritHerbPlotBlockEntity` 存 `Holder<SpiritHerb>`：灵田的存档指向定义。
- 附带：`Holder` 在**数据包不提供该条目**时仍然是"身体里存着的那份引用"（房规里那条"注册表引用存 `Holder`"），
  数据表没有对应的东西可存。

### 5.3 反向查找

`technique_binding` 的主键其实是**功法**：`ItemBindingService.declaration` 与 `technique(...)`
都用 `binding.technique()` 过滤（`items` 只是"这条绑定顺便认领哪些物品"）。
数据表的键是物品，方向不对。

### 5.4 概念的身份（真损失）与 picker 行（不是损失）

**真损失是"概念的身份"。** `artifact` 被品质解析与轮盘引用（`Holder<Artifact>` 是
`ArtifactService.definition` 的返回类型）；`pill_binding` / `spirit_herb` / `technique_binding` 的三条见 §5.2 / §5.3。
数据表的值没有 id，所以"别的定义指向这个概念""存档里记着这个概念"都做不到。

**picker 行不是损失——这里要更正本稿早先的写法。** 一行就是一个 `PickerItem`，即
`(ItemStack stack, List<Component> names)`（`ItemPickerManager:126`）：

- **`stack`** 是"从格子里拿走什么"。`registerMatcher` 用 `ItemMatcher::entries` 经 `stackItems(...)`
  （`:130-144`）把条目展开成具体物品：标签会展开，而 `wildcard` / `regex` / 三个堆级条目**被静默丢弃**
  （`default -> {}`）。给数据表写 provider 时直接遍历 `getDataMap(type)` 的键即可，拿到的是同一批**而且更全**
  （数据表的键在加载期已经展开成具体条目，不存在被丢掉的那几种）。
- **`names`** 是搜索/显示用的多份名字：物品自己的 `getHoverName()`、`DefinitionText.name(holder)`、
  以及定义 id 的字符串（`:177`）。

**而名字本来就是自动生成的**：`DefinitionText` 的键是
`<类别>.<注册表命名空间>.<条目命名空间>.<条目路径>`（`util/DefinitionText.java:35`），
`NamedDefinition` 自带文本时用自带的，否则回落到这个生成键。所以"能不能自动生成"不是问题，
数据表这一侧只差一个**口径**：拿什么当那个"条目"。自然的选择是 **`(数据表 id, 挂到的条目 id)`**
（如 `mxt:quality` × `minecraft:carrot`），生成键形如 `quality.mxt.quality.minecraft.carrot`；
也可以顺着"值可以很复杂"这一条，把 `name` / `description` 直接写进值里（`#标签` 上的值会被复制到展开后的每个
条目，所以同一个名字自然覆盖整批）。两条都是机械工作。

剩下两处要动的只是扩展点：① picker 的**分类**来自"一个注册表"（`ItemProvider.key`，`:212`），数据表不是注册表，
需要新增一类 provider；② 行名里那份"定义 id 字符串"要换成 `(表, 键)`。

→ **picker 不构成"不能迁"的理由**：它今天只是**消费**了概念的身份，而重建它并不需要身份。

## 6 能迁的三张：迁法与代价

以 `item_aura` 为例（最干净的一张）：

```json
// data/mxt/data_maps/item/item_aura.json
{
  "values": {
    "#mxt_test:aura_fuels": { "type": "mxt:common", "aura": 64, "consume_speed": 1, "release_speed": 1 },
    "mxt_test:qingxiao_spirit_crystal": {
      "replace": true,
      "value": { "type": "mxt:common", "aura": 128, "consume_speed": 2, "release_speed": 2 }
    }
  }
}
```

值就是 `ItemAura` 去掉 `items` 与 `priority` 之后的那份记录。`tool_binding` / `blueprint_binding` 同理
（值 = 手法 id 列表 / 图纸 id 列表）。

**priority 的四种替代**（按推荐度）：

1. **自定义 merger 按来源判**：`merge(registry, first, firstValue, second, secondValue)` 里看
   `first` / `second` 是 `Either.left(TagKey)` 还是 `Either.right(ResourceKey)`——"具体条目压过标签"就是
   `second.right().isPresent() != first.right().isPresent()` 时取具体那个，否则后到者赢（约八行）。
2. **值里带 `priority` + 自定义 merger 比大小**（想要"多条定义并存"时，值改成列表 + `listMerger` 追加，
   读取侧排序取第一个）。
3. **`{"value": …, "replace": true}`**：明确表达"我压过前面的"。
4. **`remove` 在添加之后跑**：先给 `#标签`，再把个别条目 `remove` 掉。

**会丢掉的东西**：**概念的身份**（不能引用 / 不能当存档键 / 不能反查，§5.2–§5.4）、`items`/`blocks` 非空的
加载期校验、`priority` 的直接语义，以及"以后想用堆级条目"的可能性。
**`/picker` 的行不在丢掉之列**（§5.4：行能重建，只需新增一类 provider 与一个生成键口径）；
顺带一提，`stackItems` 今天会**静默丢掉** `wildcard` / `regex` / 堆级条目，数据表那一侧反而没有这个毛病。
**换来的**：免 `Provider` 查、标签键、`replace`/`remove`/merger、
可选同步、少写一套索引代码（今天 `AlchemyHeatService`、`BlockAuraService` 各自手写了标签展开 + priority 比较）。

## 7 不建议整体替代的理由（**2026-10-04 被用户否决，见文首**）

1. **语义不对**：`ItemMatcher` 表是"**这堆物品属于哪个概念**"，数据表是"**这个条目附带什么数据**"。
   把前者做成后者，概念就不能被引用、不能当存档键、不能在 picker 里出现。
2. **十张表里有四张硬性不行**（§5），另有六张要补偿 priority 与 picker 行——收益（省索引代码）远小于改动面
   （十张表 + 测试包 + 仓库文档 + 文档站中英 + 编辑器 schema）。
3. **真正的痛点不是数据包格式**：`ItemMatcher.find` 每次**全表遍历逐条 `test`**、而且**要 `Provider`**。
   这是**运行时索引**问题，照 `ChainCache` 的写法做一份"按注册表实例开键 + `ServerCache.onDatapackLoaded` 失效"
   的索引就够了，数据包一个字不用改。
4. **想要"免 `Provider`"也有别的路**：`ServerLifecycleHooks.getCurrentServer()` 在服务端到处能用
   （仓库里已有此写法），客户端则退化成"只读组件 + 数据表"（见 `69` §3.5）。

**混合方案（如果既要身份又要免 `Provider`）**：**数据表当索引、注册表当定义**——值写
`Holder<WeaponBinding>`，运行时 `Holder#getData` 直接拿到定义，省掉遍历。但一个条目只能指向一条值，
priority 得靠"值是有序列的列表"；而且要求作者写两处——**在加载期从定义自动生成同一份索引才是它的等价物**，
那也就是上面那份缓存。

## 8 若要做：分阶段路线

| 阶段 | 做什么 | 风险 |
| --- | --- | --- |
| 0 | **不动老表**：只做 `69` 的 `mxt:default_quality`（新表） | 零（新表面） |
| 1 | 把 `item_aura` 迁成数据表（最干净的一张，用来验证形状 / 同步 / merger 四条机制） | 低（本体无内容，只动测试包与文档） |
| 2 | `tool_binding` / `blueprint_binding`（要同时决定 picker 行怎么来） | 中 |
| 3 | 再评估 `currency` / `item_binding` / `weapon_binding`（要同时决定 priority 与"堆级能力"的取舍） | 高（语义取舍） |

## 9 验证方式与待拍板

- **验证**：编译；测试包夹具（`item_aura` 数据表版 + 一条 `#标签` + 一条 `replace` 覆写 + 一条 `remove`）；
  探针腿（标签展开命中 / 具体条目压过标签 / `remove` 生效 / 客户端读得到 / 与旧表结果逐条等价）；
  **实机跑一次**（`runTestServer`，先问）。要重点验的是"**值里放 `Holder`**"与"**自定义 merger**"这两处
  （源码支持，但本仓库无先例）。
- **待拍板**：
  1. 要不要迁？我建议**不迁老表**，只做阶段 0（新表用数据表，老表加缓存）；
  2. 若迁：priority 用哪种替代（§6 的 1–4）；
  3. 若迁：`/picker` 的生成键口径选哪个（照 `(表 id, 条目 id)` 生成键？还是把 `name` 写进值里？——两者都行，见 §5.4）；
  4. 是否接受"以后这些表不能再写堆级条目"。
