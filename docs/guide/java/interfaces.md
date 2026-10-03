---
title: 特殊公开接口
---

本页的实现型接口里，`AuraAccess`、`ItemAuraAccess`、`UseItemAuraAccess`（原 `runtime/spirit`）与 `WheelMenuEntry`（原 `screen/wheel`）已于 2026-09-22 搬进 **`com.iafenvoy.mxt.api`**；同一天这一族又多了三个生物侧契约（`Contractable`、`ContractOperations`、`CaptureListener`，见文末三节）。供热方块实现 `AlchemyHeatSource`，也在这个包里。该包**只有接口与 `package-info`**，实现仍在各自模块，搬动只改包名与 import。`TooltipAppender` 是 NeoForge 的扩展点、`Cost` 在 `data/cost`；**`Toggable` 留在 `data/ability`——它不算对外 API**（它是本体登记"需要按键的技能"的形状，`mxt:active` / `mxt:flight_control` / `mxt:storage` 三个技能类型实现它）。哪些东西**不**进 `api` 见 `AGENTS.md` §3：只有"别的模组会实现或调用"的契约才进去，服务类的静态代理是明确的推迟项。

### `AlchemyHeatSource`

供热方块在 `com.iafenvoy.mxt.api` 里的**可选**高级接口。丹炉读的是**底层正中央那一格**（本地 index 4）里的方块：默认只查数据包注册表 `mxt:heat_source`（按方块或方块标签给 `max_temperature` 与 `heating_per_tick`），方块自己实现了这个接口时**以方块的回答为准**、忽略表里给它写的条目。两个方法都只读：`double maxTemperature(BlockState state, ServerLevel level, BlockPos pos)` 与 `double heatingPerTick(BlockState state, ServerLevel level, BlockPos pos)`——`pos` 是供热格自己，不是核心，方块可以据此按自己的方块状态或周围环境回答。返回值必须有限且大于 0，否则丹炉把这格当成没有热源；不要每个 tick 分配一份温度曲线。温度写入由服务端炼丹服务完成，方块不要自己改批次。本体没有生产热源方块。测试模组的 `mxt_test:alchemy_test_fire`（150 / 40，走标签条目）与 `mxt_test:alchemy_weak_fire`（80 / 10，走更高 `priority` 的条目）只供测试，不是内容包要注册的方块；`mxt_test:alchemy_advanced_fire` 实现本接口，用来验证接口优先于表。

`AlchemyWorkstation` 仍在 `api`。保留 `container`、`state`、`getBlockPos`、`furnaceItem`、`furnaceDefinition`、`temperature`、`targetTemperature`、`setTargetTemperature`、`setTemperature`、`structureStatus`、`phase`、`setChanged`。没有 `select`、`clearSelection`、`selected`。新增 `heatSourcePos`、`wallTemperatureLimit`、`heatTemperatureLimit`、`maximumTemperature`；`fireContainer`、`canPlaceFire`、`canTakeFire`、`fireTemperatureLimit` 已随异火槽一起删除（供热是世界里的一格方块）。`poweredTicks`、`setPoweredTicks`、`auraBank` 已删除。开炉是 `AlchemyWorkstationService.start(ServerPlayer, AlchemyWorkstation)`，预览同形且不接收所选配方。动作包是三个字段：`containerId`、`TEMPERATURE` / `START` / `ABORT`、温度。参数表以当前接口为准，本文不另造重载。

### `AuraAccess`

展示架、容器等方块实体实现的**灵气存取**接口：按整单位交换某一种灵气（`insert`/`extract` 一次只处理一种，返回操作后**没能移动**的数量；`simulate=true` 只模拟，不修改状态），容量由 `getCapacity(entity)` 给出。

参数是 `Holder<Aura>` 而不是 `resource`：`resource` 只是一套数值系统（边界/图标/资源条），它不知道自己这个数是干什么用的；`aura` 才是"哪一种灵气"的身份，它引用一个 `resource` 作为自己被计量的单位（`Aura.resource()` = "我用哪个数计量"）。所以凡是"哪一种灵气"的字段、参数与存储键都用 `Holder<Aura>`——存取接口、物品与方块存储、环境灵气池、`item_aura.type` 都是。反过来说，纯计数器没有灵气身份，因而**不能**被存入物品（它能进玩家池子，因为池子按值开键）。语义边界见 `research/audit/resource-cultivation-split.md` §7.3/§7.4。

### `ItemAuraAccess`

可充能物品实现的**存储**接口，只回答三件事：能装哪些灵气（`getCapacity`）、装进去多少（`insert`）、取出多少（`extract`）。除灵气、增加/抽取和模拟参数外，通过 `getCapacity(LivingEntity, ItemStack)` 从物品动态计算容量，不能在 Java 中写死容量；装的是哪种灵气与装了多少由 `mxt:spirit_storage` 组件记在物品自己身上（一张「灵气 → 已存单位」的表，键是 `Holder<Aura>`，灵石与符箓载体共用），`SpiritStoneItem` 因此只把定义当容量来源，不会因为数据包改了 `item_aura.type` 就把已有存量改读成另一种灵气。缺组件对灵石读作"满"、对符箓读作"空"，这条**由物品回答**而不是由组件回答——组件只是一份数据。

存取被问在**任何地方**：展示架读写一张符、热键栏读一件物品、`item_aura` 定义描述一个物品，用的都是这个接口。所以只实现它的物品是**被存进去**的，不会被"按住右键灌"——那个手势是物品额外选择加入的，见下。

### `UseItemAuraAccess`

让物品**按住右键灌注灵气**的接口，继承 `ItemAuraAccess`：`HoldBinding`/`HoldService` 负责手势与姿势，`SpiritChargeService` 每 tick 从持有者自己的灵气池取出并写入 `insert`。实现它**不代表**要自己描述形状——灵石就实现了它，却把 `pour` 留空，于是走 `item_aura` 定义那条共享读法。它表达的是"这个物品可以被按住灌"，不是"这个物品是特殊的"。不实现它的存储（比如只用来存东西的通用物品）永远不会被武装成手势。`HoldBinding` 另外提供一对**带持有者**的默认重载（`claims(LivingEntity, Provider, ItemStack)` 与 `holdTicks(LivingEntity, Provider, ItemStack)`）——不关心是谁拿着的声明不用实现它们；法器就是靠这一对做到"归别人就不接管这次右键"（见 `docs/数据包格式.md` 的 `artifact`）。

拆成两个接口是因为"存储"与"被灌"不是一回事：存储能被问在任何地方，被灌只发生在一个人对着自己手里那一堆做手势的时候。三个默认方法对应手势的三个时刻：

- **`pour(registries, stack)`**（手势开始前、之后每 tick）——这个容器**是什么**：按灵气分列的 `SpiritPour`（每种灵气已存多少、上限多少，**顺序即灌注顺序**），数值按整堆给出、不再乘堆叠。默认返回空 = "我没什么特别的"，于是回落到 `item_aura` 定义那条共享读法（灵石走这条）。只有容量取决于**这一堆上写了什么**的物品（符箓载体）才覆写它。两侧都会问（客户端靠它算手势长度），所以实现只能读传进来的 `Provider`，且同一个 stack 必须答得一样。速率与代价**不在**这里，那是手势的（定义路线按两个速度反向使用，自述存储按 1 单位/tick、1:1）。
- **`canPourInto(holder, stack)`**（每 tick，**付灵气之前**）——这一 tick 值不值得灌。手势的顺序是"先扣灵气、再 `insert`、最后 `onCharged`"，所以只要有"插进去也没意义"的情况就会白花灵气；这个方法让物品在付钱前否掉。默认 `true`（只进不出的容器没有二话可说），只有**会因被灌满而自焚发动**的物品覆写它——符箓覆写成 `TalismanService.canFireFrom`，即"这个持有者的冷却窗口还开着吗"。注意它**不是**"要不要自动发动"那条：那由载体自己的模式决定（`mxt:talisman` 组件的 `mode`，`fire` 灌满即发动／`store` 只积累），跟灌注闸门不是一回事。
- **`onCharged(source, stack)`**（一次**真实**移动之后）——"我被灌了"，由物品自己决定是不是满了、要不要动手。默认什么都不做。

**写入者负责汇报**：任何往存储里写入灵气的一方（长按灌注、`AuraAccess` 方块实体等）在**真实写入之后**调用 `onCharged(SpiritSource, stack)`（`simulate` 不算），由物品自己判断"这是不是满了"以及随之而来的行为（符箓在这里发动，并消耗一件本体或按铭刻的耐久扣除）。之所以由写入者汇报、而不是让物品在自己的 `add` 里判断，是因为只有写入者知道**这东西在哪、谁付的账**：展示架上的一张符，是被站在别处的人（或一枚灵爆）填满的。`SpiritSource(level, position, actor, consumedByHand)` 同时带着位置与行为者——行为者出账、被记录并为能力作答；位置是这次激发的地点，既以 `block_x`/`block_y`/`block_z` 进公式，也作为**原点**交给位置类行为；`consumedByHand` 说明这次是不是"手上的消耗"（展示架、机器等摆着的存储为 `false`）（见 `docs/数据包格式.md` 的「灌注与激发」）。也正因为汇报是"选择加入"的：写入方遇到只实现存储的物品时，本就没有什么可汇报的。

### `TooltipAppender`

物品模块通过 NeoForge `TooltipAppender` 注册 Tooltip。每个模块使用独立 Appender，资源、货币、品质和灵气存储显示互不耦合。

### `Cost`

技能、阵法和其他行为的消耗抽象。一个 `Cost` 只回答"这一项要扣什么"（`charge(CostContext)`，求值加通道检查，只读），实际校验与扣除由 `CostTransaction` 用同一份计划完成：`plan` 逐项求值与查通道，`commit` 按通道写入，中途任何一项拒付就把已经写下的还原。所有消耗字段都是它的数组（格式见 [`Cost`](../../数据包格式.md#cost)）。新增 Cost 类型应使用固有注册表分派（`mxt:cost_type`），而不是在 JSON 中写 Java 类名；付款者与通道由调用点提供（`CostContext`），数据包只写"要什么"。包内分工：`data/cost` 根放契约与交易器（`Cost`、`Charge`、`Costs`、`CostTransaction`、`ItemCostDraft`），四种固有类型在 `data/cost/builtin/`（对应注册在 `registry/MxtCosts` 的 `mxt:resource` / `mxt:aura` / `mxt:item` / `mxt:js`），付款上下文与它的枚举在 `data/cost/context/`（`CostContext`、`CostChannel`、`CostFailure`、`CostOrigin`）。

### `WheelMenuEntry`

轮盘条目的纯客户端接口：`kind()`（技能 / 灵气 / 契约行为，以及内容模组自己注册的类型）、`id()`、`title()`（轮盘中间显示的名字）、可选 `icon()`、强调色、`tooltip(Player)`（类型 + 具体数值，技能那一条的**最后一行写来源**：学习的技能 / X的技能（X = 承载物品名，按品质上色）/ 其它来源）、`cooldownTicks(Player)`（还剩几 tick，0 = 就绪）/ `usable(Player)` 两个可用性钩子，以及使用回调 `onSelected(WheelSelection)`（`WheelSelection` 带着它是在**哪一格的编号**上、以及那一格读自**哪个来源**）。一个来源贡献哪些条目由 `WheelMenuProvider` 给出——它的入参是 `(player, source)`，`source` 是 `api/WheelSource`（内置五页：主盘 / 主手物品 / 副手物品 / 法器 / 契约灵兽），返回值**可以比一页长**（一页 12 格，多出来的由 `WheelMenuContent` 开新页），条目本身既不知道自己落在第几格，也不知道自己属于哪一页。旧的两个 hotbar 条目接口（`HotbarEntry`）随快捷栏一起删除。

### `WheelSource` / `WheelEntryKind`

轮盘的两个扩展点（2026-09-25，见 [`research/50`](../../../research/50_目标选择器与轮盘扩展点设计.md)）：**一页**与**一类格子**。两者都是 `api` 里的接口，实例由内容模组自己实现并在 mod setup 里注册（`runtime/wheel/WheelSourceTypes#register` / `WheelEntryKinds#register`，先注册者胜、只在游戏线程读），页顺序就是注册顺序。

- `WheelSource`：`id()`、`displayName()`、`configured()`（是不是玩家自己摆的那一盘）、`grantSources(entity)`（这一页由哪些授予来源拼成）、`equipment(entity)`（从哪几件栈上读承载物）、`offers(entity, kind, id)`（这一项此刻能不能从这一页触发——服务端每次触发都要先问它）。
- `WheelEntryKind`：`id()`、`displayName()`、`holdsEntry()`（`mxt:empty` 是唯一答否的那个，空格子也得有类型）、`exists(access, id)`（这一格还算不算数，读不出来的会在存盘时被清掉）、`trigger(player, source, id)`（**按下这一格做什么**，只有服务端调）、以及默认返回 `false` 的 `directed(player, source, id, wanted)`（点名一个状态；只有开关那类实现它）。

客户端把某个 provider 注册到**某一页的 id** 上（`WheelMenuContent.register(source, provider)`，多槽位），所以加一页不会覆盖内置页；配置界面仍按内置 kind 分池，第三方 kind 的条目暂时进不了那两个池子。

### `Toggable`

**需要按键才能发动的技能**（2026-09-22 作为 `ToggableArtifactAbility` 诞生，2026-09-23 合并后升格为与宿主无关的 `Toggable`，同日内联技能取消后删掉了它的 `key()` 与 `displayName()`，见 `research/40_能力与法器能力合并设计.md`（§12 记了同日的两次收缩））：判据是一句话——**凡是要按键才发动的都算技能、都进轮盘**。它是**数据层**的接口，不是 `api` 包里的对外契约，任何 `mxt:ability_type` 都能实现它。接口把三件事交给实现自己回答：`state(ctx)`（有没有开关状态、现在是哪一边；**空 = 一次性**，如储物）、`activate(ctx)`（按下了；只有服务端调，返回 `Result(changed, failure, failedResource)`，`Failure` 的 18 个取值（多一个 `NO_VEHICLE`：御器之术在主手与副手都没找到飞行法器）与 `AbilityService.Failure` 同名同义，会被轮盘翻译成动作栏那一句——按压与施放共用 `actionbar.mxt.ability.failure.*` 一份文案表，见 [`docs/guide/java/wheel.md`](wheel.md)），以及一个有默认实现的 `gated(ctx)`（这次按压要不要先过共用的"条件 + 冷却 + 消耗"闸门；开关在**关**的那一下返回 false，因为落地不该收费）。**这一格叫什么用技能自己的 `name`**，接口不再另给一个名字；`type` 也不需要报一个"宿主内的 key"——轮盘条目的身份就是这条技能的注册表 id。**状态归实现自己管**（飞行读**驾驶者**的 `FlightAttachment`——记着飞的是哪条术、哪辆车，储物没有状态），轮盘不认识"这件事是什么"，只认识这几件事，所以加一个新技能类型不需要动轮盘。今天**五个**实现是 `mxt:active`（原本就按一下施放——它 `gated` 返回 false，因为施放事务自己付款）、`mxt:channelled`（同上：一次完整施放开始引导，之后按 `tick_interval` 收维持费）、`mxt:targeted`（同上：一次完整施放，然后对选择器挑中的每个实体各跑一次子技能，见 `AbilityApplier`）、`mxt:flight_control`（开关：起剑 / 落剑；它从主手、其次副手取那件飞行法器，落地时原样归还）与 `mxt:storage`（一次性——打开承载物的储物箱，容器菜单与窗口都复用原版箱子那一套，见 `docs/guide/java/screens.md`）。字段与玩家侧表现见 `docs/数据包格式.md` 的 `ability` / `artifact` 两节。

### `Contractable`

让生物**能被契约**的资格接口（2026-09-24）：**实现它就是全部资格**——数据包无法把一个实体变成契约对象，所以原版生物默认都签不了；"不是所有生物均可契约"就是这条的落地。它同时继承原版的 `OwnableEntity`，所以"谁是主人"整个交给**原版的 owner 逻辑**：`getOwner()` 由 `EntityReference` 经所在维度解析、`getRootOwner()` 白拿。自己的成员是 `setContractOwner(owner)`（签订时由框架调用，生物把主人写进**它自己存主人的地方**）、`acceptsContract(context)`（这份契约类型它签不签；默认读**该契约类型自己的实体类型标签** `#<命名空间>:contract/<路径>`，标签不存在或为空即不限制，见 [`contract_type`](../../数据包格式.md#contract_type)）、`onContractBound(context)`（主人写好之后）、`onContractReleased(context)` 与 `onContractDeath(context)`（**解除**与**死亡**是两个钩子，谁也不替谁猜）。

**主人没有第二份**：本体不存主人，`mxt:contract` 附件里也没有这个字段——`getOwnerReference()` 与 `setContractOwner` 都由实体自己实现（原版驯服动物用 `TamableAnimal` 已有的那一对，其它生物自己存一个 `EntityReference<LivingEntity>`，放进自己的存档与同步数据里）。于是框架侧只有一个读点 `Contracts.ownerOf` / `Contracts.owner`，问的永远是实体。**解除契约只清契约记录**：要不要连主人一起忘掉由生物自己在 `onContractReleased` 里决定，"解约"与"忘掉谁驯服了它"不是同一件事。

契约的其余部分不在接口里：契约类型、签订时刻、召回闩只有一份，在生物的 `mxt:contract` 附件上。

### `ContractOperations`

契约之后"这只灵兽自己怎么做"的接口：`recall(context)`（主人摇了铃，落地动作交给它）、`follow(context)`（每 tick 跟随；主人必须在线且同维度）、`onDealtDamage(context, target, damage)`（自己打出伤害、结算之后），以及 2026-09-25 加进来的行为三件套（下段）。**每个默认实现就是框架从前写死的那一段**——`recall` 直接传送到主人，`follow` 超过 32 格传送、超过 4 格寻路——所以实现了接口却什么都不覆写的灵兽，行为与从前完全一致。**不实现它等于不要这套通用行为**：这只生物仍然能被契约，但框架不替它跟随、不替它召回落地、也不上报协战，`follow_action` 与 `combat_action` 因此不跑（它们本来就是给这两个时刻配色的）。带着生物本身、主人 UUID、在线的主人（离线为空）与契约类型的 `ContractContext` 是这些方法的入参。

**行为（order）是三类东西，都不在接口的固定形状里**：`behaviors()` 是"这只兽认哪些命令"（默认＝`ContractBehaviors.BUILT_IN` 的跟随 / 游荡 / 驻守 / 召回，**两侧都要能答**，御兽铃读它填轮盘页）、`onBehaviorSelected(context, behavior)` 是**输入**（主人下了命令；返回 `false` 即拒绝，记录保持原样）、`tick(context, behavior)` 是每 tick 的驱动（默认分派到 `follow` / `wander` / `stay`，不认识的命令什么都不做）。`wander` 与 `stay` 是新增的默认：前者在主人 32 格外先传送过去（免得游荡的兽被丢下），否则每约两秒、且寻路空闲时在主人周围 3–8 格挑一个新点走过去——**框架不往实体里塞 goal**，所以生物自己的游荡目标照旧，要改由它自己覆写；后者停寻路并清攻击目标（"停下来"而不是"冻住"）。

**行为本身是类，不是枚举**（`data/creature/ContractBehavior` + `ContractBehaviors`，用户点名要求）：`new ContractBehavior(id, momentary)` 一把就是一条命令，`ContractBehaviors.register(...)` 让它能被 id 读回来，内容方因此**不用改框架的清单**就能加一条（"停手""回窝""盘旋"都行）；框架只保证自己的四个内置项一定在。`momentary` 区分"常驻"与"只此一次"（召回的闩与冷却归框架，所以它走 `ContractService.requestRecall`，不写记录）。**当前命令只有一份**，在 `mxt:contract` 附件上（存 id；读不出来的 id 与旧存档一律退回跟随）。**输入端唯一出口**是 `runtime/creature/ContractBehaviorService.request(...)`：御兽铃的轮盘与 `/contract behavior` 都走它，检查顺序是"已绑定 → 主人 → 实现了接口 → 这条命令在它的清单里 → 生物的 `onBehaviorSelected` → 写入或执行一次"。

**御兽铃是指针**：右键生物＝把这只兽对准（服务端把"生物 UUID + 显示名 + 它自己答的命令"写进物品组件 `mxt:contract_bell`），右键空处＝在客户端打开轮盘并停在「契约灵兽」那一页——页面读的正是铃上那份快照，所以不需要在客户端解析一只可能没加载的灵兽。下命令（含召回）是轮盘里的一格，走 `WheelService` 与 `WheelEntryKinds.BEHAVIOR`。

### `CaptureListener`

**被捕捉与释放的通知接口**（2026-09-24；它前身 `Capturable` 的门槛已经取消）：捕捉**不是实体的资格**——任何生物都可能被捕捉，**怎么捕捉由物品决定**（能装什么、要不要契约、代价多少，全是那个物品自己的规则；灵兽袋自己的规则是"你自己的已契约灵兽、一次一只"）。所以这里只剩两个可选钩子：`onCaptured(captor)`（被收走之后，实体离开世界之前调用）与 `onReleased(captor)`（重新回到世界之后），默认什么都不做。**不实现它也照样能被捕捉**，只是收不到这两次通知；`captor` 在不是玩家动手时为空。运行时查找点同样是 `runtime/creature/Contracts`。

这三个接口的查找只有一处（`runtime/creature/Contracts`），卷轴、御兽铃、灵兽袋、命令与两个事件桥都走它；契约类型的字段、代价与上限见 [`contract_type`](../../数据包格式.md#contract_type)，命令见[命令](../play/commands)。

### `Perchable`

让生物**能被"挂"在另一具身体上**（肩挂灵宠那一类）的契约（2026-09-27，见 [`research/56`](../../../research/56_肩挂与挂点设计.md)）：**实现它就是全部**——座位点、记录、容量与所有受理检查都在框架侧（`runtime/perch/PerchService`），所以附属只需要交出自己的生物与它的答案，**不需要为自己造一个座位**。

`perchOffset(vehicle, claimed)` 回答"我想坐在那具身体的哪里"，坐标在**载具自己的坐标系**里：`x` 是载具的左侧、`z` 是车头方向、**`y` 从载具当前顶部往下量**（所以潜行或换姿势时挂着的生物自动跟着走，两边都不必知道姿势）；返回空＝**拒绝这具载具**（框架什么都不写，回一个拒绝结果）。`claimed` 是**已经在同一具载具上就座的**其他乘客声明过的偏移，所以有多种座位可给的生物可以自己挑一个空的（都满了就拒绝，也是合法答案）。偏移会按平台交给座位钩子的 `scale` 等比缩放，与 `AbstractHorse` / `Camel` 那些原版座位同口径。

另外两个可选钩子是**时刻通知**（都有默认空实现）：`onPerched(vehicle)` 在记录写下之后（同一具载具上换个座位**不算**第二次就座、不会再通知），`onPerchReleased(vehicle)` 在记录被清、乘客关系也结束之后——自己下来的、被服务器策略放下的、被判定为失效的都走这一条（载具已经消失的那一种没有载具可传，所以钩子不触发：要精确判断状态就问 `PerchService.perchOffset(生物)`，记录才是唯一真相）。

**为什么不是"接口 + 实体类"**：本体不为它提供任何实体——生物是附属的东西（铁律 9），基座只提供"挂"这件事本身与它要问的那两个问题。查找点就是 `PerchService`：附属在自己的代码里（右键、驯服、任务完成……随便哪个时刻）调 `PerchService.perch(生物, 载具)`，框架去问生物的 `perchOffset`；`PerchService.release(生物)` 是下来的唯一出口，`PerchService.perchOffset(生物)` 是只读查询。**数据包与脚本目前进不来**（没有 `mxt:perch` 动作、没有命令、没有脚本方法）：那是"玩法入口"的决定，与这条契约分开。

### `MountRenderer` / `MountRenderContext`（载具渲染器）

**让别的模组决定载具怎么画**（2026-09-29，见 [`research/61`](../../../research/61_飞行法器渲染系统设计.md)）。`mxt:mount` 本身是"这件法器是飞行法器"的声明（御器之术据此从主手、其次副手取件），怎么画只是它顺带回答的一半；一个实体类型只能注册一个 `EntityRenderer`，所以载具的 `FlyingSwordRenderer` 只负责"摆好朝向并挑一个渲染器"，**画什么由数据包的 `mxt:mount.render` 决定**：默认 `mxt:item`（今天那套物品模型）、`mxt:geckolib`（GeckoLib 模型，装了才有）、以及内容模组注册的类型。

两处注册、**以同一个 `MapCodec` 对象为键**（不是 id，所以两边不可能写歪）：

```java
// common：自己的分派类型（mod 构造期）
public static final DeferredRegister<MapCodec<? extends MountRender>> REGISTRY =
        DeferredRegister.create(MxtRegistries.MOUNT_RENDER_TYPE, MyMod.MOD_ID);
public static final DeferredHolder<..., MapCodec<MyRender>> MY_RENDER = REGISTRY.register("my_vehicle", () -> MyRender.CODEC);

// client：它的渲染器（客户端 setup；旧版是 EntityRenderersEvent.RegisterRenderers）
MountRenderers.register(MyRender.CODEC, new MyVehicleRenderer());
```

三件事在契约里说死：

- **渲染器拿到的是只读的 `MountRenderContext`**：承载的物品、载具实体本身（它的 id、乘客、维度都从这里读）、`display`（**可能为空**，空＝用你这个渲染器自己的默认姿势）、yaw / pitch / 部分刻 / 光照、以及两条**姿态轴**（`MountPose.motion()` 与 `MountPose.crew()`，见 `docs/数据包格式.md` 的「`mxt:mount` 怎么画」）。第二阶段的 `submit` 拿到的还是**同一份 context** 与你自己在 `createState()` 造的那份草稿状态（每辆车每帧一份，别指望它跨帧）。
- **姿态栈交给你时已经站在载具原点、已经转过 yaw**，`pitch` **没有**转——声明过的 `display` 是在"已转 yaw、未俯仰"的坐标系里写的，所以俯仰要你自己接（本体两档渲染器的顺序都是 `translation → pitch → rotation → scale`）。
- **服务端永远不解析渲染器**：类型注册在 common、渲染器注册在 client，中间只靠 codec 对上。因此**这台机器没有渲染器时回落成 `mxt:item` 并记一条警告**（不是报错）——数据包要能在装了与没装 GeckoLib 的两台机器上都能进。类型**没注册**（那个模组不在）才是加载期报错，这是有意的响亮失败。

### `MountVehicle`（载具的实体契约）

**让别的模组自带一种载具本体**（2026-09-29 新增；登记见 [`research/audit/基座缺口审计.md`](../../../research/audit/基座缺口审计.md) 的 B3）。`mxt:mount` 的 `entity_type` 点名一个**已注册的实体类型**，起剑时本体就 `EntityType.create(...)` 它，之后只通过 `MountVehicle` 与它说话。写包的作者因此可以点附属自己的"船 / 轿 / 飞舟"，不必改本体一行代码。

```java
// common：实体类 implements MountVehicle，类型照常注册
REGISTRY.registerEntityType("my_skiff", MySkiff::new, MobCategory.MISC, b -> b.noLootTable().sized(1.4F, 0.6F));
// client：这个实体类型要有自己的渲染器（原版一个 EntityType 只认一个 EntityRenderer）
event.registerEntityRenderer(MY_SKIFF.get(), MySkiffRenderer::new);
```

契约是**七个方法 + `OwnableEntity` 的 `getOwnerReference()`**（与 `api/Contractable` 同一个形状；`level()` 由 `Entity` 提供，`getOwner()` / `getRootOwner()` 从原版白拿）：`setVisual` / `visual`（那件法器，也是定义从哪读）、`setOwner`（原版只读不写，所以写入这一步落在契约里）、`setFlightSpeed`（本体会把"定义的速度 × 术的倍率"夹好后写进来）、`seats` / `freeSeats`、`mountDefinition`（这一趟飞的是哪条定义，空＝没读出来）。

**法器不归实体管**：起剑时交出去的那件法器由**框架**在载具离场时归还——`Entity#remove` 上的 `EntityMountRemovalMixin` 是那处入口，落剑、`/kill`、实体自己 `discard()` 三条路都走它（`FlightService.giveBack` 把 `visual()` 清空之后才交还，所以同一 tick 的丢弃与死亡不会还两遍；`shouldDestroy()` 为假的卸载 / 换维度则把法器留在载具里，跟着它回来）。**附属只需要在离场时给出正确的 `visual()`**，归属与收回的规则不必自己实现。

**其余全是实体自己的事**：移动与落剑判据（本体只读 `horizontalCollision` / `verticalCollision`）、座位与上座、尺寸与坐姿、尾迹、存档与同步，以及定义里那些字段（`width` / `height` / `seat_offsets` / `sit` / `step_height` / `render` / `display`）要不要读——**不读就等于那些字段对它无效**。本体**不提供基类**（`FlyingSwordEntity` 是 `final`），所以"船"的水面移动这类差异化行为完全由附属自己写，这正是这条契约存在的理由。

**三道边界**：①`entity_type` 写的 id 没注册＝**加载期报错**（原版实体类型注册表的 codec）；②类型存在但**没实现 `MountVehicle`** ＝起剑被拒（`FlightService.Failure.INVALID_VEHICLE`，玩家看到的是"骑不上去"，日志点名类型、每个类型只记一次）——**加载期判不了这件事**，"实不实现接口"只有运行期知道；③`create` 只发生在服务端，客户端画什么由那个实体类型自己的渲染器决定。
