---
title: 功法、进度链与熟练度
---

一门会升级的功法由三张表拼成：`progression`（进度链上的一级一个文件）、`resource`（衡量熟练度的数值）、`technique`（把前两者接到自己身上，并说明每一级给什么、要什么）。等级不是第四张表：**链就是 `next_level` 串出来的那条直线**，没有任何一级指向它的那一级是入口，`ServerCache` 在启动与数据包重载时沿 `next_level` 给整条链编号（入口为 `0`）。链上**没有身份字段**，所以同一条链可以被多个持有者共用，各自用自己的 `default_level` 进入。

字段级说明见 [`docs/数据包格式.md`](../../数据包格式.md) 的 `progression` 与 `technique` 两节，这里只写"三张表怎么拼、什么时候会发生什么"。

## 晋升的条件与时机

服务端**每 20 tick** 对每个 `LivingEntity` 跑一次检查（`AbilityEventBridge` 的慢拍 → `TechniqueMasteryService.tick`），对身上每一门已学功法逐级往上走：下一级的 `mastery` 要求被 `mastery_resource` 指名的那个数值满足、且该级的 `condition` 成立就晋升，一次检查最多连升 64 级，**不会跳级**。两个门槛都要过——`mastery` 是数值，`condition` 是其它一切（境界、状态、场地），只想要数值就写 `mxt:always`。

两个解析期硬约束：`mastery_resource` 与 `configuration` 都**要求 `default_level`**（没有入口就没有可爬的链）；链重建时还会拒绝"后一级的 `mastery` 比前一级低"的链条（`lowers its mastery requirement at level <id>`），因为晋升只拿"下一级"做比较。`condition` 不要求入口级——**不存在"晋升进入口级"这件事**，入口级写了 `condition` 也只被解析。

晋升是**先提交、后发信号**：`ProgressionAttachment` 里写上新等级，然后按新等级重算能力授予（`granted_abilities` 加上已经到达过的每一级的 `ability`，因为 `ability` 是最低要求），最后发布一次 `mxt:progression_level`（公式变量 `level` 是刚到达的链内序号，扩展值 `owner` 是持有者定义的 id，今天就是功法 id）。等级只升不降：**没有任何路径会降低已经拿到的等级**，花掉熟练度数值只会推迟下一级。

## 熟练度只是一个数值

本体不认识"功法熟练度"这个概念：`mastery_resource` 指的是 `resource` 表里的一个数，链上每一级的 `mastery` 是这个数要达到多少，而**这个数怎么涨完全由内容包决定**。所以能写 `mxt:add_resource` 的地方都是熟练度的来源，按宿主分四类：

- **技能**：会跑动作的五个类型（`mxt:active` / `triggered` / `channelled` / `aura` / `interval`）各自声明 `entity_action` 等四个动作字段，把行为写进去就是"用技能涨熟练度"。注意**技能不知道"我来自哪门功法"**：同一条技能被境界、丹药、灵根等多个来源授予时，那几条来源都会往同一个数值里加。
- **触发规则**（`mxt/trigger/`）：`{"trigger": …, "condition": …, "action": …}`，动作跑在信号携带的 actor 上，规则自己的 `chance` / `cooldown` 用来限频。可挂的信号是内置 11 条（`tick` / `attack` / `hurt` / `kill` / `block_break` / `block_use` / `item_use` / `equip` / `death` / `breakthrough` / `progression_level`）加上移植的原版触发器 30 条（`consume_item`、`changed_dimension`、`slept_in_bed`、`player_killed_entity` …，见 `TriggerSignals`）。
- **修炼档案**：`cultivate_action`（结算成功那一拍跑一次）与 `tick_action`（每个 tick 都跑）。
- **其它实体行为宿主**：`realm_stage.success_action` / `fail_action`、`tribulation` 的时间线与成败行为、`curse` 的四个钩子、`formation` 的三个实体钩子、`forging_blueprint.complete_action`、`weapon_binding` / `item_binding` / `item_aura.exhausted_action`、`pill.on_consume` / `on_overdose`、`secret_realm.enter_action` / `exit_action`、`contract_type` / `creature_profile`、`element_reaction.action`。

不写任何行为也会涨的有两条**灵气**通路，因为它们本来就往"灵气引用的那个 `resource`"里写：`aura` 的 `regen` 每 tick 自然回复（正在修炼该链时让位给修炼结算），以及修炼结算（`regen × absorb_amount × 亲和 × 灵气速度`，先填容量、溢出才转修为，另有 `aura_gains` 与双向换算）。**把 `mastery_resource` 指成自己打坐用的那个数值，修炼本身就是熟练度来源。**

数值同样会被扣：任何 `Cost`、`TransferResourceBiEntityAction`、`mxt:spirit_vessel` 都能把它拿走。资源有 `min` / `max`，越界变更**被钳到边界而不是被拒绝**，所以门槛高于 `max` 时那条链永远爬不到顶，且不报错。命令侧的 `/mxt resource <id> set <value>` 直接改写附件里的值，**不走 `ResourceService`**，因此不受钳制。

## 读状态与运维

- 条件侧：`mxt:technique` 问"学过哪些"（读的是**学过**，功法没有启用开关），`mxt:progression` 问"爬到了哪一级"（`comparison` 取 `exact` / `at_least` / `at_most`，比较走缓存索引出来的链内序号；可选用 `owner` 限定到某些持有者定义）。KubeJS 侧用 `MxtTechniques.level(entity, technique)`。
- 显示：功法面板（`Z` 进人物信息面板里的按钮，或那条未绑定按键）画出每门功法的等级与熟练度进度条，进度条的染色取该数值的 `particle_color`。
- 命令：`/mxt registries validate` 一次报出链的问题（`next_level <id> is not a progression`、`follows both <A> and <B>`、`chain is cyclic, or joins another chain, at level <id>`、`lowers its mastery requirement at level <id>`、`enters the unknown progression level <id>`、`does not configure the progression level <id>`、`configures progression level <id>, which it can never reach from <entry>`）；`/mxt trigger rules <signal>` 看某个信号挂了几条规则；`/mxt trigger publish <signal> [entity]` 手动发一次信号，是验熟练度来源最快的办法；`/mxt resource <id>` 读数值，`/mxt resource <id> set <value>` 直接改。

**纪录清理**：包改了某门持有者的入口等级之后，身体里那条走不到的等级记录会在**玩家登录**与**数据包重载**时被清掉（`ProgressionService.pruneForeignLevels`，只在两个低频繁入口跑，不在每次读取时跑），该持有者退回自己的入口级；每清一条打一条带持有者 id 与等级 id 的 `WARN`。这条检查只覆盖**当前仍持有的持有者**，且数据包重载那次只扫在线玩家。

`progression` 与 `ProgressionAttachment` 是通用层（`{持有者定义 id → 等级}`），今天只有功法接上去；灵宠等其它系统接进来时复用同一份服务与同一条链，不需要新的链注册表。
