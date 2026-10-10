# 80 灵植野生收养与 Jade 显示设计

> **已被 [`81`](81_灵植按方块认领与果实药龄设计.md) 取代（2026-10-10，用户要求彻底重写）**：灵植改成**按方块认领**（`blocks` 取代 `items`、`api/SpiritHerbPlant` 整个删除），区块附件收敛成 `坐标 → 一个 gameTime` 的表——**"野生"的判据从"行里的 `seed` 有没有值"变成"那个数是正是负"**（正＝被放下过、负＝自然生成未读过），`seed` 字段与 `HerbStatus.wild` 随之删除，`growth.seeds` / `growth.harvest` 换成 `growth.drops`。Jade 的两半分工（数据提供者 / 显示组件各一个对象、靠相同 `getUid()` 配对、登记面只有按 `Class` 的重载所以第一句只能 `instanceof` 一个方块类）**仍然有效**，但过滤条件已从 `instanceof SpiritHerbPlant` 换成 `SpiritHerbService.findBlock`，且不再显示"是否野生"那一行。原文保留作历史。

> **修订（2026-10-09，见 [`79`](79_灵植年龄与区块附件设计.md)）**：本稿取代 `79` 的野生判据。原文保留作历史。

## 基准与状态

2026-10-09，基于工作树（未提交改动：`research/README.md`、`78` / `79` 的落地改动、`secret_realm_reward_box` 资产删除、`items.md` 等）。Minecraft 26.1.2 / NeoForge 26.1.2.99 / Java 25。Jade 的登记面与 `Accessor` 取数面是拿 `curse.maven:jade-324717:8651070` 那份 jar 用 `javap` 核过的（§3.2）。

**状态：已落地（未实机）**——`compileJava` / `compileTestModJava` / `processTestModResources` 通过、主包两份 lang 键集合一致（1167 键）；探针已按新口径重写但**未实跑**（实机需另行获准，见 §6）。

**一句话**：野生植株从「附件里永远没有数据、每次读都现算」改成**第一次读就收养**——roll 出的药龄**写进行里**，此后它与播种的植株走**同一条结算路**（每 20 tick、照扣灵气）；两种植株的分别从「有没有行」挪到「**行里有没有种苗**」。同时给灵植补一套 **Jade 显示**（草药名 + 当前药龄 / 成熟药龄 + 成熟与否 + 是否野生）。

## 拍板（2026-10-09，用户逐条选择）

| # | 问题 | 结论 |
|---|---|---|
| ① | 收养后还长不长 | **变成普通植株继续长**——每 20 tick 照常结算，`growth.costs` **照扣灵气**。这条推翻了 `79` 的「野生不付灵气」 |
| ② | 什么时机写这行 | **任何读取药龄的路径**——右键状态查询 / 采摘 / 破坏 / **Jade 看一眼**都算，不限于 Jade |
| ③ | Jade 显示什么 | **草药名 + 当前药龄 / 成熟药龄 + 成熟状态**，野生额外标一行 |
| ④ | Jade 挂在哪一层 | **只加数据夹具**时代的既有礼节：登记在 `Block.class` 上、由 `instanceof SpiritHerbPlant` 过滤（Jade 没有按接口登记的入口，见 §3.2） |

---

## 0. 速览

| 三句话 | |
| --- | --- |
| **它改了什么** | `79` 把「野生」定义成**结构性的缺失**（附件里没有那一行），代价是**每次读都要重算**：`wild_age` 是一个 `NumberProvider`，补算又要从 gameTime 0 逐期走到现在（上限 1 年 ≈ 1200 期公式求值）。Jade 每看一眼就是一次读取，于是「看一眼」会变成「跑 1200 次求值」，而且两次看得到的数字可能不同（roll 是随机的）。 |
| **一句话形状** | **首次读取即落地**：`resolve` 的野生分支从「算完就扔」改成 `adopt`——roll 出起点、从 gameTime 0 补算一次、把结果与「现在」写进 `mxt:herb_chunk` 的一行（`seed` 是空堆）。行一旦存在，它就被 `HerbChunkTicker` 当作普通植株结算。 |
| **谁负责什么** | 不变：「哪些方块算灵植」归方块（`api/SpiritHerbPlant`）、「多老算熟 / 野生起点多大」归数据包、「结算节奏与补算」归本体服务。**新增的只有读侧展示**（`compat/jade`）。 |

**两种植株的分别换了位置：**

| | 播种的 | 野生的 |
| --- | --- | --- |
| **`79` 的判据** | 有行 | **没有行** |
| **本稿的判据** | 行里的 `seed` **非空** | 行里的 `seed` **是空堆**（或者还没有行，即尚未被读过） |
| 结算 | 每 20 tick，扣费 | 收养后**相同** |
| 采摘产出 | 收成 + 种苗 | **只有收成**（它从来没有种苗） |
| 潜行拉苗 | 退回种苗、移除植株 | **不接管**（回落原版），植株留在原地 |

---

## 1. 为什么这算改形状

`79` 的 §3.6 把「行存在与否」当成唯一的身份判据，并明写**不需要额外的标记位**：

> 「这一格有没有记录」＝「它是不是播种出来的」：附件有记录就是播种的，没有就是野生的。**两者不需要额外的标记位**。（`79:180`）

这条判据成立的前提是**野生植株的行永远不会被写出来**。本稿要写这一行，前提就没了，于是判据必须换一个载体。换的是**行里的 `seed`**：

- 播种时 `plant`（`SpiritHerbGrowthService:73`）写的是 `stack.copyWithCount(1)`——**永远非空**（能播种就说明 `seedOf` 认出了它）。
- 收养写的是 `ItemStack.EMPTY`——**永远为空**。
- 这个字段本来就存在、本来就参与存档，而且**已经是「有没有种苗」这件事的判据**：`pull`（`:149`）与 `harvest`（`:161`）都在读 `plant.seed().isEmpty()`。

所以判据不是新加的，只是**从「行的有无」挪到「行里那个已经在了的字段」**。这比新增一个 `wild` 布尔位好：多一个标记位就多一个能与 `seed` 矛盾的状态（比如「标记说野生、行里却有苗」），而本仓库的房规是**同一件事不要写两遍**。

**代价**：`79:180` 那条口径作废，`HerbChunkAttachment` / `SpiritHerbPlant` / `SpiritHerbGrowthService` 三处类注释都跟着改了。`79` 保留为历史稿，文首加修订标记。

---

## 2. 收养的形状

### 2.1 改动落在 `resolve` 的野生分支

`resolve`（`SpiritHerbGrowthService:199-208`）是**唯一**回答「这株草现在几岁」的地方——右键状态查询与采摘都走 `emptyHand:90`，破坏掉落走 `breakDrops:176`。`79` 的野生分支是纯函数：

```java
// 79 的形状：算完就扔，下一次读重新 roll、重新补算
if (plant == null) {
    double rolled = growth.wildAge().evaluate(FormulaContext.of(level));
    double start = Double.isFinite(rolled) && rolled > 0.0D ? rolled : 0.0D;
    return settle(level, pos, growth, Math.min(start, growth.maxAge()), level.getGameTime());
}
```

现在拆成两个方法——`resolve` 只负责分派，`adopt` 负责这一件事：

```java
private static double resolve(ServerLevel level, BlockPos pos, Growth growth,
                              Optional<Holder<SpiritHerb>> herb, Plant plant) {
    if (plant == null) return adopt(level, pos, growth, herb);
    fold(level, pos, plant, growth, level.getGameTime() - plant.settledAt());
    write(level, pos);
    return plant.age();
}

private static double adopt(ServerLevel level, BlockPos pos, Growth growth, Optional<Holder<SpiritHerb>> herb) {
    double rolled = growth.wildAge().evaluate(FormulaContext.of(level));
    double start = Double.isFinite(rolled) && rolled > 0.0D ? rolled : 0.0D;
    double age = settle(level, pos, growth, Math.min(start, growth.maxAge()), level.getGameTime());
    attachment(level, pos).start(pos, herb, level.getGameTime(), ItemStack.EMPTY).setAge(age);
    write(level, pos);
    return age;
}
```

要点：

1. **补算那一步完全照抄 `79`**：`settle(...)` 还是那个「只算 `growth_rate`、不碰 `condition` 与灵气」的逐期推进（`:249-259`），上限还是 `ticks_per_year`。所以**收养那一刻的药龄与 `79` 现算出来的完全一样**——这不是一次语义变更，是**把同一个结果存下来**。
2. **`settled_at` 写的是「现在」**，不是 0：补算已经把 gameTime 0 到现在的这段吃掉了，行从此刻起记账。写 0 会让下一次结算把同一段再算一遍。
3. **`seed` 写 `ItemStack.EMPTY`**：野生植株没有种苗，这个字段就是两种植株的新判据（§1）。
4. **`herb` 写方块给出的那株草**：行一旦存在就自己带 herb，与播种行同形；`herbOf`（`:290-295`）优先读行，所以收养之后即使方块改口也不影响。
5. **收养用 `attachment(...)` 而不是 `existing(...)`**：这是**真的在写**，所以建附件是对的——房规「只读查询用 `getExistingData`」约束的是**问一句就走**的路径（`row` / `existing` 仍然只读），而收养本身就是写。
6. **收养会回调 `growthChanged`，破坏那条路不会**：收养是一次药龄跳变，而**下一次结算要等到 20 tick 之后**（若起点已到 `max_age`，`fold` 返回 false，那次回调永远不会来），所以方块必须在这一刻被告知。因此补算那一步抽成 `wildAge`：`adopt` = `wildAge` + 写行 + 回调，**破坏掉落**只调 `wildAge` 拿数——它走 `BlockDropsEvent`，方块**可能已经被移出世界**（`79` B11），在那里回调方块就是把消息发给一个不在的东西，而且它写下的行会立刻被 `dropOnRemove` 的 `forget` 删掉。所以 `api/SpiritHerbPlant.growthChanged` 的 Javadoc 里写明收养时机，破坏那一路则明确不算。

### 2.2 收养之后它就归 ticker 管

行一旦存在，`HerbChunkTicker.settle`（`:98-130`）下次扫到这个区块就会像对待播种植株一样处理它：条件、灵气、`growth_rate`。**这就是拍板 ①**，也是本稿与 `79` 最实质的行为差别：

- `79`：野生植株**永远不会被结算**，所以它不扣灵气、也不会长大（每次读现算的那个数已经封顶在 1 年）。
- 本稿：收养之后**照常扣灵气**。`wild_age` 只决定**收养那一刻的起点**，此后与播种植株同命。

**这意味着一株野生的灵草现在会成为灵气池的消费者**——`growth.costs` 写了多少就扣多少。数据包由此得到一个真实的选择：把 `wild_age` 写得很大、让自然生成的植株一被看见就接近成熟；或者写得小、让它从此时起慢慢长并持续付费。这是本稿有意交还给数据包的权力，`79` 那条「野生不付灵气」的口径也随之作废。

### 2.3 读侧出口 `statusAt` / `HerbStatus`

Jade 需要「只看不摘」地拿到读数，而 `emptyHand` 会在成熟时直接把植株摘掉。所以新增一个只读出口：

```java
public static Optional<HerbStatus> statusAt(ServerLevel level, BlockPos pos, SpiritHerbPlant block,
                                            BlockState state) {
    Optional<Holder<SpiritHerb>> herb = herbOf(level, pos, block, state);
    Growth growth = herb.map(value -> value.value().growth().orElse(null)).orElse(null);
    if (growth == null) return Optional.empty();
    Plant plant = row(level, pos).orElse(null);
    double age = resolve(level, pos, growth, herb, plant);
    return Optional.of(new HerbStatus(herb.orElseThrow(), age, growth.matureAge(),
            plant == null || plant.seed().isEmpty()));
}

public record HerbStatus(Holder<SpiritHerb> herb, double age, double matureAge, boolean wild) {
    public boolean mature() {
        return this.age >= this.matureAge;
    }
}
```

- **`resolve` 是唯一出口**：`statusAt` 不自己算 age，也不自己 roll，所以「读一次」与「摘一次」看到的是同一个数（`79` 那条「一次交互只解析一次」的口径继续成立）。
- **`wild` 读作「没有种苗」**：`plant == null`（还没行，正要被收养）与 `plant.seed().isEmpty()`（收养过了）都是野生，与 §1 的判据同一件事。
- **方块没有 `growth` 定义时返回空**：没有 `growth` 就没有「几岁算熟」，显示不了。

### 2.4 「看一眼就会写存档」

这是拍板 ② 的直接后果，也是最需要写下来的一条：**Jade 的服务端数据提供者读一次药龄，就会收养一株野生植株**。也就是说

- 玩家扫视一片自然生成的灵草，**被看到的那几株**从此开始每一期扣灵气；
- 没被看到的**不写、不扣**，保持 `79` 的行为（下次被读到时再 roll 并补算——补算上限 1 年，所以「老服上的野生植株很老」这条语义**没有丢**）。

宽严的取舍是刻意的：把收养放在「第一次读」而不是「区块加载」是**用户拍板 ②**，好处是**没人看的区块一个字节都不写**，坏处是「看一眼」有副作用。`SpiritHerbDataProvider` 的类注释里明写了这一条。

---

## 3. Jade 两半

### 3.1 两个对象、一个 uid

房规（`AGENTS.md` §3）要求 Jade 的载荷与显示**必须拆成两个对象**，靠**相同的 `getUid()`** 配对：Jade 自 1.21.6 起在登记时直接拒收「一个对象同时实现 `IServerDataProvider` 与 `IComponentProvider`」的写法（`Data providers cannot implement IComponentProvider`，客户端启动即崩）。照 `SpiritCraftingTable{Data,Component}Provider` 的现成模板：

| 文件 | 角色 | 登记口 |
| --- | --- | --- |
| `compat/jade/SpiritHerbDataProvider.java` | `IServerDataProvider<BlockAccessor>`：**载荷** | `register(IWailaCommonRegistration)` → `registerBlockDataProvider` |
| `compat/jade/SpiritHerbComponentProvider.java` | `IBlockComponentProvider`：**显示** | `registerClient(IWailaClientRegistration)` → `registerBlockComponent` |

`ID = mxt:spirit_herb` 与四个 NBT 键常量都放在**载荷**那一侧，显示读它——只登客户端组件不会送任何数据，`accessor.getServerData()` 永远是空 NBT，而只登载荷则什么都不显示。

四个键（都带 `mxt_` 前缀，避免与 Jade 自己的键撞名）：

| 键 | 类型 | 含义 |
| --- | --- | --- |
| `mxt_herb` | String | 草药 id（`SpiritHerb` 注册表条目的 id） |
| `mxt_herb_age` | double | 当前药龄 |
| `mxt_herb_mature_age` | double | 这一株的 `mature_age` |
| `mxt_herb_wild` | boolean | 是不是野生的（判据见 §2.3） |

显示侧**只在 `mxt_herb` 存在时才画**（`data.contains(...)`）：没送数据的方块（也就是不是灵植的方块）不该多出任何一行。

### 3.2 挂在 `Block.class` 上，由 `instanceof` 过滤

登记面是用 `javap` 核过的——`IWailaCommonRegistration` 与 `IWailaClientRegistration` 的登记方法**只有**按 `Class` 的两种重载，**没有**按接口或谓词登记的入口：

```
registerBlockDataProvider(IServerDataProvider<BlockAccessor>, Class<?>)
registerBlockComponent(IComponentProvider<BlockAccessor>, Class<? extends Block>)
```

灵植方块是**数据包 / 附属模组**提供的，本体连一个类型名字都点不出来，所以只能登记在 `Block.class` 上、在里面自己过滤：

```java
public void appendServerData(CompoundTag data, BlockAccessor accessor) {
    if (!(accessor.getLevel() instanceof ServerLevel level)) return;
    BlockState state = accessor.getBlockState();
    if (!(state.getBlock() instanceof SpiritHerbPlant plant)) return;   // 唯一的过滤器
    ...
}
```

**每个被看的方块都会跑一次这个守卫**，所以它是第一句、也是唯一一句先把非灵植挡掉的话。这与 `HeatSourceDataProvider` / `BlockAuraComponentProvider` 已有的做法一致（它们也登记在 `Block.class` 上）。

### 3.3 草药名在客户端解析，不随载荷送文本

载荷只送**id**。显示侧拿客户端自己那份数据包注册表把 id 换回 `Holder<SpiritHerb>`，再交给 `DefinitionText.name(holder)`：

```java
Holder<SpiritHerb> holder = MxtDatapackRegistries
        .holder(accessor.getLevel().registryAccess(), MxtResourceKeys.SPIRIT_HERB, id).orElse(null);
return holder == null ? DefinitionText.name(id, "spirit_herb") : DefinitionText.name(holder);
```

这样**两种写法都读得对**：数据包自己写了 `name`（`NamedDefinition`）就用它写的那份，没写就回到 `spirit_herb.mxt.<命名空间>.<路径>` 这个生成键。若随载荷送一段已经渲染好的文本，数据包里那份 `name` 就永远显示不出来。定义被删掉时（查不到 holder）回落生成键，仍然读得出「它原来是哪一种草」。

### 3.4 显示的四行

| 行 | 键 | 内容 |
| --- | --- | --- |
| 草药名 | （`DefinitionText`） | 绿色，与物品 tooltip 的写法一致 |
| 药龄 | `jade.mxt.spirit_herb.age` | `药龄 %s / 成熟 %s`，两个数都是 `TooltipText.number`（去掉多余的 `.00`） |
| 成熟与否 | `jade.mxt.spirit_herb.{mature,immature}` | 只出其中一行，`age >= mature_age` 决定 |
| 野生 | `jade.mxt.spirit_herb.wild` | 只在 `mxt_herb_wild` 为真时出 |

另外登记页名 `config.jade.plugin_mxt.spirit_herb`（房规：Jade 插件配置页的名字就在这个键里）。**五个键两份 lang 都要写**（本稿落地后主包 1167 键、两边一致）。

---

## 4. 边界与坑

| # | 边界 | 怎么办 |
|---|---|---|
| B1 | **读一次就有副作用**（收养会写存档、此后每期扣灵气） | 拍板 ② 的已知代价，写进 `SpiritHerbDataProvider` 类注释与文档站；收养只发生在**真的读到了药龄**时，没被读过的区块一个字节都不写 |
| B2 | **`settled_at` 写「现在」而不是 0** | 补算已经把 gameTime 0 到现在的这段吃掉了（§2.1 第 2 点）；写成 0 会让下一次结算重复计费 |
| B3 | **判据从「行的有无」换成「`seed` 的有无」** | §1；`pull` / `harvest` 本来就在读这个字段，没有新增标记位 |
| B4 | **Jade 登记面没有接口 / 谓词重载** | 登记在 `Block.class` 上、第一句 `instanceof` 过滤（§3.2）；这是 `javap` 核过的事实，不是没找到 |
| B5 | **Jade 不许一个对象兼两职** | 两个 enum、一个 uid（§3.1）；兼了就客户端启动即崩 |
| B6 | **`statusAt` 拿不到 `growth` 时不显示** | 返回空——没有 `growth` 就没有「几岁算熟」，没有可显示的东西 |
| B7 | **`statusAt` 不能摘走植株** | 它只读 `resolve`，不碰 `harvest`；成熟与否由显示层自己比，不触发采摘 |
| B8 | **破坏那一路不收养、也不回调 `growthChanged`** | §2.1 第 6 点：方块可能已被移出世界，写下的行也会立刻被 `forget` 删掉；那一路改用 `wildAge` 直接拿数 |

---

## 5. 要改的清单

| 路径 | 怎么改 |
| --- | --- |
| `runtime/alchemy/SpiritHerbGrowthService.java` | `resolve` 加 herb 形参；野生分支拆出 `adopt`；新增 `statusAt` + `HerbStatus`；类注释改口径 |
| `attachment/HerbChunkAttachment.java` | 类注释改口径（行的有无不再是判据）；`start` 的注释补收养这一路 |
| `api/SpiritHerbPlant.java` | 类注释：野生植株「在被读之前」由方块说 |
| `compat/jade/SpiritHerbDataProvider.java` | 新增（§3.1） |
| `compat/jade/SpiritHerbComponentProvider.java` | 新增（§3.1） |
| `compat/jade/MxtJadePlugin.java` | 两个登记口各加一行，并注明为什么落在 `Block.class` 上 |
| 两份 `lang`（zh / en） | `jade.mxt.spirit_herb.{age,mature,immature,wild}` + `config.jade.plugin_mxt.spirit_herb` |
| `src/test-mod/` 的 `HerbProbes` | `wild` 腿重写（§6）；`HerbTestBlocks` 类注释改口径 |
| `research/79` | 文首加修订标记（§1 判据被本稿取代；§3.3 野生那段作废） |
| `research/README.md` | 目录加 `80`，`79` 的状态列加一句「判据已被 `80` 取代」 |
| 文档站（**中英各一份**） | `datapack/json/spirit_herb.md` 的野生语义、`index.md` 的灵植行、`installation.md` 的 Jade 兼容行 |

---

## 6. 验收

- **必做**：`.\gradlew.bat compileJava compileTestModJava processTestModResources --console=plain` 通过；两份 lang 键集合一致。**已通过**（主包 1167 键、两边一致）。
- **探针**（`src/test-mod` 的 `/mxt_test herb`，`wild` 腿重写后）——**已编译、未实机**：
  - `wild starts with no row`：刚放下的野生方块**还没有行**（方块自己说得出它是什么）。
  - `wild adopted on read`：`statusAt` 读一次之后**行出现**、`wild` 为真、**行里的 `seed` 是空堆**。
  - `wild row names herb`：行里那株草就是方块报的那一株（新判据下 `seed` 空、`herb` 在）。
  - `wild age cached`：**再读一次得到同一个数**——这是本稿的核心断言（`79` 的形状下两次读会各 roll 一次）。
  - `wild harvest`：摘下来的收成带 `mxt:herb_age >= 10`、**不出种苗**、行被清掉。
  - `wild pays nothing`：收养的那一刻**不扣灵气**（补算那一趟只走 `growth_rate`，与 `79` 一致）；扣费要到收养**之后**的结算期才开始（拍板 ①），本腿在同一 tick 内摘掉，所以看不到扣费。
  - `wild not pullable`：潜行**不接管**，植株留在原地。
  - 原有的 `growth` / `breaks` / `vanillaFlowers` 各腿**未改判据**，应照旧通过（`seed` 非空 → 仍然是播种植株）。
- **实机**：`runTestClient` + `/mxt_test herb` **必须先获准**（`AGENTS.md` §1.4）；本稿批准不等于运行许可。**本轮未获准，只报告编译与静态检查结果**。实机时留意两件事：① Jade 那四行要对着一个真的夹具方块看（`alchemy_test_wild_grower` 与 `alchemy_test_soil` 各看一次，后者种上再看不该多出野生的那一行）；② 收养后的植株**下一期会不会真的扣灵气**只有实机能验，探针里那一段是同一 tick 内完成的。
