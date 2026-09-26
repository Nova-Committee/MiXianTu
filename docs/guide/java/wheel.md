---
title: 客户端轮盘
---

12 扇轮盘是技能（法器技能已并入其中）与灵气的**唯一触发入口**：框架在 `screen/wheel`，内容在 `screen/wheel/content`。它替换掉了原来"技能与灵气各有一条客户端 Hotbar、各有一个配置界面"的两套东西（过程见 `research/28_技能与灵气归一化设计.md`）。

**轮盘由"主盘 + 从盘"组成，用一套连续编号串起来**（2026-09-22 新增并按玩家口径重做，见 `research/31_多轮盘与轮盘来源设计.md` §10）：

- **主盘** = 玩家的 12 格自定义布局，编号 `0..11`；**打开永远回到它**。
- **从盘**（`api/WheelSource` 的实例，**注册顺序即编号顺序**）按随身装备自动生成，格子从 `12` 起接着排：

| 来源 id | 名字 | 内容从哪来 | 存不存 |
| --- | --- | --- | --- |
| `mxt:configured` | 主盘 | 附件 `wheel_layout` 里玩家自己摆的 12 格 | 存（只有它存） |
| `mxt:main_hand` | 主手物品 | 主手物品那条授予来源现在授予的主动技能，加上它这件法器声明的技能 | 不存，现读 |
| `mxt:off_hand` | 副手物品 | 副手物品那条授予来源现在授予的主动技能，加上它这件法器声明的技能 | 不存，现读 |
| `mxt:curios` | 法器 | Curios 已装备物共用的那条授予来源现在授予的主动技能，加上这些法器声明的技能 | 不存，现读 |
| `mxt:contract` | 契约灵兽 | 手里**御兽铃**对准的那只灵宠认的行为（铃上的组件 `mxt:contract_bell` 记着它） | 不存，现读 |

- **来源不是枚举**：`api/WheelSource` 是一个接口，框架内置上面五条，**内容模组可以自己注册一条**（`WheelSourceTypes.register`，id 唯一、先注册者胜），它的页就与内置页一起参与编号与翻页；页顺序就是注册顺序。接口回答四件事：`id()` 与 `displayName()`（这一页叫什么）、`configured()`（是不是玩家自己摆的那一盘）、`grantSources(entity)`（这一页由哪些授予来源拼成）、`equipment(entity)`（这一页从哪几件栈上读承载物）、`offers(entity, kind, id)`（这一项此刻能不能从这一页触发）。

- **一页 12 格**：每个来源占 `ceil(条目数 / 12)` 页，**一条都没有就一页都不占**（主盘永远占一页，它的 12 格本来就在）。所以"一个从盘不够用就再开一个新的"——15 个技能占两页，第 2 页 3 格 + 9 个空格，**没有条目会被丢掉**。
- 页会随装备出现和消失，**所以后面的编号会跟着前后移**：编号是"第几格"这个位置，不是某个条目的身份（这正是玩家要的口径：物品拿走时编号不动，物品回来时同一个编号指回同一个技能）。
- 页列表只在客户端（`screen/wheel/WheelMenuContent#pages`）；服务端从头到尾不知道有几页。

从盘读的是**技能授予账**（`AbilityAttachment` 的 `SourceLedger`），而它本来就是按来源记的（`AbilityEventBridge#onEquipmentChange` 记 `mxt:equipment/<槽位>/<物品>`，Curios 记 `mxt:curios_equipment`）。来源 id 的写法为此收进 `runtime/ability/AbilitySources`（`equipment(slot, stack)` / `CURIOS`），授予侧与轮盘侧共用一份定义。读取统一在 `runtime/wheel/WheelSources`：`abilities(entity, source)` 给出来源现在的全部**可按技能**（凡实现 `Toggable` 的，按 id 排序、**不截断**——分页是客户端的事），`abilities(entity)` 给出玩家**持有的全部**可按技能（主盘那个池子读它），`equipment(entity, source)` 给出这一页该读哪几件装备的栈（主盘给双手 + Curios），`carrier(entity, source, id)` 给出这一项此刻的**承载物**（这一页上提供它的那件栈），`offers(entity, source, kind, id)` 回答"这个来源现在认不认这一项"。**从盘没有任何存储**：撤销授予（把物品换掉、摘掉法器）那一刻它自己就空了——技能本身记在账本上，而"哪件物品提供它"是每刻现读的。

**需要按键的技能（`Toggable`）。** 判据只有一句：**凡是要按键才发动的都算技能，都进轮盘**。技能的 `type` 实现了 `Toggable` 就是这样的东西——实现这个接口等于声明"把我放进轮盘"。它既包括**一次性**（`mxt:active`：施放一次；`mxt:storage`：按一下打开承载物的储物箱，没有任何状态留下），也包括**开关**（`mxt:flight_control`：开＝起剑、关＝落剑）。接口把三件事交给实现回答：

| 方法 | 谁问、问什么 |
| --- | --- |
| `Optional<Boolean> state(ctx)` | 有没有"开着/关着"这回事，现在是哪一边；**空 = 一次性**（施放与储物都是空的）。两侧都问：客户端画状态，服务端据此决定做什么。 |
| `Result activate(ctx)` | 按下了。只有服务端调，返回 `Result(changed, failure, failedResource)`——「做了没有 + 为什么没做 + 缺的是哪门资源」（`Failure` 的取值见下）。 |

（2026-09-23 起接口只有这两件必答的事**加上**一个有默认实现的 `gated`：早先的 `key()` 与 `displayName()` 是"内联技能"那套身份的遗留，内联取消后一并删除——这一格的名字用技能自己的 `name`。同日 `Result` 多了第三个分量、`Failure` 也不再是自己那一套小枚举。）

**`Togglable.Failure` 的 15 个取值与 `AbilityService.Failure` 同名同义**（`NOT_OWNED` / `ALREADY_SET` / `UNAVAILABLE` / `NO_CARRIER` / `CANNOT_MOUNT` 是按压独有的五个：所有权、状态已经是这样、说不清、没有承载物、骑不上去；其余十个两边共有）。`AbilityActivationService.failureOf(...)` 只做名字搬运，**不再把管线区分得出来的原因折叠成 `UNAVAILABLE`**——2026-09-23 之前它只映射冷却 / 代价 / 未持有三种，`CONDITION_FAILED`、`ELEMENT_AFFINITY`、`NO_CHARGES`、`INVALID_FORMULA` 全被压成「现在用不了」，玩家和日志都查不出所以然。今天轮盘的动作栏文案只有一份表 `actionbar.mxt.ability.failure.*`，日志里的原因也是真的；`UNAVAILABLE` 是兜底：拿不到服务端玩家、按下的东西根本不是 `Toggable`、飞行 `startRiding` 失败。`INVALID_FORMULA` 兼管"实现自己的数算不出来"——储物 `slots` 公式算出 ≤ 0 报的就是它。

付费与冷却走一处：`gated(ctx)` 默认 `true` 时，`AbilityActivationService` 先过一遍共用闸门（条件 + 冷却 + 技能自己的 `costs`，整组全有或全无），过了才调 `activate`；**开关往"关"的那一下 `gated` 返回 false**（落地不该收费），`mxt:active` / `mxt:channelled` 也返回 false——它们的施放事务自己付款，重复收一次就错了。因此：

- 它出现在**提供它的那张从盘**上。**能不能钉到主盘看它在哪**：主手 / 副手物品声明的技能**不进配置界面右侧那个池子**（手里拿什么随时会换，那两张从盘页才是它的位置），**Curios 槽位上法器声明的技能**则和学到的技能一样可以钉；已经钉好的格子照旧解析、服务端照旧受理（2026-09-26 收紧，池子取自 `WheelContent#options`，`#pool` 仍是那 12 格的解析名单）。
- **一件法器可以给出好几条**（同一把剑既能飞又能储物），每条都是 `mxt:ability` 里**自己的注册表条目**，轮盘格子的身份就是它自己的 id；所以一本书授予的主动技和一件法器给的开关在轮盘上是同一类格子。这类格子的类型是 `mxt:ability`，它的 `exists(access, id)` 只要求这个 id 能解析成一条技能（写法器 id 不算——那是定义，不是技能）。
- **状态归实现自己管**：飞行读 `FlightAttachment`（**每个实体**一份、记着**是哪条技能**在飞，已同步给本人与追踪它的客户端；轮盘只对玩家开），储物与施放没有状态。没有通用开关存储。
- 客户端画的是**报告**，不是指令：开着的开关绿、关着的灰、一次性的紫；tooltip 的**最后一行写来源**（「学习的技能」/「X的技能」/「其它来源」，2026-09-26 由原来那行「法器：X」改成这一行并移到末尾；X 是承载物品名，按品质上色、没有品质就金色），另有开关状态，一次性没有状态那一行。**格子不画物品图标，画技能名**——一件法器的两个技能若都画同一把剑的图标就分不出谁是谁。
- **储物那一格打开的是原版箱子菜单**（`MxtMenus.ARTIFACT_STORAGE` 是 `ChestMenu`，客户端注册 `ContainerScreen`），窗口标题是技能名，内容是承载物自己那份 `mxt:artifact_storage`。容器是 `ArtifactStorageContainer`：一个写入即回写的实时视图，**不持有栈**——每读每写都按"这件承载物还在不在玩家身上"重新解析，一离身 `stillValid` 就是假，服务端每刻检查菜单并把窗口关掉，所以不会往一个没人拿着的栈里写东西。

**框架不决定轮盘上有什么。** `WheelMenuProvider` 回答"这个来源现在贡献哪些条目"（`entries(player, source)`，可以比一页长），唯一实现是 `WheelContent`，由 `MiXianTuClient#init` 里的 `WheelContent.register()` 登记。`WheelMenuEntry` 是条目契约：

```java
public interface WheelMenuEntry {
    WheelEntryKind kind();                       // ABILITY / AURA / BEHAVIOR
    Identifier id();                             // 技能是 Holder<Ability> 的 id；行为是代码里的 ContractBehavior id
    Component title();                           // 轮盘中间显示的名字
    Optional<IconReference> icon();              // 扇区里画的图标（可无）
    List<Component> tooltip(Player player);      // 类型 + 具体数值
    long cooldownTicks(Player player);           // 还剩几 tick，0 = 就绪
    default boolean usable(Player player);       // 默认 cooldownTicks <= 0
    void onSelected(WheelSelection selection);   // 使用回调（轮盘仍开着）
}
```

两个实现是 `AbilityWheelEntry`（技能：名称、图标、消耗、冷却、状态色，以及"这一项来自哪件承载物"；不论技能是谁给的都用它）与 `AuraWheelEntry`（灵气：发射量、余量、元素），它们不同的部分留在各自里面；轮盘、配置界面与网络只认 `kind + id`。

**12 格内容存在服务端。** 玩家附件 `WheelLayoutAttachment`（`wheel_layout`）存一份 12 格 `WheelLayout`（主盘），每格是 `WheelSlot`（`WheelEntryKind` + id，空格用 `EMPTY` 哨兵），另有一个 `armed` 字段（`Optional<Integer>`，见下）存**当前选中的格子编号**。配置界面关闭时发 `WheelLayoutC2SPayload` 上送，服务端 `WheelService.sanitize` 把大小强制成 12、逐格校验 id 能否在对应注册表里解析，再写回附件；附件同步给本人，因此轮盘与配置界面读到的就是服务端会执行的那一份。没保存过（附件里没有）时客户端读到的就是 **12 个空格**——**不做任何默认填充**（2026-09-22 按用户要求删掉原来的"前 6 个灵气 + 前 6 个技能"派生填充，理由见 `research/28` §4.2：放什么是玩家的决定，而"按当前可用池现算"的那份填充还会在第一次保存前自己换位置）。代价是**整张轮盘一格有内容的都没有**时按 `R` 不开（`WheelMenuContent#hasAnyEntry`，判的是所有页的所有格子——主盘空但手里那把剑有技能时也要能开），新档第一次要先用 `/wheel` 放条目。老存档里叫 `selection` 的那个字段现在**不再被读**（`RecordCodecBuilder` 忽略不认识的键），于是布局保住、选中项丢一次。

**存的是编号，不是条目。** 附件里那个 `armed` 就是一个 `int`——格子按整张轮盘连续编号，所以编号是一个**位置**，不是某条东西的身份。它由 `screen/wheel/content/WheelSelectionSync` 负责两个方向：登录后从同步过来的附件里把编号读回来（`WheelSelectionState#restore`，**不解析、不校验、不报 warning**：编号本来就允许"这个位置上现在什么都没有"）；编号变了才上送 `WheelSelectionC2SPayload`（`Optional<Integer>`），配置界面保存布局时强制重发一次（页列表刚被换掉）。服务端 `WheelService#sanitizeArmed` 只做范围检查（`0..MAX_ARMED`），存下来就是全部。客户端状态里因此有**两个不同的值**：`number`（存的那个编号）与 `effective`（它**此刻**代表哪一格）。

**编号解析不出"有内容的格子"时回退到第一个有东西的格子。** `WheelMenuContent#effective(pages, number)` 的规则：

| 情况 | 结果 |
| --- | --- |
| `number < 0`（从没选过） | **第一个有东西的格子**（`firstHeld`；整张轮盘一格都没有时才是"没有目标"） |
| `number` 在现有格子里、那格有东西 | 就是它 |
| `number` 在现有格子里、那格是空的 | **第一个有东西的格子**（同上） |
| `number` 指向**根本不存在**的格子（页没了、或那一页的条目变少了） | **最后一个有东西的格子**（从后往前找，老规则不变） |

**"永远有一个选中"是刻意的（2026-09-22 用户点名）**：轮盘打开时 `WheelSelectionState#selectDefault()` 把"从没选过"落成第一个有东西的格子（HUD 金框因此一开始就画在那一格上），此后无论格子怎么来去，`effective` 都不会是"没有目标"——**只有整张轮盘一格内容都没有**才落空，那种情况下按 `V` 只说「轮盘上还没有任何条目」。**指针那条口径没变**：`WheelMenuScreen#selection` 仍然直接读指针所在的格子、为空格子返回 null，所以"指着空格子按 `V`"仍然什么都不做；变的只是**保存的选择**——`selectSector` 不再把空格子写进编号（指针扫过空格子时，选择留在上一次真正选中的那一格）。**`number` 在任何分支里都不被改写**，所以物品拿回来、格子回来了，同一个编号又指回同一个技能。金框画在 `effective` 上，`V` 花掉的是 `effective`，发出去的编号是 `number`。

**触发只有一条通道**：`WheelActionC2SPayload(source, kind, id)` —— **来源 + 条目类型 + 这一项**，前两者是 **id 字符串**（不是注册对象：这一侧不认识的页或类型会被服务端按"拒绝并说明"处理，而不是让包解不出来，内容模组因此不必自带协议版本），由客户端在按下的那一刻从**当时那一格**解析出来 → 服务端按 id 取回页与类型（`WheelSourceTypes.byId` / `WheelEntryKinds.byId`）再 `WheelService.trigger`：先要求**这个来源现在仍然认这一项**（`WheelSources#offers` 转发给来源自己的 `offers(...)`：主盘读存档布局，装备页与法器页读授予账与该页装备此刻是否仍提供这条技能，`mxt:contract` 页读铃组件里那只灵宠认的行为表），认下来才把这一格交给**它自己那个 kind** 的 `trigger(...)`：技能走 `runtime/ability/AbilityActivationService.activate`（`mxt:active` 在那里转成一次施放，`mxt:flight_control` / `mxt:storage` 各自做自己的事，共用闸门只过一遍）、灵气走 `SpiritBurstService.fireOnce`（校验元素、使用条件、冷却与余量后发一发 `SpiritBurstEntity`）、行为走 `runtime/creature/ContractBehaviorService.request`（当场从铃回查那只灵宠，再查记录、主人、它认不认这条命令，召回则走 `ContractService.requestRecall`）。**请求只说"按了这一格"，不说该往哪边走**：方向归服务端，所以客户端即使把状态猜错了也提不出一个不可能的状态，包也不用带方向（`WheelActionC2SPayload` 上那个可选的 `enabled` 是给脚本与界面**点名一个状态**用的，轮盘自己永远留空；旧的 `FlightToggleC2SPayload` 已于 2026-09-25 并入它）。**主盘也校验**：布局本来就是客户端交上来的，这道检查不是防作弊，而是让"这一项确实来自你说的那个来源"对所有来源都成立——配置界面刚清掉一格、玩家手里还按着 `V` 时，那次请求会被拒。**编号本身不参与触发**：它只回答"打哪一格"。

**两个键分工（`research/27` §6）。** `key.mxt.wheel`（默认 `R`）只负责**选**：按住打开轮盘、指针决定格子，松开（HOLD）或再按一次（TOGGLE）**只关闭、不触发**；**打开始终回到主盘（第一页）**。`key.mxt.wheel_use`（默认 `V`）负责**用**：轮盘开着时用掉指针当前那一格且**不关轮盘**，关着时用掉**记住的编号此刻代表的那一格**；鼠标左键等同于它。

**页是视图，编号才是选择**（`WheelSelectionState` 里是 `page` + `number` 两个值）。轮盘界面画当前页的 12 格；HUD 轮盘格**把每一页都画出来**（四列、向下长，见下），所以它不需要"当前页"；12 个槽位键作用于当前页；关着轮盘时的 `V` 作用于**编号此刻代表的那一格**（可能在任何一页上）。切换是 `key.mxt.wheel_previous` / `key.mxt.wheel_next`（**默认键盘左 / 右方向键**，`InputConstants.KEY_LEFT` / `KEY_RIGHT`，可在按键设置里改）+ **轮盘界面里的鼠标滚轮**（向上＝上一页、向下＝下一页，客户端配置「轮盘选择 → 滚轮翻页」默认开，关掉时 `mouseScrolled` 交回 `super`）：三条路径都走同一个 `WheelMenuController#stepPage`（`stepPage` + `refresh` + 那句动作栏提示），所以不会在"翻到哪页、报不报"上漂。**轮盘开着关着都能用**（滚轮那条只在界面开着时存在），翻到头是绕回第一页还是停在两端由客户端配置「轮盘选择 → 循环翻页」决定（**默认绕回**；`WheelSelectionState#stepPage(delta, wrap)` 只在这一处读它），切完在动作栏报一句「轮盘 2/3：主手物品」——从盘的页由随身物品决定，摘掉东西后那几页就没了，不报一声玩家不知道自己站在哪一页上。**切页不动编号**（切换不是瞄准），因此也不会因此上送什么：编号没变就没有包。有别的界面打开时不动作，与其余轮盘键同一口径。因此：

- 编号存在客户端 `WheelSelectionState`（`number`，`-1` = 还没选过）里：轮盘打开期间每帧只在**指针所在的格子真的有内容**时才写入编号（空格子不写，所以指针扫过空框不会把选择清掉），关轮盘后保留，`ClientPlayerNetworkEvent.LoggingIn` 清空，随后由服务端记住的 `armed` 填回来（跨会话那半见上一节）。同一份状态里还存着**这一客户端刻解析出来的全部页**（`pages()`，每页恒 12 格、`null` = 空格子），因为 HUD 那张网格每帧要读每一页的每一格，而解析要走一遍灵气注册表与"能不能发射"的公式，所以只在每刻刷新一次（`refresh`）；切页时控制器立刻再刷一次，本刻的画面与 HUD 才不会差一拍。条目在**用之前**才解析，所以授予被撤销 / 定义被删 / 灵气付不起时那一格当场变成"没有目标"，按 `V` 什么都不做。
- **这把键与切换键都读原始输入**（`WheelMenuController` 用 `InputConstants`/GLFW 轮询），不走 `KeyMapping`：屏幕一打开原版就 `KeyMapping.releaseAll()`（§1.4 的老坑），而且"按着 `V` 松开 `R`"会让 `grabMouse()` 里的 `KeyMapping.setAll()` 把 `V` 重新置为按下、补出一次假按下，等于多触发一次。一个键只在一处判边沿就没有这个问题。关着的时候还要求没有任何界面打开，否则在聊天栏打 `v` 就会放技能。
- **按了没生效时给一句动作栏提示**（2026-09-22 追加，见 `research/27` §9）：现在只有一种情况——`open(...)` 与 `use(...)` 的"整张轮盘一格内容都没有"都发 `actionbar.mxt.wheel.empty`。**`actionbar.mxt.wheel.no_selection` 已随"永远有一个选中"删除**（"从没选过任何格子"不再是可能状态）。**刻意不给提示的情况**：指针所在的格子当前解析不出条目（空格子、定义被删、授予被撤销）——那在轮盘格上本来就画成空框，按 `V` 也不关轮盘，所以 `usePointed(...)` 的 `selection == null` 分支保持完全静默（第一版曾给它加过 `actionbar.mxt.wheel.no_entry`，用户看过之后点名去掉）。走**客户端** `Minecraft#gui#setOverlayMessage`，不发包——服务端从头到尾不知道有这次按键。**有别的界面打开时也保持沉默**（裸轮询照样读得到物理按键，但那时是在聊天栏打字/翻背包，不是请求）。
- `release_to_select` 配置**已删除**（松开不再选中）；`mode`（按住 / 切换）保留。`WheelSelection.Method` 由 `RELEASE`/`CLICK` 改为 `KEY`/`CLICK`，只表示"是键盘还是鼠标要求的"。
- **12 个槽位各有一把键，默认全部未绑定、单独一个分类**（`research/27` §10）：`MxtKeyMappings.WHEEL_SLOTS` 是 `List<KeyMappingHolder>`，由 static 块里的一个 `for` 按 `WheelGeometry.SECTORS` 填出（键名 `key.mxt.wheel_slot.1` … `.12`，分类 `mxt:wheel_slot` =「**觅仙途：轮盘槽位**」/ "MiXianTu: Wheel Slots"——分类名带模组名，因为原版把分类平铺在按键设置里）。**分类内的顺序由 `order` 决定**：原版按键列表是 `Arrays.sort` → `KeyMapping#compareTo`，同一分类里**先比 `order`、`order` 相同才比"翻译后的显示名"**（`I18n.get(name)`），所以第一版不补零会排成 `1、10、11、12、2、3…`（用户实测截图发现）。做法是给每把键传 `sector` 当 `order`（`KeyMappingHolder` 为此新增一个转发到原版五参 `KeyMapping(...)` 的构造器），于是显示名可以是老实的「槽位 1」…「槽位 12」，顺序在任何语言下都固定。**下标 `i` = 页内格子 `i`，而键名里的数字是 `i + 1`**——配置界面给 12 格的编号是 `1` 在正上方、顺时针，所以"槽位 1"就是当前页的第 0 格。按下槽位键 N = **在当前页上选中第 N 格并立刻用掉**（等于"把指针指过去再按 `V`"）：`useSlotKey(...)` 先算出编号（`page * 12 + sector`）、取出那一格、`WheelSelectionState.selectSector(sector)`（HUD 金框立刻跟过去，编号随之跨会话持久化），再 `entry.onSelected(...)`；空格子按下什么都不做也不提示（与 `V` 同一口径）。**`R`/`V`/左右切换与这 12 把键都由 `WheelMenuController` 裸轮询，不走 `KeyMappingHolder` 回调**：`setAll()` 会伪造一次按下，对施法就是白放一个技能（§6.3）；边沿照常采样、只在"没有别的界面打开，或轮盘正开着"时动作，于是在聊天栏里打字不会施法、按着槽位键开关一次界面也不会补放。
- 轮盘中间那一行在可用时写「按 `V` 使用」（键名取实际绑定），不可用时写「冷却中 4.3s」——剩余时间按 tick 读（附件里存的是冷却结束的那一 tick），由 `WheelDuration.seconds` 统一写成**永远一位小数**的秒数，与 tooltip 里的「冷却 / 施法」同一种写法。**环的上方另有一行页号**「轮盘 2/3：主手物品（两把切换键的实际绑定）」，键名同样取实际绑定；环本身画哪一页都长一个样，没有这一行就分不出自己站在哪。
- HUD 上有一个可拖动元素「**轮盘格**」（`screen/wheel/WheelSelectionEntry`，布局键 `wheel.selection`）：**永远是 4 列，行数随内容的格子数向下长**——它是**整张轮盘的一览**，不是当前页。**只有主盘画空格子**：那 12 个空框是玩家自己摆的布局，空着就是要看得见；从盘的页只画它真正贡献的那几格，所以 3 个技能的页就是 3 格，不是 3 格 + 9 个空框（因此页边界不再一定等于行边界）。**块的上方不写任何字**：它是拿来看的，哪一页由轮盘自己说。每格画图标或名字开头、底边一条类型色；**冷却中的格子按原版物品那样压一层白幕**（盖住图标的剩余比例、随时间从上往下退，剩余取条目的 `cooldownTicks`、全长取 `cooldownLength`），其它原因不可用时压一层暗色；**编号此刻代表的那一格换成金色边框的贴图**（`slot_22_selected.png`，与配置界面选中的候选格、轮盘上指针所在格子的金色是同一套语汇）。它整块自绘（`renderBlocks()` 返回空、走 `render()`），默认位置是**窗口左边、竖直居中**；尺寸每帧按内容算（`layoutWidth` / `layoutHeight` 是动态的，`refreshPlacement` 里 `setSize` 回报给框架，长出去会被夹回窗口）。它在 `MiXianTuClient#init` 与资源条一起登记，关着也能按 `V` 这件事靠它才不盲目。

**按了没反应时会被告诉原因。** 轮盘这条路上有三处会拒绝请求，而客户端从画面上分不出来，所以三处都会说话：服务端重读来源后发现**这一项已经不在那个来源上**时（`WheelSources#offers` 之后那一步解析不出技能也走这一句），日志记一条 info、动作栏报「轮盘上的这一项已经失效了」；技能管线拒绝这次施放时（灵根不符、资源不足、冷却、条件不满足、次数用完、没有权限……），`AbilityService` 返回的结果不再被丢掉——`AbilityActivationService.failureOf` 把原因**原样**带成 `Togglable.Failure`（不再折叠），日志记一条 info（含原因名与缺的那个资源），动作栏按同一份文案表报出来：按压走「使用失败：<原因>」（`actionbar.mxt.wheel.use_failed`）、非按压技能的施放走「施放失败：<原因>」（`actionbar.mxt.wheel.cast_failed`），两条后面接的都是 `actionbar.mxt.ability.failure.*`，缺资源的还会点名是哪一门。灵气发射失败同理报「灵气没能发射出去」。这一条是照 `CultivationModeService#notifyFailure` 的口径做的：**拒绝要说出来，不能只有"按了没反应"**，而且说出来的必须是**真的那一条**。

**加一种新条目**：实现 `WheelMenuEntry`，再让 provider 把它放进某个来源即可——轮盘的几何、分页、渲染、开合与选择语义都不用动，工具提示自己拼（`WheelTooltips` 里有共用的数值、消耗与元素写法）。**加一类新东西**（既不是技能也不是灵气）实现 `api/WheelEntryKind` 并 `WheelEntryKinds.register`：接口上的 `exists(access, id)` 决定这一格还算不算数（读不出来的格子在存盘时被清掉）、`trigger(player, source, id)` 就是"按下这一格做什么"（开关那类再实现 `directed(...)`），配置界面按 kind 分池、服务端按 kind 分派都不再需要改。**加一个来源页**实现 `api/WheelSource` 并 `WheelSourceTypes.register`，再把客户端的 `WheelMenuProvider` 用 `WheelMenuContent.register(source, provider)` 注册到**这一页的 id** 上（多槽位，互不覆盖）：编号、翻页、校验与"这一项还在不在"都自动接纳它。契约行为（`mxt:behavior`，2026-09-25）与契约页（`mxt:contract`）在改造前就是靠改枚举、`switch` 与单一 provider 加进去的——现在这两条扩展点不再需要动框架代码了；`WheelService` 里只剩三个内置 kind 的实现（按压 / 灵气 / 契约指令）与公共的拒绝播报。**加法器技能**只做两件事：写一个新的 `mxt:ability_type` 条目并让它实现 `Toggable`；如果它需要物品，就在 `activate` 里对 `ctx.carrier()` 判空并返回 `NO_CARRIER`——轮盘、配置池、触发与文案都会自己接纳它，唯一要记住的是"轮盘格子的身份就是这条技能自己的注册表 id"。

**格子里画什么。** 每个格子（环上的扇区与 HUD 轮盘格）先画条目的 `icon()`；**没有图标时改画名字**——`IconRenderer.renderName` 取 `title()` 里放得下的开头几个字，画在图标的位置上，所以一圈填满没有图标的条目也不会出现空格子。环上文字宽度按该半径上一扇的弧长减去留白算（`WheelMenuScreen#labelWidth`），因此相邻扇区的文字不会互相压；完整名字始终在轮盘中间与 tooltip 里。配置界面那一排 12 格同理（`IconRenderer.renderOrName`：有图标画图标，没有就画名字开头），22px 的格子只放得下两个汉字，全名看 tooltip。

编辑界面是 `WheelConfigurationScreen`：左边 6 列灵气池、右边 6 列"技能"池（各自滚动、各自 tooltip，右池来自 `WheelContent#options`：**学到的技能**（功法 / 灵根 / 体质 / 境界授予）**+ Curios 槽位上法器声明的技能**；主手 / 副手物品声明的技能不进池，那两张从盘页才是它们的位置——2026-09-26 收紧），下面一排 12 格是**主盘**的 12 格（共用，任意放），`Esc` 保存并关闭。**界面里没有说明文字**（"Esc 保存并关闭"、从盘来源与切换键、扇区编号与放法这四句已于 2026-09-26 按用户要求从界面删除）：12 格紧贴分隔线、编号一行在格子上方，格子下方一行写**每个扇区各自的槽位按键**（`MxtKeyMappings.WHEEL_SLOTS`，`key.mxt.wheel_slot.1` … `.12`），形如 `[Z]`；**没绑定的格子什么都不写**（不显示原版的"未知"占位），名字比 24px 的格子宽还长时截断。**只有主盘可编辑**：从盘的内容由随身物品（与手里的铃）决定，站在界面里预览的与战斗里看到的不是同一份，所以干脆不预览。两个入口都是**纯客户端**的（界面读的是同步过来的附件与注册表，服务端无事可做）：客户端命令 `/wheel`，以及按键 `key.mxt.wheel_configuration`（**默认未绑定**）。主盘上钉了一条只有某件法器才给的技能、而那件法器已经不在身上时，那一格与"技能被撤销"同一表现：配置界面画 `?`、轮盘上什么都不画，按下去由服务端拒绝并提示「这件法器不在身上」对应的那类原因。
