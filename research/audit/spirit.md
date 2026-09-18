# 灵石灌注（`AuraItemAccess` 反向使用 `item_aura`）审计

审计基准：MC 26.1.2 / NeoForge 26.1.2.99。
结论来自直接读反编译源码（`Item`、`Consumable`、`LivingEntity`、`MultiPlayerGameMode`、`ServerPlayerGameMode`）与本工作区实测（服务端审计 `MiXianTu server audit passed`）。本文件是"先记录、后动手"的持久化笔记。

> 变更记录
> - 2026-09-17：新增本文件。按用户要求落地"实现了 `AuraItemAccess` 的物品都可以被按住右键灌注灵气"。
> - 2026-09-17：**灌注参数可以由物品自己声明**（见 §3.1，为符箓载体接入）。`AuraItemAccess` 增加 `pour(Provider, FormulaContext, ItemStack)`（默认空 = 沿用 `item_aura` 那条共享读法）与 `onChargeFilled(holder, stack)`（容量填满时回调一次）；`SpiritChargeService` 改为先问物品、没有才回落定义，`Charge` 变成已求值的读数。灵石路径按 §2 的口径一格未变。同时修掉计费的一个上界问题：实付被上限从上方裁掉时，`before - after` 会把被裁掉的量算成实付（`units = floor(实付 / 单价)`），现在取 `min(请求量, …)`。符箓侧的细节见 `research/audit/talisman.md`。
> - 2026-09-17：**按用户两条意见收敛接口**。① `pour` 只回答"我是什么"（按资源分列的 `stored`/`capacity`，顺序即灌注顺序），每 tick 的注入与扣费收归手势（`SpiritChargeService.POUR_INTAKE_PER_TICK`/`POUR_COST_PER_UNIT`），求值上下文参数随之删除——唯一实现必须忽略它，签名里不该有。② "满了之后做什么"改为**由写入者汇报**：`onChargeFilled(LivingEntity, ItemStack)` → `onCharged(SpiritSource, ItemStack)`，`SpiritSource(level, position, actor)` 同时带位置与行为者，展示架（`DisplayStandBlockEntity.add`）在真实写入后汇报它自己 `worldPosition` 的位置。详见 §3.1、§3.2。
> - 2026-09-17：**修掉"展示架灌不进去"**（用户实测反馈："展示台的灌注代码好像没挂载上"）。灵力射线（`SpiritBurstEntity`）原来只按方块的**碰撞形状**判定能不能命中，而展示架把所存物品显示在方块上方 **1.65**、碰撞形状只到 **1.4375**，于是"对着架上那件物品打"这个唯一自然的瞄法永远从架子上方飞过，一滴都灌不进去。现在容器按"**所在格子 ＋ 上面一格**"（`SPIRIT_REACH_HEIGHT = 2.0`）判定，且仍然服从"更近的方块先赢"，不会穿墙灌注。审计新增 `verifyDisplayStandPour`（§5 第 7 条）。
> - 2026-09-17：**按拆分后的语义统一命名与存储**（用户提问："`SpiritAccess` 和 `SpiritItemAccess` 还是传入的 `Resource`，这个语义是不是不对了"）。当时的结论是"**参数类型没错，是名字和两处遗留不对**"，据此① 两个接口改名为 `ResourceAccess`/`ResourceItemAccess`（41 处引用 + docs/research/README）；② `SpiritBurstEntity` 的 `auraType`（`Holder<Resource>`）改名 `auraResource`，与档案的 `aura_type`（`Holder<Element>`）脱钩；③ `SpiritStorageComponent` 由单个 `int amount` 变成记下数值类型的形状；④ 补上拆分漏掉的默认数据迁移。详见 §7。**这条结论本身是错的，已被 §7.7 推翻（接口再次改名 `AuraAccess`/`AuraItemAccess`，参数改成 `Holder<Aura>`）。**
> - 2026-09-17：**两个整单位存储合并成一个形状**（用户提示："我记得有个物品组件用来存灵气（Map存储，支持多个），做的通用一些"）。仓库里已有的通用件是 `mxt:resource_container`（浮点、持有者池子的便携副本，见 §7.6），不适合灌注契约；因此把**同为整单位**的两个合成一个：`mxt:spirit_storage` 变成一张「资源 → 已存单位」的表（`{amounts:{...}}`），`mxt:talisman_charge` 与 `TalismanChargeComponent` 删除。键就是"装的是哪种东西"，所以 §7.3 想要的那条语义不再需要额外字段；缺组件读作"满/空"仍由物品回答。**键类型随后由 `Holder<Resource>` 改为 `Holder<Aura>`，见 §7.7。**详见 §7.3 与 §7.6；新增审计第 18 条（`verifyTalismanInvocation`）把组件对外的 JSON 形状、单资源读取与"清空即删键"一起钉住。
> - 2026-09-17：**身份归位，接口参数改成 `Holder<Aura>`**（用户："`Resource` 只是一个数值系统，数值有啥用 Resource 自身不知道，`Aura` 才是用来区分哪个灵气的……应该是传入 aura"）。§7.1 的结论被推翻：`ResourceAccess`/`ResourceItemAccess` 再次改名 `AuraAccess`/`AuraItemAccess` 且参数全为 `Holder<Aura>`，`SpiritStorageComponent`/`SpiritPour.Entry`/`Charge`/`ItemAura.type`/`has_realm`/世界灵气（含客户端快照与 S2C 包）/`aura_costs`/`aura_gains`/阵法存量全部改键，`AuraLookup` 的整表反查删除。详见 §7.7 与 `research/audit/resource-cultivation-split.md` §7.4。
> - 2026-09-17：**「灌满就被按住的手激发」的机制查清，实现交由用户自己写**（用户："长按的时候充能，但是当充满之后因为还是按下，会被判定为需要激发导致被激活，应该设计为必须松手再按才会激发"；随后："POURING 相关的代码都删了吧，我自己来写"）。**机制**（这条是结论，留着）：灌注是一轮使用周期，**跑完时按钮还按着**——客户端 `Minecraft.handleKeybinds` 发现 `isUsingItem()` 为假而键仍按下，就再 `startUseItem()` 发一次 `ServerboundUseItemPacket`，服务端于是对**同一只还按着的手**再调一次 `Item#use`；此时载体刚好满（`store` 模式尤其明显，因为它不会被 `fire` 那样烧掉、满的状态会留下来），`TalismanItem.use` 就把它当成一次主动激发。**两次失败的尝试也记在这里，免得重走**：① 用"这一 tick 是否灌过"的**时间窗**（`getGameTime() - at <= 1`）——用户实测"第一 tick 确实没激发，但是第二 tick 激发了"，因为窗口赌发包延迟，而按住不放时之后**每一 tick** 都是一次新的 use，窗口必然过期；② 用"这一轮正在灌注"的**闩**（`Set<UUID>` + `LivingEntityUseItemEvent.Stop`/`Finish` 清除 + `pouredJustNow` 查询）——逻辑上自洽，但用户决定自己写，故**已从仓库删除**（`SpiritChargeService` 的 `POURING`、两个事件处理器、`pouredJustNow`、`TalismanItem.use` 里的闸、以及 `actionbar.mxt.talisman.release` 文案全部撤掉）。用户看过原版 `CrossbowItem` 作为参照：它用 `isCharged()` 当**状态**、`use()` 在未装填时才 `startUsingItem`、`useOnRelease`/`releaseUsing` 负责松手发射——注意弩是"松手即发射"，与本需求的"松手再按"不同，只能借状态机不能照抄发射时机。
> - 2026-09-17：**潜行右键不再被长按接管**（用户实测反馈："现在切换无效，应该是代码优先级的问题"）。符箓加了"潜行右键切模式"之后切不动，根因不在符箓：`HoldService.onItemUse`（`priority = LOWEST`）在**右键那一刻**就把"账单未满"的载体武装成了长按——`SpiritChargeHold.claims` → `holdTicks` 对未满载体返回正数，于是 `arm` 给栈写上 `minecraft:consumable`，原版随后走"持续使用"那条路，**`TalismanItem.use` 根本不会被调用**，潜行分支没有机会执行。这也解释了为什么"满符能切、没满的符切不了"（满符 `holdTicks == NO_HOLD`，`claims` 为假，不被武装、`use` 正常执行）。修法：在 `onItemUse` 最前面加 `if (event.getEntity().isShiftKeyDown()) return;`——潜行点击永远不是长按，让位给物品自己的 `use`。放在这里而不是放进 `claims` 的理由：能不能长按是**栈**的性质，谁在点它不是。注意这条闸是**手势级**的，因此所有长按物都受影响（功法阅读、灵石灌注同样不再被潜行右键武装），不是只针对符箓。**未实测**。
> - 2026-09-17：**测试数据包不再写本体物品的定义**（用户实测："物品放到展示台出现问题：正常放手上正常激活符箓，但是放展示台上之后无法激活……放台子上的 stack 里面存的是 `mxt:common`，但是我使用的灵气始终是 `mxt_test` 里面的"）。根因不在灌注：`ItemAuraService.find` 按 `items` 匹配物品、取注册表顺序的**第一条**（`ItemAuraService.java:43-49`），而测试模组为本体物品写了三条同名定义——`mxt_test:item_aura/spirit_stone`（`type: mxt_test:spirit_power`，匹配 `mxt:spirit_stone`）、`mxt_test:block_aura/spirit_stone_block`、`mxt_test:currency/spirit_stone`——本体那三条永远排在前，于是测试那三条**从未生效**（§5 第 7 条与 `verifyDisplayStandPour` 的历史注释、`verifyFormationAbsorption` 对 `mxt:common` 的断言都记着这件事）。用户拿 `mxt_test:spirit_power` 的灵爆打展示台时，本体灵石按自己的定义只收 `mxt:common`（`SpiritStoneItem.insert` 首行的类型判定），`insert` 全量退回 ⇒ `remaining == amount` ⇒ 连 `onCharged` 都不调（`DisplayStandBlockEntity.insert`），符箓一次也不发动且无任何报错。修法：删掉测试包那三份同名定义（用户："本体里面不要放任何无关的测试内容，只保留内置物品的相关定义"），审计里依赖"石头是 `mxt_test:spirit_power`"的断言改回 `mxt:common`，并新增一条守卫——四档灵石都必须解析到本体定义、`type` 都是 `mxt:common`，以后再被遮蔽会当场报错而不是静默失效。
> - 2026-09-17：**本文提到的那些名字跟着注册表改名**（用户："`cultivation_technique` 改成 `technique`，`cultivation` 改成 `aura`"）：`CultivationProfile` → `Aura`（移入 `data/aura/`）、`CultivationProfiles` → `AuraLookup`（移入 `runtime/aura/`）、`MxtResourceKeys.CULTIVATION` → `AURA`、注册表 `mxt:cultivation` → `mxt:aura`（目录 `data/<ns>/mxt/aura/`）。四个词各指什么见 `research/audit/resource-cultivation-split.md` §7.3。**顺带记一个坑**：`mxt:cultivation` → `mxt:aura` 是子串替换，误把物品 `mxt:cultivation_jade_slip`（功法玉简）改成 `mxt:aura_jade_slip`，服务端启动在 `technique_binding` 解析阶段直接失败（`Unknown registry key ... mxt:aura_jade_slip`）；已修回，并用改动前的完整 id 列表（`git grep -h -o -E 'mxt:cultivation[A-Za-z_]*' HEAD`）核对过没有第二处。

## 1. 需求与选型

用户原话："设计一下看看，能不能让实现了 `SpriteItemAccess` 的物品都可以 CONSUMABLE，行为是往物品充入灵气"（仓里没有 `SpriteItemAccess`，按 `AuraItemAccess` 理解）。"CONSUMABLE" 指的是上一轮从功法系统里抽出来的长按模块——让这些物品获得"按住使用"的手势。

三个分叉由用户拍定：

| 分叉 | 选择 | 备选与理由 |
| --- | --- | --- |
| 灵气来源 | **持有者自身灵气池** | 环境灵气（`AuraService.consume` 会真的扣区块库存、由 regen 恢复；`FormationAbsorption.environmentSupply` 则是另一种先例：不扣库存、只抵消账单）。环境方案的诗意更足，但配合 `release_speed > consume_speed` 等于把免费的环境/再生灵气放大成自身灵气，需要额外平衡闸门 |
| 手势长度 | **按容量派生（充满即止）** | 固定窗口（简单，但普通灵石要按三次）。派生需要给 `HoldBinding` 加栈级时长重载 |
| 匹配方式 | **接口即闸门** | 新开 `charge_binding` 数据包注册表（界面更全，但"实现接口即可"就不是自动的了） |

未采用、但值得记下的一条：在 `item_aura` 上加可选 `charge_speed`/`charge_cost` 字段（默认沿用现成两个）。现成两个字段的耦合是真实存在的——`consume_speed` 同时决定"烧多久"和"充多快"——但对现有内容（四档灵石都是 `consume_speed: 1`）没有影响，所以先不加字段。

## 2. 源码事实（设计所依赖的全部前提）

1. `Item.use` / `getUseDuration` / `getUseAnimation` / `finishUsingItem` 全部读 `DataComponents.CONSUMABLE`。**没有该组件的物品 `use` 返回 `PASS`、`getUseDuration` 返回 0**，因此"不武装"就等于原版什么都不做，也不会触发 `LivingEntityUseItemEvent.Start`——这正是"某一堆不归我驱动"能安全表达成"不写组件"的依据。
2. `CommonHooks.onItemRightClick` 在客户端与服务端都于 `ItemStack.use` **之前**触发；但 `MultiPlayerGameMode.startPrediction` 是**无条件** `send` 那个包的（取消分支也照样返回包）。所以**客户端取消只是取消本地预测，服务端仍会独立判一遍**：拒绝的权威必须在服务端，客户端取消只省掉一个幻影姿势。反过来说，客户端"允许"而服务端拒绝＝幻影姿势；客户端"拒绝"而服务端允许＝无姿势但服务端真的会做。结论：只有**两端答案必然一致**的条件才适合做成点击期拒绝，动态条件（如付不起）只能在 tick 内报告。
3. 服务端 `LivingEntity.useItem` 就是手槽里那个**活对象**（`updatingUsingItem` 每 tick 用 `getItemInHand` 回填，`itemStack == this.useItem` 时继续），所以在 `Tick` 事件里写 `event.getItem()` 就是写到真身上；而 `Finish` 携带的是 `useItem.copy()` 的**副本**（这个坑上一轮踩过：清副本的组件等于没清）。
4. `completeUsingItem` 只在计数走到 0 时触发；`HoldService` 已在 `Start` 摘掉组件，所以 `finishUsingItem` 读不到 `CONSUMABLE`，返回原堆——不消耗、不加 `ITEM_USED`、不发 EAT 事件。
5. `Consumable.shouldEmitParticlesAndSounds` 的节奏是私有常量 `r % 4`，窗口开在 `consumeTicks() * 0.21875` 之后；时长不足约 19 tick 就永远不出声。这也解释了上一轮"`learn_time` 4/5 没有声音"。**灌注的音效因此必须是短音**，否则每 4 tick 一次会把长音叠成嗡鸣。
6. `MxtDataComponents.register` 一律 `persistent(...).networkSynchronized(...)`，所以 `mxt:spirit_storage` 在客户端有同步副本：两端对"是否已满/有多少"的答案一致，栈级闸门可以两端同时判。
7. `MxtDatapackRegistries.registry(key)`（无 `Provider` 的重载）在客户端直接抛异常；`ItemAuraService.find(ItemStack)`/`type(ItemStack)`/`capacity(ItemStack)` 走 `ServerLifecycleHooks.getCurrentServer()`，**客户端恒为 0/空**。所以栈级判断必须带 `Provider` 求值（`level.registryAccess()` 两端都有，且 `item_aura` 是同步到客户端的数据包注册表）。
8. `ItemStack.split` 返回**副本**并把原堆清空，所以燃放路径里的"抽空 + 归还"发生在副本上：测试不能拿原对象读结果（第一版审计就是这么写错的，日志里表现为 `TickResult[consumed=0.0, ..., active=false]`）。
9. `ResourceHolderAttachment` 只存值；`ResourceService.change` 会按定义上下限 clamp。`ResourceUseService.canUse` 只在 `ResourceTransactions.tryConsume` 一类路径上生效。

## 3. 落地

| 文件 | 内容 |
| --- | --- |
| `runtime/spirit/SpiritStorageEntry` | 唯一按**能力**匹配的 `ItemMatcher.Entry`：`matches` = `stack.getItem() instanceof AuraItemAccess`；codec 是 `MapCodec.unit`，所以数据包可以写 `{"type": "mxt:spirit_storage"}`。它放在接口旁边而不是 `util/matcher/builtin`，因为它是唯一依赖 `runtime` 的条目 |
| `registry/MxtItemMatchers` | 注册 `spirit_storage` 条目类型 |
| `runtime/spirit/SpiritChargeHold` | 声明：`requiresHold()==true`、物品级时长 `NO_HOLD`（长度属于栈）、姿势 `BLOCK`、音效 `AMETHYST_BLOCK_CHIME` |
| `runtime/spirit/SpiritChargeService` | `initialize()` 注册 `HoldSource`；`onUseTick` 灌注；`onItemUse` 只做反馈（从不 cancel）；`resolve(Provider, stack[, ctx])` 是唯一读数入口；`Charge`（`stored`/`capacity`/`percentage`/`full`/`holdTicks`/`intake`/`costPerUnit`） |
| `data/item/HoldBinding` | 新增栈级 `holdTicks(Provider, ItemStack)`（默认回落物品级）与 `claims(Provider, ItemStack)`（默认 `> NO_HOLD`）；`NO_HOLD` 的语义扩展为"这一堆不归我驱动" |
| `runtime/hold/HoldService` | 三处（武装/摘除/保活）改问 `claims`，新增 `ours(...)` 守卫，`arm`/`armQuietly`/`keepArmed`/`playHoldSound` 带 `Provider` |
| `data/aura/SpiritStorageTooltipAppender` | 删除自带的 `Charge` 模型与配色，改为委托 `SpiritChargeService`（tooltip、Jade、`mxt:spirit_storage_not_full`、灌注四处同一份读数） |

**每 tick 的算术**（服务端，`onUseTick`）：

```text
want    = max(1, floor(consume_speed(ctx) × 堆叠))
units   = want - access.add(..., want, simulate = true)      // 物品真正吃得下的量
unitCost= release_speed(ctx) / consume_speed(ctx)
units   = min(units, floor(持有者该资源 / unitCost))         // 付得起的整单位，先算后付
before  = 持有者该资源
ResourceService.change(持有者, 资源, -(units × unitCost))     // 实付 = before - 结果值
units   = floor(实付 / unitCost)                             // 被上下限裁剪时按实付重算
access.add(..., units, simulate = false)
```

**窗口**：`clamp(ceil(capacity / intake), 1, 200)`。按**容量**而非缺口推导，因为音效节奏每 tick 都要重算这个长度，缺口驱动的长度会随充能进度缩短并把声音带走。代价是"半满的物品会提前灌满、姿势剩余部分空转"——可由玩家提前松手。

### 3.1 物品自己声明它的存储（2026-09-17 追加，同日两次收敛）

`item_aura` 是"一个物品"的共享语言，容量按堆叠放大；但有一类物品的容量取决于**这一堆上写了什么**（符箓载体的容量 = 所铭刻定义的 `aura_cost` 合计，且按灵气分列），只有堆自己能回答。因此：

- `AuraItemAccess.pour(Provider, ItemStack)` 返回按灵气分列的 `SpiritPour`（每种灵气 `stored`/`capacity`，顺序即灌注顺序），数值**按整堆**给出、不再乘堆叠；默认返回空，于是 `SpiritChargeService` 回落到 `item_aura` 定义那条共享读法（灵石一字未改）。
  **两处收敛**：① 一开始 `SpiritPour.Entry` 还带着每 tick 的注入与扣费（`intake`/`cost`）和求值上下文，唯一的实现根本不用那个上下文（账单必须脱离持有者定价），等于签名里带了个必须忽略的参数——速率收归手势，`pour` 只回答"我是什么"；② 用户指出"只传 `LivingEntity` 不够，展示架也会激发，位置不是持有者站的位置"，于是汇报形状改成 `onCharged(SpiritSource, stack)`（见下）。
- 一次灌注只填 `active()` 那一条（第一条未满），填满一条继续下一条；`full()` 要求每一条都满。`SpiritChargeService.full(...)` 是"满没满"的对外提问（用户提的 `isFull`），它同时认物品自述与定义两条路——**正因为灵石那条路走定义，这个提问放不进接口**。
- 这样的物品**不需要** `item_aura` 定义，因此也不会顺带成为修炼燃料。
- `resolve` 对定义那条路仍要读两次（先空上下文拿灵气种类，再持有者上下文读速率）；对自述存储是常量，两次相同。

### 3.2 写入者汇报，物品决定（2026-09-17 追加）

"满了之后做什么"归物品，但**触发它的必须是写入者**：

- 往存储里写灵气的每一方（长按灌注 `SpiritChargeService.onUseTick`、展示架 `DisplayStandBlockEntity.add`）在**真实写入之后**调用 `AuraItemAccess.onCharged(SpiritSource, stack)`；物品自己判断是不是满了、要不要动手（符箓发动 + 烧一张）。因此这个回调**不是**"已经满了"的通知，而是"刚写进去了"的通知。
- 为什么不让物品在自己的 `add` 里判断（也是用户最早的建议）：`add` 会被 `simulate=true` 调用（那时绝不能发动）、会被**展示架**调用（那里没有持有者，而能力必须有行为者），而且即便如此服务仍得先知道"每 tick 写多少"才谈得上调用它。
- 为什么汇报里要带 `SpiritSource(level, position, actor)`：**只有写入者知道这东西在哪、谁付的账**。展示架上的一张符是被站在别处的人或一枚灵爆（`SpiritBurstEntity` 带着 `ownerLivingEntity`）填满的，位置不能从持有者身上读。行为者出账、被记录、为其能力作答；位置以 `block_x`/`block_y`/`block_z` 进入能力的公式上下文（与 `AbilityEventBridge.blockContext` 同一套写法）。
- 边界（已写进数据包文档）：位置既进**公式**（`block_x`/`block_y`/`block_z`）也进"在哪里发生"的全部东西——它作为这次激发的**原点**放进 `Context` 的扩展数据（键 `mxt:origin`，`EntityActionContext`/`BiEntityActionContext` 各自提供带实体兜底的 `position()`），`spawn_projectile`/`spawn_particles`/`spawn_effect_cloud`/`spawn_lightning`/`explode`/`play_sound`/`block_action` 读它，`mxt:area` 目标选择器以它为圆心，`mxt:teleport` 把它当作"行为者所在处"；缺省即行为者自身（普通施法一字未变）；投射物的朝向仍取行为者。**条件与取值**（`condition` 一族、按位置读环境灵气的取值）仍以行为者位置为准——那是条件层的事，是独立的一步。

**为什么不早停**：物品灌满时本可以调 `stopUsingItem()` 立刻收姿势（读过源码，这条路径安全：`useItemRemaining` 变负后 `completeUsingItem` 因 `useItem` 已空而走 `releaseUsingItem`，不会触发 `Finish`），但服务端清 `DATA_LIVING_ENTITY_FLAGS` 后客户端 `updatingUsingItem` 不再递减自己的计数，姿势是否随之收掉无法在无客户端环境下证实。收益（省掉一段空转）远小于"可能卡住姿势"的风险，所以不早停。

## 4. 顺手修掉的坑

1. **`HoldService.arm` 会覆盖物品自带的 `minecraft:consumable`。** 这是上一轮那个"摘掉所有原版进食"bug 的反方向：`arm` 是直接 `stack.set(CONSUMABLE, ...)`，若某物品既是长按物（如功法书）又自带食物组件，武装会把食物组件盖掉，而 `Start` 的摘除又会把它摘走 ⇒ 不能吃、也不会被消耗。修法：`ours(stack, hold)` 以"静音、无粒子、无 `on_consume_effects`、姿势与音效与本声明一致"识别自己写入的组件；**不是自己的就不武装，`Start` 也只摘自己写的**。刻意不比较时长——声明可以按栈给时长，而栈的数量在中途变化时不该让它"不像自己"。
2. **`arm`/`keepArmed`/`playHoldSound` 原来没有注册表可用。** 栈级时长必须两端算同一个数，而客户端拿不到服务端注册表（§2 第 7 条），因此这三个公开方法都改成接收 `Provider`（`level.registryAccess()`）。
3. **改 `HoldBinding` 时弄丢了 `extends ItemMatcher`**（`compileJava` 立刻在 `HoldLookup.hold` 上报"无法推断类型变量"）。记一笔：这个契约靠 `extends ItemMatcher` 才能被 `ItemMatcher.find(Stream, stack)` 使用，`TechniqueBinding` 上冗余的 `implements ItemMatcher` 是它的历史残留。
4. **扣费方式选型（测试包数据暴露的）。** `mxt_test:spirit_power` 有修炼档案，`use_condition` 是 `mxt:has_realm`，且 `max` 是境界条件（凡人 = 0）。若用 `ResourceTransactions.tryConsume` 扣费：凡人永远无法灌注；更糟的是 clamp 会把整池清零，而 `before - after` 会把"被裁剪掉的量"误算成"实付"，等于凭空给物品充能。最终走 `ResourceService.change` + `before - result.value()` 计量实付——这也是 `SpiritVesselItem.store` 与燃放方向（`ItemAuraService.release`）的既有做法：**这个方向的存取不经过资源使用门禁**。
5. **栈数量缩放与时长无关。** `capacity` 与 `intake` 都乘堆叠，所以一组四颗的窗口与单颗相同（400/4 == 100/1）。审计把这条钉住了，免得以后有人"优化"掉其中一个乘法。
6. **展示架灌不进去：射线只按碰撞形状判定命中（用户实测发现）。** `SpiritBurstEntity.tryHitAuraAccess` 原本对候选格子取 `getCollisionShape(...)` 再 `clip`，注释还写着"展示架的碰撞形状伸到格子上方，所以要把邻居格子也算上"——方向是对的，但问错了形状：展示架把物品显示在**方块上方 1.65**（`DisplayStandBlockEntityRenderer` 的 `state.height`），而它的碰撞形状只到 **1.4375**（`DisplayStandBlock.DISPLAY_SHAPE = box(2,0,2,14,23,14)`）。玩家唯一自然的瞄法就是瞄那件物品，而那条射线正好从碰撞形状上方约 8 cm 处飞过 ⇒ **永远打不中，一滴也灌不进去**；实测（逐 0.2 格扫瞄）命中窗口只到"瞄准点 +0.8"（世界 y≈71.3），"瞄物品"（y≈71.65）全飞过。修法：容器按"**所在格子 ＋ 上面一格**"判定（`AABB(pos).expandTowards(0, 1, 0)`），因为"哪里能灌进去"是个**地方**、不是一面墙；`level.clip(...)` 那道"更近的方块先赢"的检查原样保留，所以墙后面的容器仍然灌不到。附带的好处：灵气工作台这类"满格但玩家要往下瞄"的方块从此也能被掠过时命中。

## 5. 审计覆盖（`MxtTestMod.verifySpiritCharge`）

1. 能力条目的 `matches` 与 codec 往返（`{"type": "mxt:spirit_storage"}` 解出来仍是同一条目，且对灵石为真、对木棍为假）。
2. 闸门矩阵：灵石解析出灌注声明；木棍与"只作燃料的灵晶"都不是长按物；缺组件的灵石（= 满）不 `claims`；清零后 `claims`；并**首次钉住 `mxt:spirit_stone` 由哪条定义充能**（主模组自带的 `mxt:common`）。
3. 窗口推导：100 容量 / 1 每刻 = 100 tick；四颗一堆的容量 400、每刻 4、窗口仍 100；容量 100000 的合成读数被 `MAX_HOLD_TICKS` 截断；武装后栈向原版报告的时长、姿势、音效都是声明的那些。
4. 灌注一 tick：进 1 单位、扣 2 点自身灵气；付不起时（池里只剩 1 点）一个单位都不动、也不扣费。
5. 往返：把刚灌进去那一单位烧出来，`TickResult` 是 `consumed=1 / released=2 / exhausted`，持有者灵气回到灌注前的数，物品**保留且被抽空**（`AuraItemAccess` 物品永不销毁）。
6. 守卫与文案：给功法书塞一个"自己的"食物组件后过一遍点击与 `Start`，组件必须原样还在；四条 action bar 文案必须有翻译（进度那条还要吃参数）。
7. **展示架的灌注走真实路径**（`verifyDisplayStandPour`）：从玩家眼睛的高度朝展示架投出真正的 `SpiritBurstEntity` 并逐 tick 推进，三种瞄法各有断言——瞄**架上显示的那件物品**（方块上方 1.65）必须灌进去、瞄**架子本体**（碰撞形状之内）必须灌进去、**高高抛过**必须一滴不灌；三发之后石头的存量恰好是命中的那两发。其余步骤都是直接调方块实体的 `add`（包括符箓审计里展示架那几条），**只有这一条能发现"游戏里根本打不中"**——这次用户报的正是它。两个容易让这条测试自己骗自己的坑也写在注释里：石头必须显式写成"已抽空"（缺组件 = 满，灌不进去），射线带的灵气必须是石头自己定义里那一种（`mxt:common`，测试模组里同名那条定义匹配同一个物品但不是它的类型）。

## 6. 边界与未闭环

- 只动持有者自身那一池灵气，不吸取环境；「环境灌注」若要加，`AuraService.consume` 是现成的扣库存入口，但需要先定平衡闸门。
- 存储按整数单位：`consume_speed × 堆叠 < 1` 时按 1 计（声明速率唯一不精确执行之处）。
- 缺组件的物品读作已满 ⇒ 刚合成的灵石本来就是满的，灌注实际作用于被抽空过的与创造栏给出的空灵石。
- 上品灵石（`aura: 100000`、`consume_speed: 1`）一次手势上限 200 单位，灌满需要约 500 次手势；这是数值耦合（同一字段既决定烧多久又决定充多快）的表现，机制本身没有错。真要解耦就在 `item_aura` 上加可选 `charge_speed`/`charge_cost`。
- 客户端姿势与服务端判定的既有偏差（服务端掉帧时姿势可能早一点收掉）同样适用于灌注；信标仍是 `HoldPoseDiagnostic`。
- 未做：灌注粒子/进度 HUD、一次灌注整组物品之外的批量入口、灌注与铭刻/制符等玩法的接驳。

## 7. 拆分之后：接口参数、撞名与存储类型（2026-09-17）

用户提问的原文是"我记得之前把 resource 和 cultivation 进行了分离操作，你看一下 `SpiritAccess` 和 `SpiritItemAccess` 还是传入的 `Resource`，这个语义是不是不对了"。逐条查过之后的结论分两半。

### 7.1 参数是 `Holder<Resource>`，这是拆分后的唯一正确答案

> **本节结论已被 §7.7 推翻，保留原文只为记录当时的推理。**用户随后明确否定了它："`Resource` 只是一个数值系统……`Aura` 才是用来区分哪个灵气的。"

拆分（`research/audit/resource-cultivation-split.md`）把 `resource` 定成**值的身份层**（数值 + 资源条），`cultivation` 只是挂在它上面的**行为档案**——档案自己的第一个字段就是 `resource`。而这两个接口搬的就是"值"本身：

- 持有者的池子是 `ResourceHolderAttachment`，按 `Holder<Resource>` 开键；出账走 `ResourceService.change(holder, resource, delta, context)`。接口若收档案，每次真实搬运都得先拆回 `Resource`。
- 档案不能当钱花：`Aura.aura_type` 是 `Holder<Element>`、`burst_amount` 是数量，描述"这个数值怎么修炼"，不描述"它是什么"。
- 文档本来就承诺无档案也能入物（`docs/guide/datapack/resource.md` 末句："没有档案的数值就是一个普通计数器，仍可被消耗、比较、写入公式和**存入物品**"）。
- "灵气种类"在别处一律用资源表达：`Element.aura_kinds`、`AuraZone.aura_kinds`、`BlockAura.aura_kinds` 都是资源 ID，`SpiritBurstClient.canBurst` 选的是 `Reference<Resource>`。**这三个字段后来全被删除**：`SpiritBurstClient` 改成选灵气，"灵气种类"整套词汇（`aura_kinds`）在下一轮整条删除（见 `research/audit/resource-cultivation-split.md` §7.5）。
- 反证：默认包里灵石搬的是 `mxt:common`，而这个数值在拆分后**没有档案**——接口若收档案，默认内容自己先跑不起来。

**当时的改名（已被 §7.7 再改一次）**：`SpiritAccess` → `ResourceAccess`、`SpiritItemAccess` → `ResourceItemAccess`（文件同名，仍在 `runtime/spirit/`）。改的是"它交换什么"，不是子系统名：`ResourceItemAccess.pour` 返回 `SpiritPour`、`onCharged` 收 `SpiritSource`，把这两个接口挪进 `runtime/resource/` 会造出 `resource → spirit` 的包环，所以留在原包。刻意不动"spirit"词汇的还有 `SpiritChargeService`/`SpiritChargeHold`/`SpiritPour`/`SpiritSource`/`SpiritStorageEntry`/`SpiritStorageComponent` 与数据包 ID `mxt:spirit_storage`（改名会破坏内容包与组件存档）——这一条在 §7.7 之后仍然成立。

### 7.2 真撞名：`SpiritBurstEntity.auraType` 与档案的 `aura_type` 指两种东西

射线的字段/构造参数/同步键原来叫 `auraType`/`AURA_TYPE`，类型却是 **`Holder<Resource>`**；而数据包的 `aura_type` 是 **`Holder<Element>`**。`SpiritBurstService.tryFire` 里两者同框：`profile.auraType()`（Element，门禁）与 `resource`（Resource，弹丸载荷）。当时改为 `auraResource` / `AURA_RESOURCE` / NBT 键 `resource`，并在类注释里写清"这是灵气**种类**（一个 resource），元素是档案的 `aura_type`，两者不是一回事"。NBT 键跟着改：影响面只有存档里在飞的射线，本仓一贯不考虑兼容。**§7.7 之后这个名字再改了一次**：字段是 `Aura`（同步键与 NBT 键 `aura`），查 `mxt:aura` 注册表。

### 7.3 `SpiritStorageComponent` 现在记下它装的是哪一种（键类型见 §7.7）

原来只有一个 `int amount`，"这个数是什么资源"靠**当时匹配到的** `item_aura` 定义反推。定义被内容包改掉 `type` 之后，同一枚灵石的 100 会被静默读成另一种灵气。现在它是**一张「灵气 → 已存单位」的表**（当时是 `record SpiritStorageComponent(Object2IntMap<Holder<Resource>> amounts)`；**§7.7 之后键类型改成 `Holder<Aura>`**，JSON 形状不变，仍是 `{amounts:{"mxt:common":100}}`），数量记在键下：

- **键即类型**：`SpiritStoneItem.contentType` 读组件里**唯一**的那条记录（`soleAura()`），没有组件、或 store 是空的才回落到定义；`getCapacity` 按它开键，`add`/`extract` 也只接受它。于是"记下自己装的是哪种"不需要额外字段——这也是后来能把符箓那张表合并进来的原因。
- **容量仍来自定义**：`item_aura.aura` 是物品的尺寸，`type` 只是它"声明接受哪一种"。因此定义改 `type` 之后，旧灵石既装不进新的那种、也烧不出旧的这种（`ItemAuraService.loadNextItem` 请求 `definition.type()` 会得到 available = 0，物品被原样退回），**不会**被改写成另一种资源，也不会丢数量。
- `SpiritChargeService.resolve` 的定义分支同样先看组件的 `soleAura()`，再拿定义要容量。
- **空表 = 被抽空**：`extract` 到 0 会顺手删键，"空"只有一种表示；缺组件则仍是"没被动过"（灵石读作满）。
- 创造栏的空灵石随之简化：写一个空表即可，不必再为"空的是什么"去查定义（原先为此改成读 `parameters.holders()`，现在那段也去掉了）。
- 组件 id 与同步方式不变（`mxt:spirit_storage` 仍是 `persistent().networkSynchronized()`，见 §2 第 6 条）：`CollectionCodecs.intMap` 与 `TalismanChargeComponent` 原来的 `Map<Holder<Resource>, Integer>` 是同一份编码，registry-fixed 的 codec 两端都带注册表读取（§7.7 之后是 `Aura.CODEC`）。
- **与符箓共用**（同日追加，见文首变更记录）：`mxt:talisman_charge` 删除，载体写的就是这个组件。"几单位、哪种资源"对灵石与载体是同一个问题，符箓只是可能同时装多种；缺组件读作"满"还是"空"仍然**由物品回答**（灵石满、符箓空），组件只是一份数据。

### 7.4 拆分漏掉的默认数据迁移

`src/main/resources/data/mxt/mxt/resource/common.json` 里还留着拆分前的 `regen` 与 `aura_type`。两个字段都已被新的 `Resource` codec **静默忽略**（RecordCodecBuilder 不报未知字段），而 `data/mxt/mxt/aura/` 目录**不存在**——`git show --stat 96aec85 | Select-String 'main/resources'` 为空，那次拆分一个字节都没动主模组的数据。后果是 `AuraLookup.find(mxt:common)` 为空，`AuraZoneRenderer`、`SpiritJeiText`、`AuraCommand`、`CultivationAffinity`、`CreatureProfileService` 这几处 `aura_type` 读取全部落空。现在：`common.json` 去掉两个死字段，新增 `data/mxt/mxt/aura/common.json`（`{"resource": "mxt:common", "aura_type": "mxt:common"}`）。这是**原样迁移**——旧文件的 `regen` 是 0（档案默认也是 0），没有 `first_realm`（信息面板只遍历玩家已跟踪的链，所以不会多出一行），没有 `use_condition`（默认 `AlwaysTrueCondition`，与"无档案恒真"一致），`burst_amount` 缺省 0（拆分前也发不出射线）。

### 7.5 验证与覆盖

`compileJava` / `compileTestModJava` 通过；`runTestServer` 打印 `MiXianTu server audit passed`（合并组件那次也重跑确认）。被这些改动直接钉住的审计步骤：`verifySpiritCharge` 第 2、3、4、5 步（灵石容量/灌满/抽空/往返全部经新组件读写，并把"由 `mxt:common` 充能"这条既有断言钉得更死——现在 `empty.resource()` 读的是组件而不是注册表顺序）、`verifyDisplayStandPour`（射线灌展示架，经 `AuraAccess`）、符箓与冷却各条（第 13 步断言发动后 `mxt:spirit_storage` **必须被清掉**，第 14 步断言拒绝时进度保留）。

审计读取方式也跟着修了：`chargeOf(stack)` 改成"把 store 里所有资源加起来"（一张载体可以装多种，读数问的是搬了几单位而不是哪一条），新增 `storedUnits(stack, aura)` 按键直读组件（§7.7 之后收 `Holder<Aura>`），用来把"从没存过"和"存了又被清空"分开。**新增了一条审计步骤**（`verifyTalismanInvocation` 第 18 条，见 `research/audit/talisman.md` §5）：把组件按文档写出的形状用 `RegistryOps` 解一遍、编码回去再解一遍、并断言"多于一条时 `soleAura()` 为空""清空是删键"，因为一个 codec 只被物品自己读写是证明不了它对外写法正确的——本轮刚在 `mxt:resource_container` 上发现了同类的文档/编码错位（见 §7.6）。留白仍然说清楚：那条"石头的存量必须记着自己是什么"的语义目前只由 `contentType`/`soleAura()` 与类型系统保证，审计里还没有"把定义 `type` 临时改成另一种资源后，旧石头既不认新资源、也不被改写"的运行时断言——那需要夹具支持同一物品的两条 `item_aura` 定义按注册表顺序切换，本轮没做。

### 7.6 仓库里已有的那个"通用 map 组件"，以及为什么没拿它当合并目标

用户指的是 `mxt:resource_container` / `ResourceContainerComponent`（`data/item/ResourceContainerComponent.java`），唯一使用者是灵力容器 `mxt:spirit_vessel`（`SpiritVesselItem`）：

- 形状是 `Object2DoubleMap<Holder<Resource>>`，**浮点**；工具提示每个资源一行（`tooltip.mxt.spirit_vessel.resource`）；右键释放给持有者、潜行右键从持有者存入，每种资源容量 `CAPACITY_PER_RESOURCE = 1000`（写死在物品类里）。
- 它是**持有者池子的便携副本**：`ResourceHolderAttachment` 用的就是同一套 `CollectionCodecs.doubleMap(Resource.CODEC)`。它的 Javadoc 里原来那句 "used by stones, batteries and future artifacts" 是过时的——灵石在拆分后就改走 `spirit_storage` 了——本次顺手把这段 Javadoc 改成"容器而非电池"并指向 `SpiritStorageComponent`（代码只动了这一段注释）。
- 它不在 `AuraItemAccess` 协议里：没有 `getCapacity(entity, stack)`（容量按物品/按堆内容算，不是常量）、没有 `simulate`、没有"写入者汇报"的 `onCharged`。它只负责"右键搬一次"。

**为什么只合并两个整单位的、而不三合一**：`double` 与 `int` 各自都有理由，不是随手写的。容器搬的是**池子**的值，池子本来就是浮点（`mxt_test:qi: 25.0`、regen `0.25`），做成副本就不能丢小数；而灌注协议按契约是**整单位**（`getCapacity`/`add`/`extract` 与 `SpiritPour.Entry` 全是 `int`，文档写"灵石存储以整数单位计，小数部分不计入容量"）。三合一必须先决定牺牲哪一边，而且容器还要一个"我不参与灌注"的开关——一旦它实现 `AuraItemAccess`，右键就会被长按模块武装成"按住灌注"，与它现在的点击语义冲突。所以结论是：**通用的是"资源 → 数量"这个形状，不是"谁能被灌注"这条协议**；两个整单位存储合并，容器保持浮点、保持自己的交互。

顺带记下查这件事时发现的一处文档问题（本轮**未改代码**，只记在这里）：`docs/通用物品.md` 里灵力容器的示例写的是 `mxt:resource_container={values:{"mxt_test:qi":25.0}}`，但该组件的 codec 是**裸 map**（`CollectionCodecs.doubleMap(Resource.CODEC).xmap(...)` → `AutoIgnoreMapCodec`），并没有 `values` 这一层——`values` 是**附件** `ResourceHolderAttachment` 的字段名（`CollectionCodecs.doubleMap(Resource.CODEC).fieldOf("values")`），看着是从那里抄过来的。更麻烦的是 `AutoIgnoreMapCodec.decode` 对解不出的条目**只打一条 WARN 然后丢弃**，所以按文档那条命令实际得到的是**空容器**而不是报错。要钉住它得加一条审计断言（两种写法各解一次），本轮没做。

### 7.7 键改成 `Holder<Aura>`：灵气才是身份（2026-09-17）

§7.1 的结论被用户当场推翻。原文三句：

> "Aura 和 Resource 的区别：Resource 只是一个数值系统，数值有啥用 Resource 自身不知道，Aura 才是用来区分哪个灵气的。检查一下代码，尤其是 `ResourceAccess`，应该是传入 aura。"

判据（用户同时给出的三条）：① 纯数值没有意义，要看使用场景——存在叫 `Spirit*` 的字段/存储里的东西就按灵气算；② 环境灵气提供的是一定量的 `Aura` 而不是 `Resource`，各处传输用 `Object2IntMap<Holder<Aura>>` 这类形状；③ `aura_kinds` 留到下一轮。

**为什么 §7.1 错了**：它把"这个数是谁"和"这个数是干什么的"合成了一件事。`Resource` 是一套数值系统（边界/图标/资源条），它**不知道自己有什么意义**；`Aura` 才是"哪一种灵气"，它引用一个 `Resource` 作为自己被计量的单位。所以 `Aura.resource()` 回答的是"我用哪个数计量"，不是"我是什么"。§7.1 的论据里唯一有分量的是"默认包里灵石搬的 `mxt:common` 没有档案"——而这一条在同一天的 §7.4 就被自己修掉了（补上了 `data/mxt/mxt/aura/common.json`），剩下无档案的四个计数器全在测试包、且只活在玩家池子里。

本文件相关部分的实际改动：

- 接口：`ResourceAccess`/`ResourceItemAccess` → **`AuraAccess`/`AuraItemAccess`**，参数 `Holder<Aura>`；`AuraAccess.requireNonNegative` 的报错文案跟着改。仍在 `runtime/spirit/`（理由见 §7.1 末段，包环那条仍然成立）。
- 存储：`SpiritStorageComponent(Object2IntMap<Holder<Aura>>)`，codec `Aura.CODEC`，`soleResource()` → `soleAura()`；**JSON 形状一个字符都没变**（`{amounts:{"mxt:common":100}}`），因为 aura id 与 resource id 同名，同一份文本换个注册表照样解得出。
- 读数：`SpiritPour.Entry.resource` → `.aura`、`SpiritChargeService.Charge.resource()` → `.aura()`；`ItemAuraService.type(...)` 返回 `Optional<Holder<Aura>>`；`SpiritStoneItem.contentType`、`TalismanService.bill`、`Talisman.auraCost` 全部换键。`ItemAura.type` 本身也改成 `Holder<Aura>`（JSON 键仍是 `type`）。
- 燃料：`ItemAuraService.release`（燃放→池子）在写入 `ResourceHolderAttachment` 前取 `aura.value().resource()`——这是唯一不需要查表的方向。
- 世界与客户端：`SpiritBurstEntity.auraResource` → `setAura`/`aura()`（NBT/同步键 `resource` → `aura`，查 `mxt:aura` 注册表）；`SpiritBurstService.ACTIVE_RESOURCES` → `ACTIVE_AURAS: Map<UUID, Set<Holder<Aura>>>`；`SpiritBurstCooldownAttachment`、`AuraChunkAttachment`、`AuraResult`、`AuraClientState.Snapshot`、`AuraStateS2CPayload` 全部按灵气开键。热栏选择（`SpiritBurstClient`）也改成选灵气（`auras`/`aurasAvailable`），发给服务端的仍是 id。
- 测试辅助：`chargeOf` 不变（它只数总单位），`storedUnits`/`carrierChargeOf` 改收 `Holder<Aura>`。

**与 §7.1 的五条"反证"逐条对照**（说明它们为什么不成立）：

| §7.1 的说法 | §7.7 的处理 |
| --- | --- |
| 持有者池子按 `Holder<Resource>` 开键，接口收档案每次都要拆回 `Resource` | 池子**仍然**按值开键（它同时装纯计数器），但接口收的是灵气：`aura.value().resource()` 是灵气自己的字段，取它不算"查表"，也不需要反向解析 |
| 档案不能当钱花（`aura_type` 是 Element、`burst_amount` 是数量） | 那两句恰恰说明档案**就是**身份：元素与射线量都是"哪一种灵气"的属性；值那一层没有这些字段 |
| 文档承诺"没有档案的数值仍可存入物品" | 该承诺改掉了：能被存入的是**灵气**，一个纯计数器没有灵气身份，也就没有"哪一种"可存——`docs/guide/datapack/resource.md` 与 `docs/数据包格式.md` 已同步 |
| "灵气种类"在别处一律用资源表达（`aura_kinds`、`SpiritBurstClient`） | `SpiritBurstClient` 已改成灵气；`aura_kinds` 是第四套表示（裸 `Identifier`，不进任何注册表），**随后整条删除**（`research/audit/resource-cultivation-split.md` §7.5） |
| 默认包里 `mxt:common` 没有档案 | 已在 §7.4 补上档案；剩下无档案的四个只出现在保持 `Resource` 的字段里 |

**一处被审计当场抓住的错**：`ResourceTransactions.tryConsume` 里新加的 value→aura 解析被放在了 `entity != null` 判断之前，编队没有阵主（`entity == null`）时 `AuraLookup.holder(LivingEntity, …)` 直接 NPE，服务端崩在 `ServerStarted`（`FormationService.maintain` → `tryConsume`）。已把解析包回判空内，并给 `AuraLookup.holder(LivingEntity, …)` 加了空守卫。

**验证**：`compileJava` / `compileTestModJava` 通过；`runTestServer` → `MiXianTu server audit passed`。§7.5 里那条留白（"定义 `type` 改掉后旧石头既不认新资源也不被改写"没有运行时断言）依然存在，只是现在它的对象是**灵气**：组件键是 `Holder<Aura>`，改 `item_aura.type` 只会让旧存量与新类型对不上。完整清单与数据兼容性核对见 `research/audit/resource-cultivation-split.md` §7.4。


