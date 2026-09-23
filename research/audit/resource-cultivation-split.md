# resource / cultivation 拆分记要

日期：2026-09-12。目的：让 `resource` 只负责"存储数值 + 展示资源条"，把修炼相关字段移入新的 `mxt:aura` 档案（一对一引用
`resource`）。本次为破坏性变更，不考虑数据兼容性。

> 变更记录
> - 2026-09-17：**补记两处遗留**（见 §7）：主模组的默认数据一个字节都没迁（`resource/common.json` 仍带着已被静默忽略的
    `regen`/`aura_type`，且没有对应的 `cultivation` 档案），以及"灵气种类"这套命名与 `Resource` 参数在接口层的语义没对齐（当时把
    `SpiritAccess`/`SpiritItemAccess` 改名为 `ResourceAccess`/`ResourceItemAccess`、射线字段改名、存储组件记下数值类型——其中改名方向随后被
    §7.4 再次推翻）。详细记录在 `research/audit/spirit.md` §7。
> - 2026-09-17：**命名收敛**（用户："`cultivation_technique` 改成 `technique`，`cultivation` 改成 `aura`"）。注册表
    `mxt:cultivation` → `mxt:aura`（`CultivationProfile` → `Aura`，文件移入 `data/aura/`；查找入口 `CultivationProfiles` →
    `AuraLookup`，移入 `runtime/aura/`），注册表 `mxt:cultivation_technique` → `mxt:technique`（`CultivationTechnique` →
    `Technique`，留在 `data/cultivation/`），`realm_stage` 的 `cultivation` 字段随之改名 `aura`。语义边界见 §7.3。
> - 2026-09-17：**`aura_kinds` 整条删除**（用户："aura_kinds 约束去掉，改成用 condition 的判断"）。它是第四套身份表示（裸
    `Identifier`，宣称的类别与实际提供的灵气可以互相矛盾），现从五个声明点、`AuraChunkAttachment` 三个集合与四处门禁、
    `CollectionHelper`、6 个语言键与 13 个数据文件里全部移除；"能在哪修炼"改由 `CultivateAction` 的 `start_condition`/
    `condition` 表达，炼丹用 `minimum_aura`。详见 §7.5。
> - 2026-09-17：**身份归位**（用户："`Resource` 只是一个数值系统……`Aura` 才是用来区分哪个灵气的，`ResourceAccess` 应该是传入
    aura"）。§7.2 的结论被推翻：凡是"哪一种灵气"的字段/参数/存储键全部改成 `Holder<Aura>`——物品与方块存储、`AuraAccess`/
    `AuraItemAccess`、`ItemAura.type`、`has_realm`、世界灵气（含客户端快照与 S2C 包）、修炼的 `aura_costs`/`aura_gains` 与阵法存量；
    `AuraLookup` 的整表反查删除，只留 value→aura 一个方向。数据零迁移（aura id 与 resource id 同名），`mxt:has_realm` 的
    `resource` 键改名 `aura`。详见 §7.4。

## 1. 字段去向

| 原 `Resource` 字段                                                                                                                    | 现位置                       |
|------------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `default_value`、`min`、`max`、`icon`、`particle_color`、`bars`                                                                         | **留在 `resource`**（数值与资源条） |
| `regen`、`use_condition`                                                                                                            | `cultivation`（按用户决定一并迁入）  |
| `aura_type`、`burst_amount`                                                                                                         | `cultivation`（灵气的身份与射线量）  |
| `first_realm`、`start_exp`、`start_cultivate_conditions`、`cultivation_to_resource`、`resource_to_cultivation`、`show_cultivation_info` | `cultivation`             |

`Resource.ResourceConversion` 内嵌类型随之搬到 `Aura.ResourceConversion`。

## 2. 结构与校验

- 新注册表 `mxt:aura`（`data/cultivation/Aura.java`），JSON 目录 `data/<ns>/mxt/aura/<id>.json`，条目 id 与被描述的 resource
  id 相互独立。
- **强制一对一**：`ServerCache.rebuild()` 遍历档案并按 `resource` 建表，发现第二个档案直接抛错；境界索引也从档案的
  `first_realm` 建立。
- **链条以档案为键**：`RealmStage.cultivation` 指向 `cultivation` 档案（不再是 `resource`），档案再指向数值。`ServerCache` 建
  `境界 -> 档案` 映射与链内序号，`indexChain` 校验链上每一级都指向同一档案（`stage.cultivation == 该档案`），并检测成环与跨链。
- 状态键与链一致：`CultivationAttachment.cultivation_progress` / `realm_stages` 现在都以 `Holder<Aura>`
  为键——链本身就是档案，读状态不需要再经由阶段反查数值。存档格式随之改变（不要求兼容）。

## 3. 查找入口

`runtime/cultivation/AuraLookup.java`：

| 方法                                                      | 用途                                                 |
|---------------------------------------------------------|----------------------------------------------------|
| `find(@Nullable Provider, Holder<Resource>/Identifier)` | 双端通用；`access == null` 时返回空而不是抛错                    |
| `find(LivingEntity/Level, …)`                           | 从实体或客户端世界取同步后的注册表                                  |
| `findServer(…)`                                         | 服务端专用，直接读服务器注册表，**不依赖 ServerCache 已构建**（启动期与命令路径用） |
| `byResource(@Nullable Provider)`                        | 一次扫描得到 `resource id → 档案`，供逐个数值遍历的循环使用             |
| `access(FormulaContext)`                                | 公式求值时的兜底访问入口（caster → target → 服务器）                |

刻意不做第二份缓存：档案注册表很小，而缓存需要与注册表同步失效。

## 4. 行为变化（有意为之）

- 自然恢复只对**有档案**的数值生效（`AbilityEventBridge.onEntityTick` 先按档案过滤）；无档案的数值不再自动变化。
- `use_condition` 语义保持"门禁主动消耗 + 资源条显示"，但由档案提供；没有档案的数值视为恒真（可正常消耗与显示）。
- 信息面板的"境界/修为进度"只显示有档案的数值（原来 `show_cultivation_info` 默认 true 对所有资源生效）。
- 灵气相关读取（灵根亲和、生物元素偏好、环境渲染、JEI、命令、灵力爆发、客户端热键栏）改成先取档案再取 `aura_type` /
  `burst_amount`；`particle_color` 仍是数值自己的展示字段。

## 5. 验证

- `compileJava` / `compileTestModJava` 通过；`runTestServer` 打印 `MiXianTu server audit passed`，无 `mxt` 相关
  WARN/ERROR、无注册表解析失败。
- 测试包已拆分：`data/mxt_test/mxt/aura/{qi,soul_power,spirit_power,water_power}.json` 承接原资源文件里的修炼字段；
  `channel_probe`、`divine_sense`、`true_essence` 原本 `regen: 0`，因此不建档案。

## 6. 后续可做

- 若一个数值将来需要多条修炼路径，需要放宽"一数值一档案"的唯一性校验，并把 `CultivationAttachment` 的键保持为档案（已经是了），无需再动状态结构。
- `RealmStage.cultivation` 与 `Aura.first_realm`
  的一致性现在是结构性的（链上每一级都指向同一档案），不再需要额外校验；剩下的是成环与"first_realm 不在自己的链上"两类，均已在
  `indexChain` 覆盖。
- 遍历所有数值再解析档案的旧写法（`CultivationTriggerService.refresh`、`CultivationActionEventBridge`
  的自动突破）已改为直接遍历档案，无档案的普通计数器不再被访问。

## 7. 遗留补记（2026-09-17）

三周后回看，这次拆留在主模组侧的有两处，都已补掉。

### 7.1 默认数据根本没迁

`src/main/resources/data/mxt/mxt/resource/common.json` 里仍是拆分前的形状：

```json
{ "default_value": 0, "min": 0, "max": 1000000, "regen": 0, "aura_type": "mxt:common" }
```

`regen` 与 `aura_type` 都已不在 `Resource` 里，而 `RecordCodecBuilder` **不会**对未知字段报错，所以它们被静默丢弃、日志里没有任何痕迹。
`git show --stat 96aec85 | Select-String 'main/resources'` 为空——那次拆分只动了 `src/main` 的 Java 与 `src/test-mod`
的夹具，**没有动过主模组一个数据文件**，所以测试包（有 `data/mxt_test/mxt/aura/*.json`）看起来是迁过的，默认包不是。后果是
`AuraLookup.find(mxt:common)` 为空：`AuraZoneRenderer`（灵气环境渲染取色）、`SpiritJeiText`（灵气账单颜色）、`AuraCommand`、
`CultivationAffinity`（灵根元素亲和）、`CreatureProfileService`（生物元素偏好）五处按 `aura_type`
的读取全部落空。默认包里颜色回退恰是同一个白、又没有灵根与生物档案，所以可见影响≈0，但数据是死的。

处理：`common.json` 去掉两个死字段；新增 `data/mxt/mxt/aura/common.json` =
`{"resource": "mxt:common", "aura_type": "mxt:common"}`，即把旧文件里的修炼字段原样搬进档案。逐项核对过没有附带行为：
`regen` 旧值 0 = 档案默认；没有 `first_realm`，而信息面板只遍历玩家已跟踪的链（`CultivationAttachment`
里没有这条档案），不会多出"境界/修为进度"行；`use_condition` 缺省是 `AlwaysTrueCondition`，与"无档案视为恒真"一致；
`burst_amount` 旧文件也没有（默认 0），拆分前后都不是可发射的灵气。

### 7.2 "灵气"这个词与 `Resource` 参数的语义错位

> **本节结论已被 §7.4 推翻，保留原文只为记录当时的推理。**这段写的是"接口参数用 `Holder<Resource>` 是对的"，用户随后明确否定了它。

`resource` 与 `cultivation` 分开之后，"灵气"精确地说是**一个带档案、且档案有 `aura_type` 的 `resource`**；而
`SpiritAccess`/`SpiritItemAccess`（现 `AuraAccess`/`AuraItemAccess`）搬的是任意 `resource`，连档案都不需要。接口参数用
`Holder<Resource>` 是对的（持有者池子、`ResourceService`、`aura_kinds` 全部按资源开键，默认包里灵石搬的 `mxt:common`
甚至没有档案），真正错位的是命名：射线里那个叫 `auraType` 的字段其实是 `Holder<Resource>`，与档案的 `aura_type`（
`Holder<Element>`）撞名；物品存储组件只记了一个 `int amount`，类型靠当时匹配到的定义反推，于是内容包改掉 `item_aura.type`
会把世界里已有的存量静默读成另一种灵气。两处都已改，细节与验证见 `research/audit/spirit.md` §7。

注意括号里那条论据**当轮就被 §7.1 自己拆掉了**：给 `mxt:common` 补上 `data/mxt/mxt/aura/common.json`
之后，主模组里已经没有"被搬的数值没有档案"这回事，剩下四个无档案的计数器全在测试包里、且只活在玩家池子里。也就是说这句话在写下时就已经失去了唯一的实物证据，只是当时没有回头核对。

### 7.3 三个词终于各就各位（2026-09-17）

用户读完 §7.1/§7.2 后指出"你还是有点没搞清楚 resource 和 cultivation 的区别"，并要求先做一次命名重构。重构之后，四个词各指一件事：

| 词             | 指什么                                                                              | 载体                                                                                                                            |
|---------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `resource`    | **值**：一个有边界、有图标、有资源条的数值                                                          | `mxt:resource`、`data/resource/`                                                                                               |
| `aura`        | **那份值的灵气身份**：它是什么（`aura_type` 元素标记、`burst_amount` 射线量），以及它如何参与修炼（境界链入口、恢复、换算、门禁） | `mxt:aura`（原 `mxt:cultivation`）、`data/aura/`、`Aura` 记录、`AuraLookup`、`runtime/aura/`                                           |
| `cultivation` | **修炼这件事**：进度、境界、突破、运功过程                                                          | `CultivationAttachment`（`mxt:cultivation` 附件）、`CultivationService`、`CultivationActionService`、`config.mxt.server.cultivation` |
| `technique`   | **功法**：可学习、按水平授予能力与修炼修正                                                          | `mxt:technique`（原 `mxt:cultivation_technique`）、`data/technique/`、`Technique` 记录                                               |

要点：**旧名把"定义"和"过程"混成了一个词**。做法与判据：

- `mxt:cultivation` → `mxt:aura`：这份定义回答的是"这个数值是什么灵气"，所以注册表、目录、类型（`CultivationProfile` → `Aura`
  ）与查找入口（`CultivationProfiles` → `AuraLookup`）一起改名；`Aura` 移进 `data/aura/`（与 `ItemAura`、`BlockAura`、
  `AuraZone` 同处），`AuraLookup` 移进新建的 `runtime/aura/`。
- `mxt:cultivation_technique` → `mxt:technique`：功法不是"修炼技术"的一种附属物，它就是 `technique`；类型改名 `Technique`
  、数据目录 `mxt/technique/`、标签目录同样改名。**类仍留在 `data/cultivation/`**：它是修炼系统的定义之一（与 `RealmStage`、
  `SkillStage`、`CultivateAction` 同处），与灵气定义不同类。
- `realm_stage` 的 `cultivation` 字段 → `aura`：它存的是 `Holder<Aura>`，字段名跟着类型走。
- **刻意保留 "cultivation" 的**：`mxt:cultivation` **附件**（玩家身上的修炼进度/境界/当前运功/冷却——这是过程的状态）、
  `CultivationService` 及其突破逻辑、`CultivationActionService`、`config.mxt.server.cultivation`、
  `actionbar.mxt.cultivation.*` 语言键、PAL 层 `cultivation`、`cultivate_action` 注册表。其中 `cultivate_action` 随后被用户要求
  **标记为将来可能移除**（`CultivateAction`、`MxtResourceKeys.CULTIVATE_ACTION`、`CultivationModeService`、
  `CultivationActionService` 四处 `//TODO::May be removed`，`docs/数据包格式.md` 的该节与注册表行、`docs/模块实现审计.md`
  与 `research/02_动态注册表清单.md` 同步标注）：它把"当前怎么修炼"放进了数据包注册表，而这件事将来可能整体收回状态附件；标记只是留痕，现在声明它仍然完全受支持。同一批里
  `Holder<Aura> cultivation` 这类**形参/局部变量**全部改成 `aura`（
  `CultivationAttachment.cultivationProgress(Holder<Aura> aura)`、`ResourceService.realmRank(..., Holder<Aura> aura)`、
  `Transition.aura`、`CultivationService` 的整条链、`CultivationActionService` 的 `ActiveCultivation.aura` 等）；而
  `ResourceSubject.cultivation()`（返回 `CultivationAttachment`）与 `Recovery.cultivation()`（一个表示修为量的
  double）保持不变——它们本来就指过程，不是指灵气。
- 脚本式的全局替换踩了一个坑并已修回：`mxt:cultivation` → `mxt:aura` 是**子串**替换，把物品 `mxt:cultivation_jade_slip`
  （功法玉简）改成了 `mxt:aura_jade_slip`，服务端启动时在 `technique_binding` 解析阶段抛
  `Unknown registry key ... mxt:aura_jade_slip`。教训与做法：改 id 前缀要按**完整 id 边界**匹配，事后用
  `git grep -h -o -E 'mxt:cultivation[A-Za-z_]*' HEAD` 列出改动前的全部 id 做核对。
- 验证：`compileJava` / `compileTestModJava` 通过；`runTestServer` → `MiXianTu server audit passed`（改动冻结后重跑确认）。

### 7.4 身份归位：凡是"哪一种灵气"都改成 `Holder<Aura>`（2026-09-17）

§7.2 的结论是错的，用户当场纠正（原话）：

> "Aura 和 Resource 的区别：Resource 只是一个数值系统，数值有啥用 Resource 自身不知道，Aura 才是用来区分哪个灵气的。检查一下代码，尤其是
`ResourceAccess`，应该是传入 aura。"

并给出三条判据：

1. **纯数值没有任何意义，必须看使用场景。** 存在一个叫 `Spirit*` 的字段/存储里的东西，就按灵气算。
2. **环境灵气提供的是一定量的 `Aura`，不是一定量的 `Resource`**，所以世界那一层走的也是 `Aura`；各处传输用
   `Object2IntMap<Holder<Aura>>` 这类形状。
3. `aura_kinds` 留到下一轮（**该轮即 §7.5，已整条删除**）。

先把两个词钉死（这是本轮全部改动的判据）：

| 词          | 是什么                                                                  | 它自己知道什么                                                                                                                |
|------------|----------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `resource` | **纯数值系统**：`default_value`/`min`/`max`/`icon`/`particle_color`/`bars` | 只知道"这个数多大、怎么画"，**不知道自己这个数是干什么用的**                                                                                      |
| `aura`     | **灵气身份**                                                             | 知道"我是哪一种灵气"（`aura_type`、`burst_amount`）以及"我怎么参与修炼"（境界链入口、`regen`、两个换算、`use_condition`）；它引用一个 `resource` 作为自己**被计量的单位** |

也就是说 `Aura.resource()` 是"我用哪个数来计量"，而**不是**"我是什么"——这正是 §7.2 反过来读的地方。

改动分六层，全部改完：

| 层       | 改前                                                                                                                                                                              | 改后                                                                                                                                  |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| 物品/方块存储 | `SpiritStorageComponent(Object2IntMap<Holder<Resource>>)`                                                                                                                       | `Object2IntMap<Holder<Aura>>`，`soleResource()` → `soleAura()`                                                                       |
| 存取协议    | `ResourceAccess`/`ResourceItemAccess`（`Holder<Resource>` 参数）                                                                                                                    | **改名** `AuraAccess`/`AuraItemAccess`，参数全为 `Holder<Aura>`；`SpiritPour.Entry.resource` → `.aura`、`Charge.resource()` → `.aura()`      |
| 燃料定义    | `ItemAura.type: Holder<Resource>`                                                                                                                                               | `Holder<Aura>`（JSON 键仍是 `type`，见下"数据兼容"）                                                                                            |
| 条件      | `HasRealmEntityCondition(Holder<Resource> resource)`、`AuraRangeEntityCondition(Map<Holder<Resource>, AuraRequirement>)`                                                         | `Holder<Aura> aura`（JSON 键 `resource` → `aura`）、`Map<Holder<Aura>, AuraRequirement>`                                                |
| 世界灵气    | `AuraZone.aura`/`BlockAura.aura`/`AuraPool.GROUPED_CODEC`/`AuraChunkAttachment`/`AuraResult`/`AuraService`/`AuraQueryCache`/`FormationAbsorption` 全部 `Map<Holder<Resource>, …>` | 全部 `Map<Holder<Aura>, …>`；连客户端快照与 `AuraStateS2CPayload` 也改成 `Holder<Aura>`（payload 本来就用 `fromCodecWithRegistries`，两端都有同步的 aura 注册表） |
| 修炼收付    | `CultivateAction.auraCosts: Map<Holder<Resource>, NumberProvider>`、`auraGains: List<ResourceGain>`                                                                              | `Map<Holder<Aura>, NumberProvider>`、新增 `AuraGain(Holder<Aura>, NumberProvider)`（形状与 JSON 键 `{id, amount}` 不变）                       |

**刻意仍然按 `Resource` 的地方**（判据 1 的反面：这些字段自己就写着"值"）：

- 玩家池子 `ResourceHolderAttachment` 与 `ResourceService`/`ResourceTransactions`：池子里同时装灵气和纯计数器（
  `mxt_test:sword_mastery`），只能按值开键。
- 公式的 `resource_value.*` 读取与 `FormulaContext.ResourceSubject`：它绑的是"某个值的公式上下文"。`RealmVariable`（
  `realm`/`realm_rank`/`absorbed_aura`/`cultivation_progress`）仍要走一次 value→aura，但这是**语义上必要**
  的一步而不是绕路——纯计数器没有境界，答案就该是"没有"（原实现的 `rank = -1 / progress = 0` 保留）。
- `Technique.mastery_resource`、`mxt:add_resource`、`ResourceCost`/`ResourceGain`、`ResourceBarContext`/`Resource.bars`、
  `mxt:resource_container`（浮点池子副本）：都是"值"或"值组成的池子"。
- `Aura.resource` 字段本身。

**反向查找只剩一个方向。** `AuraLookup` 删掉了整表反查 `byResource`/`holdersByResource`（它们唯一的存在理由就是"
存储把身份丢了、事后要捞回来"），新增 `all(...)` 供"遍历所有灵气"使用；留下的只有 **value → aura** 这一个方向（
`holder(Provider, Holder<Resource>)` 等），因为"这个值是不是带着一门灵气"确实要问，而"这门灵气是哪个值"是它自己的字段、不需要查表。修炼侧的三个
`Resource` 入口（`CultivationService` 的 `remainingProgressCapacity`/`breakthroughStatus`/`pendingConditions`）直接**删除
**（无调用者）；`attempt`/`addProgress` 改成收 `Holder<Aura>`，只保留给"手里只有名字"的命令与脚本用的 `Identifier` 入口，且改为对
`mxt:aura` 注册表解析。`Failure.WRONG_RESOURCE` → `WRONG_AURA`（语言键同步改名）。

**数据完全兼容，一个字节都不用迁。** 判据是"aura id 与 resource id 同名"这个现有约定（`AuraLookup.holder` 就是按
`profile.value().resource()` 的 id 过滤，`ServerCache.rebuild` 硬性拒绝"一个 resource 挂两份档案"，所以 Aura→Resource
是单射），于是同一份 JSON `"mxt:common"` 换个注册表照样解得出。改之前逐字段核过全部数据文件：`item_aura.type`、
`aura_zone.aura`、`block_aura.aura`、`talisman.aura_cost`、`aura_range.aura`、`creature_profile.minimum_aura`、
`cultivate_action.aura_costs`/`aura_gains`、`formation.max_bonus`/`storage.capacity`、`change_aura` 引用的 id *
*全部都有对应的 `mxt:aura` 定义**；四个没有档案的计数器（`sword_mastery`、`divine_sense`、`true_essence`、`channel_probe`
）只出现在 `add_resource` 与 `mastery_resource` 这两个保持 `Resource` 的字段里。唯一真正改了 JSON 键的是 `mxt:has_realm` 的
`resource` → `aura`（两个测试夹具 + 两处文档示例）。

**新增 `aura.*` 显示名。** 既然灵气是独立定义，它就不该借 `resource.*` 的名字：主包加 `aura.mxt.common`，测试包加
`aura.mxt_test.{qi,soul_power,spirit_power,water_power}`（en/zh 各一份），`verifyDisplayNames` 增加 `MxtResourceKeys.AURA`
一行，审计因此在"每个面板会印的名字都有两种语言的翻译"这条上把灵气也覆盖了。

**一次被审计当场抓住的错。** 为了让"用门禁"也走灵气，`ResourceTransactions.tryConsume` 里加了 value→aura 的解析，但把它放在了
`entity != null` 判断**之前**：编队没有阵主时 `entity` 就是 null，`AuraLookup.holder(LivingEntity, …)` 直接 NPE，服务端在
`ServerStarted` 阶段崩掉（`FormationService.maintain` → `tryConsume`）。修法是把解析包回判空里（没有实体就没人可问），另外给
`AuraLookup.holder(LivingEntity, …)` 自己加了空守卫，与它"每个入口都容忍缺失"的既有承诺一致。

**当时留给下一轮**：`aura_kinds`。它当时是第四套身份表示——裸 `Identifier` 字符串（`mxt_test:aura_kind/fire`），既不进
`mxt:resource` 也不进 `mxt:aura`，却参与"能不能修炼"的判定（`AuraZone`/`BlockAura`/`Element`/`CultivateAction`/
`AlchemyRecipe` + `AuraChunkAttachment.auraKinds` 三重集合）。**已在 §7.5 整条删除。**

**验证**：`compileJava` / `compileTestModJava` 通过（顺手清掉 33 个因换类型而失效的 import）；`runTestServer` →
`MiXianTu server audit passed`（第一次因上述 NPE 崩在启动期，修好后重跑通过）。被本轮直接钉住的审计步骤：
`verifySpiritCharge`（`Charge.aura()`、池子仍按值读写）、`verifyTalismanInvocation` 第 18 条（存储表按灵气解码/回环/
`soleAura()`）、`verifyFormationAbsorption`（吸收量按灵气合计、`MaintainRule` 三路拆分按灵气）、`verifyFormationStorage`
（存量与容量按灵气、账单仍按值 id）、`verifyDynamicResourceValues`（`addProgressForChain` 收 `Holder<Aura>`）、
`verifyFormationRuntime`（`max_bonus` 按灵气落在被覆盖的池子上）。

### 7.5 `aura_kinds` 整条删除，"能在哪修炼"交给 condition（2026-09-17）

用户看完全量清单后的判定原话：

> "aura_kinds 约束去掉，改成用 condition 的判断。"

上一轮我把它列成"第四套身份表示，留到下一轮"，这一轮先把它到底在做什么查清（结论写在下面），再删除。**先查清语义这件事是必要的
**——它不是"这里有没有某门灵气"，而是"这块地方算不算某种**类别**的地方"：

- 供给侧 = 覆盖该位置的 `aura_zone.aura_kinds` ∪ 该区块里所有灵气方块发射器的 `block_aura.aura_kinds`（
  `AuraChunkAttachment` 里为此存了 `auraKinds`/`templateAuraKinds`/`blockAuraKinds` 三个集合，还写进区块存档）。
- 需求侧 = `cultivate_action.aura_kinds`、`alchemy_recipe.aura_kinds`。
- 判定 = 集合全包含（`CollectionHelper.containsAllFast`），不满足时修炼**这一 tick 作废**（
  `Result.rejected(Failure.ENVIRONMENT)`，actionbar 报「当前环境不允许修炼」，运功不中断）、炼丹**起不来**。

它与同一份定义里的 `aura` 键基本不重合，因此它其实是**独立的一套词汇**：`overworld` 提供 `spirit_power`+`water_power` 却宣称
`common`；`firelands` 提供 `spirit_power` 却宣称 `fire`；`end_suppressed` 一门灵气都没有却宣称 `void`；灵矿石提供
`spirit_power` 却宣称 `spirit_stone`。数据里 6 个标记（`common`/`fire`/`water`/`void`/`spirit_stone` + Element 那个）只有
`fire` 与 `common` 真被要求过，其余是纯装饰；`Element.aura_kinds` 连读取点都没有。

**删除清单**（判据：它是第四套身份表示，而且"宣称的类别"与"实际有什么灵气"可以互相矛盾）：

| 层        | 删除的东西                                                                                                                                                                                           |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 声明       | `AuraZone.auraKinds`、`BlockAura.auraKinds`、`Element.auraKinds`（死字段）、`CultivateAction.auraKinds`、`AlchemyRecipe.auraKinds`（两份拷贝各一处）                                                              |
| 运行时      | `AuraChunkAttachment` 的三个集合 + 三个 NBT 字段 + `hasAuraKinds` + `refreshAuraKinds`（`initializeAuras`/`setBlockContribution` 各少一个参数）、`AuraResult.auraKinds`、`BlockAuraService` 的收集、`AuraService` 四处接线 |
| 门禁       | `CultivationActionService:137`（AuraResult 路径）、`:192`（区块附件路径）、`AuraDistributionService:49`、`AlchemyWorkstationService:54`                                                                        |
| 只服务于它的代码 | `util/CollectionHelper`（全仓唯一用途就是那三处全包含判定）、`AuraCommand` 的类型行与 `auraKinds()` 助手                                                                                                                  |
| 语言键      | `aura_kind.*`（主包 1 + 测试包 5，各两种语言）、`command.mxt.aura.query.kinds`                                                                                                                                |
| 数据       | 13 个文件去掉 `aura_kinds`（7 个 `aura_zone`、2 个 `block_aura`、3 个 `element`），两个修炼夹具改用 condition                                                                                                        |

**替代方式**：`CultivateAction` 本来就有 `start_condition`（开始一次）与 `condition`（每次结算前），两者都能读环境，所以"
在哪里才能练"由内容方自己写条件。测试夹具的替换是：

- `fire_meditation` → `start_condition` + `condition` = `mxt:aura_range` 要求 `mxt_test:spirit_power` ≥ 90。原来的"火属"
  在这份测试数据里从来没有任何灵气支撑（测试包没有火系灵气，`fire` 只是标签），而"火之地"实际就是 `nether`(100)/`firelands`(
  120) 那两个灵力最浓的地方，所以数值化是同一意图的忠实翻译；两边都写是因为只写 `condition` 的话，在别处起手会先"开始成功"
  再下一 tick 被中止。
- `qingxiao_meditation`（默认行为）→ 直接去掉，默认冥想本就该随处可练。
- 炼丹 → 用 `minimum_aura`（本来就是 `Map<Holder<Aura>, NumberProvider>`），且仓库里一个炼丹配方数据都没有。

**行为变化（有意）**：以前"站错地方"只是这一 tick 不结算、运功继续；现在 `condition` 不成立会**中止**
运功并报「修炼条件未满足」。这是把门禁从"隐性跳过"改成"显式条件"的直接结果，与其它条件（境界、环境 `cultivate_condition`
）一致。另外被阵法吸收的发射器以前仍会贡献类型（`BlockAuraService` 里 `insideFormation` 只影响贡献量），现在连"类型"
这个概念都不存在了。

**验证**：`compileJava` / `compileTestModJava` 通过；`runTestServer` → `MiXianTu server audit passed`。审计里**没有任何断言
**曾钉住这套门禁（全仓 grep `ENVIRONMENT`/`aura_kinds` 在测试模块零命中），所以删除没有削弱覆盖——代价是这条玩法约束从此只由条件夹具间接体现。




