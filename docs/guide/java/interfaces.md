---
title: 特殊公开接口
---

本页的实现型接口里，`AuraAccess`、`ItemAuraAccess`、`UseItemAuraAccess`（原 `runtime/spirit`）与 `WheelMenuEntry`（原 `screen/wheel`）已于 2026-09-22 搬进 **`com.iafenvoy.mxt.api`**；该包**只有接口与 `package-info`**，实现仍在各自模块，搬动只改包名与 import。`TooltipAppender` 是 NeoForge 的扩展点、`Cost` 在 `data/cost`；**`ToggableArtifactAbility` 留在 `data/artifact/ability`——它不算对外 API**（它是本体登记法器技能类型的形状，`mxt:flight` / `mxt:storage` 两个固有类型实现它）。哪些东西**不**进 `api` 见 `AGENTS.md` §3：只有"别的模组会实现或调用"的契约才进去，服务类的静态代理是明确的推迟项。

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

**写入者负责汇报**：任何往存储里写入灵气的一方（长按灌注、`AuraAccess` 方块实体等）在**真实写入之后**调用 `onCharged(SpiritSource, stack)`（`simulate` 不算），由物品自己判断"这是不是满了"以及随之而来的行为（符箓在这里发动并消耗一件本体）。之所以由写入者汇报、而不是让物品在自己的 `add` 里判断，是因为只有写入者知道**这东西在哪、谁付的账**：展示架上的一张符，是被站在别处的人（或一枚灵爆）填满的。`SpiritSource(level, position, actor, consumedByHand)` 同时带着位置与行为者——行为者出账、被记录并为能力作答；位置是这次激发的地点，既以 `block_x`/`block_y`/`block_z` 进公式，也作为**原点**交给位置类行为；`consumedByHand` 说明这次是不是"手上的消耗"（展示架、机器等摆着的存储为 `false`）（见 `docs/数据包格式.md` 的「灌注与激发」）。也正因为汇报是"选择加入"的：写入方遇到只实现存储的物品时，本就没有什么可汇报的。

### `TooltipAppender`

物品模块通过 NeoForge `TooltipAppender` 注册 Tooltip。每个模块使用独立 Appender，资源、货币、品质和灵气存储显示互不耦合。

### `Cost`

技能、阵法和其他行为的消耗抽象，提供面向 `Player` 的检查和实际消耗方法。新增 Cost 类型应使用固有注册表分派，而不是在 JSON 中写 Java 类名。

### `WheelMenuEntry`

轮盘条目的纯客户端接口：`kind()`（技能 / 灵气 / 法器技能）、`id()`、`title()`（轮盘中间显示的名字）、可选 `icon()`、强调色、`tooltip(Player)`（类型 + 具体数值）、`cooldownTicks(Player)`（还剩几 tick，0 = 就绪）/ `usable(Player)` 两个可用性钩子，以及使用回调 `onSelected(WheelSelection)`（`WheelSelection` 带着它是在**哪一格的编号**上、以及那一格读自**哪个来源**）。一个来源贡献哪些条目由 `WheelMenuProvider`（唯一实现 `WheelContent`）给出——它的入参是 `(player, source)`，`source` 是 `WheelSource`（主盘 / 主手物品 / 副手物品 / 法器），返回值**可以比一页长**（一页 12 格，多出来的由 `WheelMenuContent` 开新页），条目本身既不知道自己落在第几格，也不知道自己属于哪一页。旧的两个 hotbar 条目接口（`HotbarEntry`）随快捷栏一起删除。

### `ToggableArtifactAbility`

法器能力条目里的**法器技能**（2026-09-22 新增，见 `research/32_法器开关与轮盘接线设计.md`）：它继承 `ArtifactAbility`，判据是一句话——**凡是要按键才发动的都算技能、都进轮盘**。接口把四件事交给实现自己回答：`key()`（同一件法器内唯一的名字，轮盘条目身份 = 法器 id + key）、`displayName()`（这一格叫什么）、`state(ctx)`（有没有开关状态、现在是哪一边；**空 = 一次性**，如储物）、`activate(ctx)`（按下了；只有服务端调，返回 `Result(changed, failure)`，`Failure{NOT_CARRIED, NOT_OWNED, ALREADY_SET, UNAVAILABLE}` 会被轮盘翻译成动作栏那一句）。**状态归实现自己管**（飞行读 `FlightAttachment`，储物没有状态），轮盘不认识"这件事是什么"，只认识这四件事，所以加一个新法器技能不需要动轮盘。今天两个实现是 `mxt:flight`（开关）与 `mxt:storage`（一次性——打开这件法器的储物箱，容器菜单与窗口都复用原版箱子那一套，见 `docs/guide/java/screens.md`）。字段与玩家侧表现见 `docs/数据包格式.md` 的 `artifact` 一节。
