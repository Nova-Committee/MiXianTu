# Saturn 边界与残留审查

## 结论与范围

审查确认抽取时把 MiXianTu 的业务常量、来源协议和少量运行规则带入了 Saturn；没有发现 Saturn 对 `com.iafenvoy.mxt` 的直接 import。下列 SA01–SA11 及 AbilityHooks 扩展项现已修复，原始问题证据保留用于解释改动原因。

修复前最直接的行为问题是通用时间线自动应用天劫灵气倍率。用户提到的 source 也有两种实际残留：`runtime.Sources` 定义了灵根、体质、功法等类别；`SaturnProfilerSources.ITEM/ENTITY` 则曾替 MiXianTu 的物品与实体记耗时，使模块归属显示为 Saturn。

审查对象为 `E:/Java/Saturn` 工作树，HEAD 为 `dae9097`，包含审查开始前已有的触发器、文件访问与 Codec 等未提交修改。覆盖主代码 648 个 Java 文件的依赖、命名、领域词和资源扫描，逐项复核命中及 MiXianTu 调用链，并检查注册出口、公开扩展契约、构建和现有独立验证入口。全量扫描与重点调用链人工复核不等于所有文件逐行完成正确性证明。

最初审查只记录问题并临时接入 Maven Local；随后按用户“修复所有问题”的要求完成两仓修改。原有未提交修改保留。当前验证为 128 项独立检查通过、5/5 组 GameTest 通过（45 条腿全部 OK）、两仓编译和中英文文档构建通过。具体改动见 [106 号稿](../106_Saturn通用边界与来源归属修正.md)。

| 编号 | 优先级 | 问题 | 状态 |
| --- | --- | --- | --- |
| SA01 | P1 | ~~通用时间线自动应用天劫灵气倍率~~ | 已修复 |
| SA02 | P2 | ~~来源类别与阵法来源工厂归属错误~~ | 已修复 |
| SA03 | P2 | ~~授予收集接口接受任意 ID，重算却静默忽略外部命名空间~~ | 已修复 |
| SA04 | P2 | ~~Cost 诊断来源包含 12 个 MiXianTu 业务标签~~ | 已修复 |
| SA05 | P2 | ~~通用 tick 表预置灵气、修炼进度与裂隙阶段~~ | 已修复 |
| SA06 | P2 | ~~突破与进度信号的定义、生产者和匹配器分属两边~~ | 已修复 |
| SA07 | P3 | ~~灵气扣款的子区块身份辅助对象留在通用 Cost 接口内~~ | 已修复 |
| SA08 | P3 | ~~AuraPulse 的 MiXianTu 旧存档兼容实现放在 Saturn~~ | 已修复 |
| SA09 | P2 | ~~MiXianTu 的物品与实体耗时计入 Saturn，相关文案也随之泄露~~ | 已修复 |
| SA10 | P2 | ~~24 个默认注册表的默认 ID 缺少命名空间~~ | 已修复 |
| SA11 | P2 | ~~独立 player-view 验证源有 3 处重复 Override，验证无法编译~~ | 已修复 |

路径说明：下文 `Saturn/` 指 `E:/Java/Saturn`，`MiXianTu/` 指当前仓库。各 SA 节的“修复前证据”描述审查时状态与行号；“已修复”段描述现实现，不能将历史代码片段当作当前代码。

## 为什么会泄露

1. **初始迁移先复制再替换名称，语义清理没有完全收尾。** `Saturn/docs/MIGRATION.md:13` 记录初始 547 个 Java 文件中复制了 498 个；`:219` 记录包名、类前缀和字符串命名空间的批量替换步骤。`Sources` 和 `TimelineContext` 的相关内容在初始提交 `cfe66e8` 就存在，不能归因为最近的触发器修改。
2. **切断类型依赖并不等于切断业务依赖。** `String`、数字阶段、自由类别 record 和公式变量名不需要 import MiXianTu，编译成功也无法说明它们属于通用库。时间线的灵气倍率就是这种遗漏。
3. **把可扩展类别做成 record，只解决了能否新增类别，没有解决常量属于谁。** `Sources.Grant` 和 `Declaration` 已经开放，六个业务 Grant 常量与三个业务 Declaration 常量仍留在 Saturn；其类注释“自带常量都是自身运行时使用”的描述也不符合代码事实。
4. **消费方规范进一步固定了错误归属。** `MiXianTu/learning/house-rules.md:39` 仍要求所有框架来源从 Saturn `Sources` 生成，并将已经改为 record 的类别称为闭集枚举。下一条契约规范也固定了来源字符串。后续修复必须同步调用与规范，避免再次把业务定义移回库。
5. **验证没有覆盖库的独立使用边界。** MiXianTu 装载后会补齐天劫上下文和业务监听器，容易掩盖独立使用时缺失变量、来源归属等问题；当前 player-view 验证还存在单独的编译阻断。只跑信息视图验证也不能证明时间线、授予协议和默认注册表正常。

迁移记录本身可以提及 MiXianTu，它是历史出处说明，不应作为泄露删除。记录中“灵气存储不进入 Saturn”等原则是明确的，实际代码没有完全落实这些原则。

## ~~SA01：通用时间线隐含天劫灵气规则~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/data/timeline/TimelineContext.java:18`。

```java
double value = duration.evaluate(this.formula) * this.scale
        * Math.max(0.0D, 1.0D + this.formula.value("aura_tribulation_modifier"));
```

`IdleEntry.begin/validate` 都调用该方法。无论调用者是否在做天劫，只要使用通用时间线，就会读取一个 MiXianTu 的变量。

- 持续时间 20、显式 scale 为 1：变量为 0 时得到 20，为 0.5 时得到 30，为 -1 时得到无效时长 -1。一个未声明该规则的通用调用因此被改变。
- Saturn 的 `SaturnFormulaVariables` 只提供 zero、random 和实体查询，未提供该变量。未显式传入时会进入 `FormulaVariables.resolve` 的未知变量日志分支，再以 0 回退；生产环境受 `LoggerDispatcher` 的次数上限限制，开发环境没有这项限流。
- MiXianTu 的实际业务入口在 `runtime/tribulation/TribulationService.java:50`，负责读取当地灵气并写入该变量，业务归属很清楚。

建议：`TimelineContext.ticks` 只计算通用的“duration × 显式 scale”；由 MiXianTu 在创建验证、前摇与逐 tick 的上下文时统一计算天劫有效 scale。保持 `WaitForEntry` 的 `unscaledTicks` 截止时限语义，并检查已有 difficulty 公式是否自行包含该变量，防止迁移后重复乘算。

修复验收应包含：独立时间线无灵气变量仍正常且无未知变量日志；MiXianTu 的天劫倍率保持一次性应用；等待条件的超时不被拉长。

**已修复：** Saturn 只计算 duration × scale；MiXianTu 的 TribulationService.effectiveScale 统一计算难度与当地灵气倍率，验证、前摇和逐拍上下文均调用它。unscaledTicks 保持原截止时限。独立时间线检查和 verify/saturn/timeline 已通过，覆盖倍率只乘一次及截止时限不缩放。

## ~~SA02：来源机制通用，内置业务类别不通用~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/runtime/Sources.java:30`、`:43`、`:65`。

| 内容 | Saturn 自己的消费者 | 当前实际归属 |
| --- | --- | --- |
| Grant 的 SPIRIT_ROOT、PHYSIQUE、TECHNIQUE、REALM_STAGE | 无常量消费者 | MiXianTu 修炼模块 |
| Grant 的 CREATURE、CONTRACT | 无常量消费者 | MiXianTu 生物与契约模块 |
| Declaration 的 REALM、TECHNIQUE、PHYSIQUE | 无常量消费者 | MiXianTu 被动属性贡献 |
| `formation(Identifier)` 与 formation 前缀 | 无调用 | MiXianTu 阵法授予与释放 |
| Declaration.ABILITY | PassiveAttributeService | Saturn 自身能力系统 |
| equipment 来源和共享装备来源 | AbilityEventBridge | 通用装备接线 |

交叉证据：MiXianTu 的 `runtime/cultivation/CultivationHooks.java:46`、`runtime/creature/CreatureHooks.java:37`、`data/formation/builtin/BuffFormationAction.java:51`、`runtime/formation/FormationWorldTicker.java:124`、`FormationEntityActions.java:56`。

建议保留 Saturn 的 `SourceLedger`、授予/释放/重算、通用来源生成协议与开放类别模型；业务常量和阵法身份构造归 MiXianTu。`CURIOS` 当前实际代表扩展装备集合，库本身通过 `AbilityHooks.equippedItems` 获取物品，不直接依赖 Curios；它主要是命名残留，可采用中性名称，但不应据此搬走装备机制。

**不能直接把 `saturn:` 全局替换为 `mxt:`。** 来源 ID 已进入台账；生成的 `saturn:passive/...` 属性修饰符也有专门清理逻辑。仅调整代码归属时应先保留字符串。若要改变 ID，必须同时设计账本、属性 ID、清理和重算的兼容策略。

**已修复：** 六个 Grant、三个 Declaration 业务常量及阵法工厂归入 MiXianTu MxtSources，Saturn 保留开放类别和通用账本工厂。扩展装备常量改名 EQUIPPED，仍保存旧 saturn:curios_equipment；既有来源、属性和阵法 ID 不变，调用和规范已同步。旧 Grant/阵法 ID 的 GameTest 已通过。

## ~~SA03：开放的收集参数与实际重算协议不一致~~

修复前证据：`CollectAbilityGrantsEvent.java:33` 接受任意 `Identifier`；`AbilityGrantService.java:42` 对每个来源执行 `if (!Sources.isGranted(source)) continue`；`Sources.java:77` 要求命名空间必须为 Saturn 且路径以 `grant/` 开头。

因此外部模组提交 `example:grant/demo/example/content` 时，事件会接受答案，重算却无提示地忽略；通过 `Sources.granted(new Sources.Grant("demo"), id)` 生成的 Saturn 协议 ID 才会处理。当前 MiXianTu 使用后者，没有据此确认它正在漏授予；这是公共契约对第三方接入的隐性限制。

`docs/API.md` 介绍了来源工厂，但事件方法没有表达这项参数限制。业务常量放在 Saturn 中，也使调用者更容易认为必须使用库预置的业务种类。

建议明确“自动重算管理的来源”和“直接授予的来源”之间的所有权边界：可以保留统一框架 ID 协议并在入口验证、公开说明；也可以提供带所有者的开放协议。不要简单删掉过滤条件，否则物品、脚本或阵法直接授予的来源可能被没有对应声明的全量重算收回。扩展接口和其消费者应采用同一份判定。

**已修复：** AbilityAttachment 新增可选 managed_sources，保存本次收集器管理的任意来源 ID；重算范围包含此前受管来源、旧保留前缀来源与本次声明。先授予再撤回，避免来源切换清空仍有效的能力状态。独立直接授予不进入管理范围。verify/saturn/grants 的 8 条腿全部 OK，覆盖切换、Codec、复制、重载撤回、手动来源保留和旧前缀清理。

## ~~SA04：CostOrigins 带入业务诊断标签~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/data/cost/context/CostOrigins.java:7`。

16 个标签中，Saturn 主代码只消费 `ABILITY`、`CHANNEL_UPKEEP`、`SCRIPT`。`RECIPE` 名称通用但目前没有自身消费者。其余 12 个为：

`BREAKTHROUGH`、`CONTRACT`、`CULTIVATION`、`FORMATION_ACTIVATION`、`FORMATION_MAINTENANCE`、`FORGING`、`ARTIFACT_FLIGHT`、`ARTIFACT_UPKEEP`、`HERB_GROWTH`、`QUALITY_UPGRADE`、`TALISMAN`、`TALISMAN_DRAWING`。

这些标签正在被 MiXianTu 对应服务引用。`CostContext` 的 origin 已经是自由 String，只用于诊断，没有要求库事先登记所有玩法来源。

建议将业务标签移到消费方的常量表，保持原字符串值和同一个 `CostContext`/`CostPayment` 入口。无需重新引入枚举、注册表或第二套付款逻辑；字符串常量本身已可共享。

**已修复：** Saturn CostOrigins 只保留 ABILITY、CHANNEL_UPKEEP、SCRIPT；MiXianTu CostSources 接管 12 个业务标签及 RECIPE，保持原字符串。所有业务消费者迁移完成，继续复用同一个 CostContext 与付款实现，两仓编译通过。

## ~~SA05：通用 tick 阶段表含业务顺序~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/runtime/EntityTickStages.java:25`。

`AURA_REGEN=200`、`PROGRESSION=510`、`RIFTS=900` 分别被 MiXianTu 的 `ResourceRegenStage`、`ProgressionTickStage`、`RiftContactCollector` 使用。Saturn 自身不实现这些阶段。

建议保留统一 `CollectTickStagesEvent`、排序和执行机制；业务模块拥有自己的阶段常量。初次整理保持数值顺序，尤其不能将进度阶段随意移到能力处理之后。`CURIOS` 作为通用扩展装备阶段可中性化命名，和它的旧来源 ID 是两个独立决定。

**已修复：** 业务阶段常量移入 MiXianTu MxtTickStages，数值顺序不变；Saturn 仅保留库内阶段，扩展装备阶段改用 EQUIPMENT。收集、排序和执行仍走原统一出口，所有消费者与探针已编译通过。

## ~~SA06：业务触发信号只搬了一半~~

修复前证据：Saturn 的 `data/trigger/TriggerSignals.java:19`、`runtime/ability/AbilityEventBridge.java:235`；MiXianTu 的 `registry/MxtTriggers.java:20`、`runtime/cultivation/CultivationService.java:103`、`runtime/progression/ProgressionService.java:128`。

`BREAKTHROUGH`、`PROGRESSION_LEVEL` 两个信号常量留在 Saturn；突破发送包装方法 `onBreakthrough` 也留在 Saturn。真正触发时机和对应 matcher 的注册则在 MiXianTu，matcher 的 ID 是 `mxt:*`，匹配的信号却是 `saturn:*`。

建议业务信号常量与生产包装放回当前拥有其规则的模块，继续通过通用 TriggerDispatcher/TriggerPublishing 发布。若要把等级变化定义成公共标准，先补齐其通用契约；目前不能仅因信号名看起来通用，就让库替没有实现的业务拥有它。迁移代码归属时先保持旧信号 ID，改 ID 需同时处理脚本订阅和配置兼容。

**已修复：** MiXianTu MxtTriggerSignals 接管两个业务信号并保留旧 saturn: ID；突破生产者直接调用通用 TriggerPublishing，Saturn 不再提供业务包装方法。匹配器、进度发布和探针同步更新。verify/saturn/ownership 验证两个旧信号 ID 保持不变。

## ~~SA07：CostTarget.Section 是灵气身份辅助对象的搬漏~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/data/cost/CostTarget.java:75`；MiXianTu 的 `data/cost/context/AuraCostSources.java:35`。

Section 只保存维度与子区块坐标；它不实现 CostTarget，而是被 MiXianTu 用作 `WorldAuraAccess` 的库存身份。Saturn 主代码无消费者，注释直接描述 pooled aura。

建议将专用于灵气的身份构造放回 MiXianTu；如果确实需要公共子区块坐标值对象，应以中性工具身份提供，避免将它挂在扣款接口上暗示库具有世界灵气库存。现有开放 CostTarget 足够接入，不需要复制付款框架。

这项主要是职责与接口组织问题，不能据此推断通用 ResourceChannel 仍在访问灵气池：它的实际实现已经只处理 Resource。

**已修复：** 身份对象归入 MiXianTu AuraSection，AuraCostSources 和扣款探针使用本体对象；Saturn CostTarget 不再声明 Section，开放的目标适配与统一扣款机制保留。两仓及全部测试源编译通过。

## ~~SA08：AuraPulse 的兼容职责属于 MiXianTu~~

初始审查只扫描 Saturn 自身引用，发现 `data/storage/runtime/AuraPulse.java` 没有类外消费者，且 Saturn 的存储注册表没有它。由此写出的“无消费者、未注册、不是有效兼容实现”结论不完整，现予更正。

跨仓编译与调用链复核确认：MiXianTu 原有 `registry/MxtDataStorages` 明确注册了该类，ID 是 `mxt:aura_pulse`。它承担 MiXianTu 旧存档读取职责，不能按死代码直接删除；问题是实现归属错误。

**已修复：** 类型迁回 MiXianTu 的 `data/storage/runtime/AuraPulse`，注册点引用本体类型，保留原 `next_tick` Codec 和 `mxt:aura_pulse` ID。Saturn 不再包含该业务类，也没有为它新建注册项。`verify/saturn/ownership` 已实跑，验证真实存储分派的编码、解码及旧 ID 往返成功。真实用户旧世界整体升级尚未实跑。

## ~~SA09：Profiler source 的归属错误已经进入调用链~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/registry/SaturnProfilerSources.java:15`；两份 lang 的 `saturn.profiling.saturn.item.tooltip`、`saturn.profiling.saturn.entity.tooltip`，均在约第 100 行。

Saturn 注册 9 个 source，其中 ITEM 和 ENTITY 在 Saturn 内没有 scope 调用。MiXianTu 却在以下路径使用它们：

- `runtime/item/ItemBindingService`、`QualityService`、`QualityUpgradeService`、`PillService` 使用 `SaturnProfilerSources.ITEM`。
- `entity/VisualProjectileEntity`、`TalismanProjectileEntity`、`SpiritBurstEntity`、`FlyingSwordEntity` 使用 `SaturnProfilerSources.ENTITY`。

所以这并非只是独立安装时出现空桶：同装时，MiXianTu 的物品、丹药、剑气等耗时会被记到 Saturn 的 source。配套 tooltip 也描述品质升降、丹药使用、剑气与符箓弹，与通用库的内容不符。

建议将这两个业务 source 的注册、scope 调用和翻译一起归到 MiXianTu，检查 GUI 排序、筛选与订阅对 ID 的引用。Saturn 的 resource、ability、trigger、cost 及能力子阶段确实测量库自己的运行时，应继续归 Saturn；采样核心也无需迁出。

另有一批低风险旧说明应统一清理：ResourceService 的 absorbed_aura/realm 注释、ResourceChannel 的灵气记账说明、FormulaContext.withResource 的修炼描述，以及 Action/Ability 中的符箓、法器、天劫专用措辞。逐项按实际通用行为重写，不把纯注释残留夸大为实际资源访问。

**已修复：** 两个 source、全部物品/实体 scope 和四个翻译键移入 MiXianTu，ID 为 mxt:item / mxt:entity；Saturn 保留其余 7 个库内 source。verify/saturn/ownership 已验证真实注册表归属。相关旧业务注释已按通用行为改写；中英键集合 Saturn 各 137、MiXianTu 各 1375，一致。

## ~~SA10：默认注册表 ID 不完整，额外暴露边界异常~~

修复前证据：`Saturn/src/main/java/com/iafenvoy/saturn/registry/SaturnRegistries.java:53` 至其工厂 `:83`。

24 个 DefaultedRegistry 都用 `create("no_op"/"empty"/...)` 传入不带命名空间的默认名，工厂直接把字符串交给 `DefaultedMappedRegistry`。当前 Minecraft 源码构造器使用 `Identifier.parse(defaultKey)`，这些默认 ID 因而成为 `minecraft:no_op`、`minecraft:empty` 等。内置条目实际登记在 `saturn:*` 下，默认值不会被对应注册填入。

此外 `TRIGGER_TYPE` 仍选择 `use`，而 Saturn 自带列表没有 `saturn:use`；即使只补命名空间，该默认条目也仍不成立。

实际影响限于会走默认回退的读取路径：以 entity_action_type 为例，注册 `saturn:no_op` 后正常查该 ID 成功，但直接 `getValue(example:missing)` 会在空 defaultValue 上抛 NullPointerException。使用 getOptional 的路径并不一定有同样表现，不能声称所有错误 JSON 都会崩溃。

建议工厂显式构建 Saturn 命名空间 ID，逐个检查默认条目确实存在；触发器另外选定有效的默认项。为每个默认注册表验证“默认 ID 已注册”和缺失项/越界 ID 的读取行为。该项是审查中额外发现的通用健壮性缺陷，不能仅据此断言它是 MiXianTu 专属逻辑泄露。

**已修复：** 默认注册表工厂显式使用 saturn: 命名空间，TRIGGER_TYPE 默认项改为实际存在的 tick。独立检查已通过；verify/saturn/defaults 对 24 个真实注册表逐一验证默认条目存在、缺失 ID 回退和越界数字 ID 回退，25 条腿全部 OK。

## ~~SA11：现有独立验证入口被重复注解阻断~~

修复前证据：`Saturn/src/verification/java/com/iafenvoy/saturn/view/PlayerViewVerification.java:382`、`:408`、`:411` 附近。

匿名 getter 的 valueCodec 与 FixtureGetter 的 valueCodec/readSaved 各连续声明两次 `@Override`。执行 verifyPlayerViews 时得到 3 个编译错误，JavaExec 验证主体没有运行。

建议移除重复注解并实际重跑验证。这是验证源的问题，不能把 Saturn 主代码编译成功或 Maven 发布成功记录为该验证通过。初次审查仅记录了此问题，后续修复与实跑结果如下。

**已修复：** 移除 3 处重复注解后实跑 verifyPlayerViews，84 项检查通过。新增 verifyFramework 独立覆盖通用边界，44 项通过；独立验证合计 128 项通过。

## ~~扩展性开放项：AbilityHooks 的单宿主假设~~

修复前使用进程级单个 Installed.current，后一次 set 会覆盖前一次。当前 MiXianTu 单宿主可工作，但多消费模组没有组合规则。

**已修复：** 新增按 owner Identifier 注册的多提供者组合，缓存不可变快照。倍率相乘，许可全部通过，提交回调依注册顺序执行，能力取去重并集，装备按 ItemStack 对象身份去重；同一 owner 只替换自身。旧 set 保留为独立兼容槽位，不再覆盖具名提供者。MiXianTu 使用 mxt:abilities 注册。

独立验证已覆盖倍率、许可、回调、替换与移除；verify/saturn/hooks 在与 MiXianTu 同装时验证能力、装备并集及独立移除，3 条腿全部 OK。实现位于 runtime/ability，api 只暴露契约。

## 应继续保留的通用机制

- SourceLedger、来源引用计数、统一能力状态与对账；问题在业务类别和契约表达，不能再造第二套账本。
- CostContext 的自由扩展、CostTarget 和统一两遍付款；灵气应继续由 MiXianTu 适配。
- 通用 Resource、Ability、Action/Condition、Timeline、Trigger、HUD、Information 和 profiler 核心。这些均在 Saturn 的公开功能范围内。
- `AuraAbilityType` 的周期半径效果是通常意义的“光环”，不读取 MiXianTu 的世界灵气，不能因名字含 aura 就判为泄露。
- 显式启用的别名机制。SaturnAliases 初始没有 mxt 命名空间，是 MiXianTu 构造器调用 `add(MOD_ID)` 才建立别名；库文档中的旧 ID 示例属于迁移说明。
- 16 个信息行、3 个组和 3 份标签属于通用玩家事实展示。审查时资源 JSON 共 25 个，均可解析；移出四个业务翻译键后，中英 lang 各 137 键，键集合一致。未发现灵根、境界、灵气等玩法定义进入 Saturn 的数据文件。

## 当前兼容边界

来源类别、诊断名称、业务阶段和信号的代码归属已调整。来源台账、被动属性、阵法来源、业务信号、旧灵气存储的既有 ID 保留；附件新增的 managed_sources 缺失时按空集合读取，旧 saturn:grant/... 来源仍进入兼容重算。

物品与实体 profiler ID 调整为 mxt:item / mxt:entity，采样窗口不属于存档玩法状态。Java 接入方需重新编译并迁移已归 MiXianTu 的业务常量引用；本轮不承诺旧二进制调用兼容。Saturn 自身的来源账本、统一付款、触发器和阶段调度继续各保留一套。

## 修复验证与发布依赖接入

审查验证阶段按用户要求，将修复工作树发布到 Maven Local，使用独立的 -audit-local 后缀。当时由 mxtLocalSaturnVersion 临时切换本地依赖，本地仓库仅对 com.iafenvoy.saturn:Saturn 开放。

用户提供正式发布坐标后，当前依赖已切换为 `curse.maven:saturn-lib-1767455:9538254`，临时本地属性、仓库与选择分支已移除。模组元数据同步要求 Saturn 至少为 1.3，避免安装缺少所需 API 的旧库。

发布 Jar 的元数据确认为 Saturn 1.3，792 个 class 与已构建验证的本地发行 Jar 逐字节一致；使用发布坐标的主代码和测试源编译通过，dependencyInsight 确认选中上述 CurseMaven 坐标。此次依赖切换没有重新启动 GameTest，下面的 5 组游戏测试结果对应之前的本地审查构建。

| 检查 | 修复后的实际结果 |
| --- | --- |
| Saturn compileJava、publishToMavenLocal | 成功，发布修复后的工作树 |
| Saturn verifyPlayerViews | 84 项检查通过；损坏数据夹具产生的预期 WARN 不算失败 |
| Saturn verifyFramework | 44 项检查通过，和上一项合计 128 项 |
| MiXianTu compileJava、compileTestModJava | 使用最终本地 Saturn Jar 成功 |
| runtimeClasspath dependencyInsight 与 GameTest 加载日志 | 只使用本地 Saturn，未同时引入原 CurseMaven Saturn |
| 当前 CurseMaven 依赖与最终编译 | 发布坐标解析成功，主代码、测试源及依赖元数据生成通过；792 个 class 与本地发行 Jar 一致 |
| verify/saturn GameTest | 5/5 组通过、45 条腿全部 OK，Gradle 退出码 0 |
| 两模组 lang 键集合 | Saturn 137/137，MiXianTu 1375/1375，中英一致 |
| 中英文文档站构建 | 成功；两语言 API 与开发模式页面已同步 |
| 客户端、完整旧世界升级、其他 GameTest | 未实跑；本轮授权范围为新增 5 组 |

GameTest 入口为 src/test-mod/java/com/iafenvoy/mxt/probe/verify/SaturnBoundaryProbes.java，结果日志为 build/saturn-audit/game-tests.log。成功标志为 All 5 required tests passed 与 BUILD SUCCESSFUL；单条腿为 saturn ...: OK。

```powershell
.\gradlew.bat runGameTestServer '-PmxtGameTestSelection=mxt_test:verify/saturn/*' --console=plain
```

Saturn 仓库中的独立验证入口：

```powershell
.\gradlew.bat compileJava verifyPlayerViews verifyFramework --console=plain
```

修复后的本地 Jar SHA-256 为 EB348422C121E75FB6CBF493CA351192797650B4CB99A859821244F97E3A85C9。独立验证日志位于 Saturn/build/framework-audit-final.log，文档构建日志位于 MiXianTu/build/saturn-audit/docs-build.log；build 下产物执行 clean 后会消失。

### 修复前复现的历史记录

初始本地 Jar 的 SHA-256 为 80D9394E025C8570952B3319F0797E7C2B27133861F5CE164572E1EE9064189B。原临时 BoundaryAudit.java / audit.init.gradle 共确认 11 条缺陷观察（含对照与 Jar 来源，并非 11 个不同故障）：20 tick 被灵气变量拉长到 30，变量 -1 令时长无效；外部来源被忽略；缺失动作回退在空默认值上失败。

这些脚本断言的是修复前缺陷存在，保留仅供追溯，不能作为当前成功回归入口。当前应使用 verifyFramework、verifyPlayerViews 和上述 5 组 GameTest。
