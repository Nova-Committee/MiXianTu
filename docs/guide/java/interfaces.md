---
title: 特殊公开接口
---

本页的实现型接口里，`AuraAccess`、`ItemAuraAccess`、`UseItemAuraAccess`（原 `runtime/spirit`）与 `WheelMenuEntry`（原 `screen/wheel`）已于 2026-09-22 搬进 **`com.iafenvoy.mxt.api`**；同一天这一族又多了三个生物侧契约（`Contractable`、`ContractOperations`、`CaptureListener`，见文末三节）。该包**只有接口与 `package-info`**，实现仍在各自模块，搬动只改包名与 import。`TooltipAppender` 是 NeoForge 的扩展点、`Cost` 在 `data/cost`；**`Toggable` 留在 `data/ability`——它不算对外 API**（它是本体登记"需要按键的技能"的形状，`mxt:active` / `mxt:flight_control` / `mxt:storage` 三个技能类型实现它）。哪些东西**不**进 `api` 见 `AGENTS.md` §3：只有"别的模组会实现或调用"的契约才进去，服务类的静态代理是明确的推迟项。

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

**需要按键才能发动的技能**（2026-09-22 作为 `ToggableArtifactAbility` 诞生，2026-09-23 合并后升格为与宿主无关的 `Toggable`，同日内联技能取消后删掉了它的 `key()` 与 `displayName()`，见 `research/40_能力与法器能力合并设计.md`（§12 记了同日的两次收缩））：判据是一句话——**凡是要按键才发动的都算技能、都进轮盘**。它是**数据层**的接口，不是 `api` 包里的对外契约，任何 `mxt:ability_type` 都能实现它。接口把三件事交给实现自己回答：`state(ctx)`（有没有开关状态、现在是哪一边；**空 = 一次性**，如储物）、`activate(ctx)`（按下了；只有服务端调，返回 `Result(changed, failure, failedResource)`，`Failure` 的 16 个取值（多一个 `NO_VEHICLE`：御器之术在主手与副手都没找到飞行法器）与 `AbilityService.Failure` 同名同义，会被轮盘翻译成动作栏那一句——按压与施放共用 `actionbar.mxt.ability.failure.*` 一份文案表，见 [`docs/guide/java/wheel.md`](wheel.md)），以及一个有默认实现的 `gated(ctx)`（这次按压要不要先过共用的"条件 + 冷却 + 消耗"闸门；开关在**关**的那一下返回 false，因为落地不该收费）。**这一格叫什么用技能自己的 `name`**，接口不再另给一个名字；`type` 也不需要报一个"宿主内的 key"——轮盘条目的身份就是这条技能的注册表 id。**状态归实现自己管**（飞行读**驾驶者**的 `FlightAttachment`——记着飞的是哪条术、哪辆车，储物没有状态），轮盘不认识"这件事是什么"，只认识这几件事，所以加一个新技能类型不需要动轮盘。今天**四个**实现是 `mxt:active`（原本就按一下施放——它 `gated` 返回 false，因为施放事务自己付款）、`mxt:channelled`（同上：一次完整施放开始引导，之后按 `tick_interval` 收维持费）、`mxt:flight_control`（开关：起剑 / 落剑；它从主手、其次副手取那件飞行法器，落地时原样归还）与 `mxt:storage`（一次性——打开承载物的储物箱，容器菜单与窗口都复用原版箱子那一套，见 `docs/guide/java/screens.md`）。字段与玩家侧表现见 `docs/数据包格式.md` 的 `ability` / `artifact` 两节。

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
