---
title: 客户端轮盘
---

12 扇轮盘是技能、灵气与法器技能的**唯一触发入口**：框架在 `screen/wheel`，内容在 `screen/wheel/content`。它替换掉了原来"技能与灵气各有一条客户端 Hotbar、各有一个配置界面"的两套东西（过程见 `research/28_技能与灵气归一化设计.md`）。

**轮盘由"主盘 + 从盘"组成，用一套连续编号串起来**（2026-09-22 新增并按玩家口径重做，见 `research/31_多轮盘与轮盘来源设计.md` §10）：

- **主盘** = 玩家的 12 格自定义布局，编号 `0..11`；**打开永远回到它**。
- **从盘**（`runtime/wheel/WheelSource`，枚举顺序即编号顺序）按随身装备自动生成，格子从 `12` 起接着排：

| `WheelSource` | 名字 | 内容从哪来 | 存不存 |
| --- | --- | --- | --- |
| `CONFIGURED` | 主盘 | 附件 `wheel_layout` 里玩家自己摆的 12 格 | 存（只有它存） |
| `MAIN_HAND` | 主手物品 | 主手物品那条授予来源现在授予的主动技能，加上它这件法器声明的技能 | 不存，现读 |
| `OFF_HAND` | 副手物品 | 副手物品那条授予来源现在授予的主动技能，加上它这件法器声明的技能 | 不存，现读 |
| `CURIOS` | 法器 | Curios 已装备物共用的那条授予来源现在授予的主动技能，加上这些法器声明的技能 | 不存，现读 |

- **一页 12 格**：每个来源占 `ceil(条目数 / 12)` 页，**一条都没有就一页都不占**（主盘永远占一页，它的 12 格本来就在）。所以"一个从盘不够用就再开一个新的"——15 个技能占两页，第 2 页 3 格 + 9 个空格，**没有条目会被丢掉**。
- 页会随装备出现和消失，**所以后面的编号会跟着前后移**：编号是"第几格"这个位置，不是某个条目的身份（这正是玩家要的口径：物品拿走时编号不动，物品回来时同一个编号指回同一个技能）。
- 页列表只在客户端（`screen/wheel/WheelMenuContent#pages`）；服务端从头到尾不知道有几页。

从盘读的是**技能授予账**（`AbilityAttachment` 的 `SourceLedger`），而它本来就是按来源记的（`AbilityEventBridge#onEquipmentChange` 记 `mxt:equipment/<槽位>/<物品>`，Curios 记 `mxt:curios_equipment`）。来源 id 的写法为此收进 `runtime/ability/AbilitySources`（`equipment(slot, stack)` / `CURIOS`），授予侧与轮盘侧共用一份定义。读取统一在 `runtime/wheel/WheelSources`：`abilities(entity, source)` 给出来源现在的全部技能条目（只列 `mxt:active`、按 id 排序、**不截断**——分页是客户端的事），`equipment(entity, source)` 给出这一页该读哪几件装备的栈（主盘给双手 + Curios，理由见下），`toggles(entity, source)` 给出这些栈声明的**法器技能**（见下一节），`offers(entity, source, kind, id)` 回答"这个来源现在认不认这一项"。**从盘没有任何存储**：撤销授予（把物品换掉、摘掉法器）那一刻它自己就空了，法器技能也一样——它读的是栈本身，不是账本。

**法器技能（`ToggableArtifactAbility`，2026-09-22 新增，见 `research/32_法器开关与轮盘接线设计.md`）。** 判据只有一句：**凡是要按键才发动的都算技能，都进轮盘**。法器定义 `abilities` 里实现 `ToggableArtifactAbility` 的条目就是这样的东西——实现这个接口等于声明"把我放进轮盘"。它既包括**开关**（`mxt:flight`：开＝起剑、关＝落剑），也包括**一次性**（`mxt:storage`：按一下打开这件法器的储物箱，没有任何状态留下）。接口把四件事交给实现回答：

| 方法 | 谁问、问什么 |
| --- | --- |
| `String key()` | 这件法器里这个能力的名字，同一件法器内唯一（`flight` / `storage`）。轮盘的条目身份 = **法器 id + key**。 |
| `Component displayName()` | 这一格叫什么（`wheel.mxt.artifact_skill.flight` / `.storage`）。 |
| `Optional<Boolean> state(ctx)` | 有没有"开着/关着"这回事，现在是哪一边；**空 = 一次性**（储物就是空的）。两侧都问：客户端画状态，服务端据此决定做什么。 |
| `Result activate(ctx)` | 按下了。只有服务端调，返回「做了没有 + 为什么没做」（`Failure`：不在身上 / 不认你 / 状态已经是这样 / 现在用不了）。 |

因此：

- 它出现在**声明它的那张从盘**上（技能在前、法器技能在后，各自按 id 排序），也出现在主盘配置界面右侧那个池子里，可以钉到主盘任意一格。
- **条目身份 = `WheelEntryKind.ARTIFACT` + `命名空间:路径/key`**（`ArtifactCapability` 负责拼与解析），所以**一件法器可以有好几个技能，一个 key 一个**（同一把剑既能飞又能储物）；同一个 key 写两次由 `Artifact` 构造期拒绝。`WheelEntryKind.ARTIFACT.exists` 要求"这一对真的存在"，光是法器 id 不算。
- **状态归实现自己管**：飞行读 `FlightAttachment`（每玩家一份、记着是哪个 archetype，已同步给本人），储物没有状态。本轮不引入通用开关存储。
- 客户端画的是**报告**，不是指令：开着的开关绿、关着的灰、一次性的紫（`ArtifactWheelEntry`），tooltip 写法器名与开关状态；一次性没有状态那一行。**格子不画法器图标，画能力名**——一件法器的两个技能若都画同一把剑的图标就分不出谁是谁，哪件法器等 tooltip 说。
- **储物那一格打开的是原版箱子菜单**（`MxtMenus.ARTIFACT_STORAGE` 是 `ChestMenu`，客户端注册 `ContainerScreen`），窗口标题是法器名，内容是法器自己那份 `mxt:artifact_storage`。容器是 `ArtifactStorageContainer`：一个写入即回写的实时视图，**不持有栈**——每读每写都按"这件法器还在不在玩家身上"重新解析，法器一离身 `stillValid` 就是假，服务端每刻检查菜单并把窗口关掉，所以不会往一个没人拿着的栈里写东西。

**框架不决定轮盘上有什么。** `WheelMenuProvider` 回答"这个来源现在贡献哪些条目"（`entries(player, source)`，可以比一页长），唯一实现是 `WheelContent`，由 `MiXianTuClient#init` 里的 `WheelContent.register()` 登记。`WheelMenuEntry` 是条目契约：

```java
public interface WheelMenuEntry {
    WheelEntryKind kind();                       // ABILITY / AURA / ARTIFACT
    Identifier id();                             // 定义 id（ARTIFACT 用的是法器定义自己的 id）
    Component title();                           // 轮盘中间显示的名字
    Optional<IconReference> icon();              // 扇区里画的图标（可无）
    List<Component> tooltip(Player player);      // 类型 + 具体数值
    long cooldownTicks(Player player);           // 还剩几 tick，0 = 就绪
    default boolean usable(Player player);       // 默认 cooldownTicks <= 0
    void onSelected(WheelSelection selection);   // 使用回调（轮盘仍开着）
}
```

三个实现是 `AbilityWheelEntry`（技能：授予、消耗、冷却）、`AuraWheelEntry`（灵气：发射量、余量、元素）与 `ArtifactWheelEntry`（法器技能：能力名当标签、状态色、`usable` 按 `ArtifactService#mayUse` 判归属），它们不同的部分留在各自里面；轮盘、配置界面与网络只认 `kind + id`。

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

**触发只有一条通道**：`WheelActionC2SPayload(source, kind, id)` —— **来源 + 技能 / 灵气 / 法器技能 holder**，由客户端在按下的那一刻从**当时那一格**解析出来 → 服务端 `WheelService.trigger`：先要求**这个来源现在仍然认这一项**（`WheelSources#offers`：主盘读存档布局、从盘技能读授予账、从盘法器技能读那一页的装备栈），认下来才技能走 `AbilityService.use`（内部再校验授予、条件、消耗、冷却与施法时间）、灵气走 `SpiritBurstService.fireOnce`（校验元素、使用条件、冷却与余量后发一发 `SpiritBurstEntity`）、法器技能走 `ArtifactToggleService`（在那张从盘上重读一遍，调实现自己的 `activate(...)`）。**请求只说"按了这一格"，不说该往哪边走**：方向归服务端，所以客户端即使把状态猜错了也提不出一个不可能的状态，包也不用带方向（区别于带 `enabled` 字段的 `FlightToggleC2SPayload`）。**主盘也校验**：布局本来就是客户端交上来的，这道检查不是防作弊，而是让"这一项确实来自你说的那个来源"对所有来源都成立——配置界面刚清掉一格、玩家手里还按着 `V` 时，那次请求会被拒。**编号本身不参与触发**：它只回答"打哪一格"。

**两个键分工（`research/27` §6）。** `key.mxt.wheel`（默认 `R`）只负责**选**：按住打开轮盘、指针决定格子，松开（HOLD）或再按一次（TOGGLE）**只关闭、不触发**；**打开始终回到主盘（第一页）**。`key.mxt.wheel_use`（默认 `V`）负责**用**：轮盘开着时用掉指针当前那一格且**不关轮盘**，关着时用掉**记住的编号此刻代表的那一格**；鼠标左键等同于它。

**页是视图，编号才是选择**（`WheelSelectionState` 里是 `page` + `number` 两个值）。轮盘界面画当前页的 12 格；HUD 轮盘格**把每一页都画出来**（四列、向下长，见下），所以它不需要"当前页"；12 个槽位键作用于当前页；关着轮盘时的 `V` 作用于**编号此刻代表的那一格**（可能在任何一页上）。切换是 `key.mxt.wheel_previous` / `key.mxt.wheel_next`（**默认键盘左 / 右方向键**，`InputConstants.KEY_LEFT` / `KEY_RIGHT`，可在按键设置里改）+ **轮盘界面里的鼠标滚轮**（向上＝上一页、向下＝下一页，客户端配置「轮盘选择 → 滚轮翻页」默认开，关掉时 `mouseScrolled` 交回 `super`）：三条路径都走同一个 `WheelMenuController#stepPage`（`stepPage` + `refresh` + 那句动作栏提示），所以不会在"翻到哪页、报不报"上漂。**轮盘开着关着都能用**（滚轮那条只在界面开着时存在），翻到头是绕回第一页还是停在两端由客户端配置「轮盘选择 → 循环翻页」决定（**默认绕回**；`WheelSelectionState#stepPage(delta, wrap)` 只在这一处读它），切完在动作栏报一句「轮盘 2/3：主手物品」——从盘的页由随身物品决定，摘掉东西后那几页就没了，不报一声玩家不知道自己站在哪一页上。**切页不动编号**（切换不是瞄准），因此也不会因此上送什么：编号没变就没有包。有别的界面打开时不动作，与其余轮盘键同一口径。因此：

- 编号存在客户端 `WheelSelectionState`（`number`，`-1` = 还没选过）里：轮盘打开期间每帧只在**指针所在的格子真的有内容**时才写入编号（空格子不写，所以指针扫过空框不会把选择清掉），关轮盘后保留，`ClientPlayerNetworkEvent.LoggingIn` 清空，随后由服务端记住的 `armed` 填回来（跨会话那半见上一节）。同一份状态里还存着**这一客户端刻解析出来的全部页**（`pages()`，每页恒 12 格、`null` = 空格子），因为 HUD 那张网格每帧要读每一页的每一格，而解析要走一遍灵气注册表与"能不能发射"的公式，所以只在每刻刷新一次（`refresh`）；切页时控制器立刻再刷一次，本刻的画面与 HUD 才不会差一拍。条目在**用之前**才解析，所以授予被撤销 / 定义被删 / 灵气付不起时那一格当场变成"没有目标"，按 `V` 什么都不做。
- **这把键与切换键都读原始输入**（`WheelMenuController` 用 `InputConstants`/GLFW 轮询），不走 `KeyMapping`：屏幕一打开原版就 `KeyMapping.releaseAll()`（§1.4 的老坑），而且"按着 `V` 松开 `R`"会让 `grabMouse()` 里的 `KeyMapping.setAll()` 把 `V` 重新置为按下、补出一次假按下，等于多触发一次。一个键只在一处判边沿就没有这个问题。关着的时候还要求没有任何界面打开，否则在聊天栏打 `v` 就会放技能。
- **按了没生效时给一句动作栏提示**（2026-09-22 追加，见 `research/27` §9）：现在只有一种情况——`open(...)` 与 `use(...)` 的"整张轮盘一格内容都没有"都发 `actionbar.mxt.wheel.empty`。**`actionbar.mxt.wheel.no_selection` 已随"永远有一个选中"删除**（"从没选过任何格子"不再是可能状态）。**刻意不给提示的情况**：指针所在的格子当前解析不出条目（空格子、定义被删、授予被撤销）——那在轮盘格上本来就画成空框，按 `V` 也不关轮盘，所以 `usePointed(...)` 的 `selection == null` 分支保持完全静默（第一版曾给它加过 `actionbar.mxt.wheel.no_entry`，用户看过之后点名去掉）。走**客户端** `Minecraft#gui#setOverlayMessage`，不发包——服务端从头到尾不知道有这次按键。**有别的界面打开时也保持沉默**（裸轮询照样读得到物理按键，但那时是在聊天栏打字/翻背包，不是请求）。
- `release_to_select` 配置**已删除**（松开不再选中）；`mode`（按住 / 切换）保留。`WheelSelection.Method` 由 `RELEASE`/`CLICK` 改为 `KEY`/`CLICK`，只表示"是键盘还是鼠标要求的"。
- **12 个槽位各有一把键，默认全部未绑定、单独一个分类**（`research/27` §10）：`MxtKeyMappings.WHEEL_SLOTS` 是 `List<KeyMappingHolder>`，由 static 块里的一个 `for` 按 `WheelGeometry.SECTORS` 填出（键名 `key.mxt.wheel_slot.1` … `.12`，分类 `mxt:wheel_slot` =「**觅仙途：轮盘槽位**」/ "MiXianTu: Wheel Slots"——分类名带模组名，因为原版把分类平铺在按键设置里）。**分类内的顺序由 `order` 决定**：原版按键列表是 `Arrays.sort` → `KeyMapping#compareTo`，同一分类里**先比 `order`、`order` 相同才比"翻译后的显示名"**（`I18n.get(name)`），所以第一版不补零会排成 `1、10、11、12、2、3…`（用户实测截图发现）。做法是给每把键传 `sector` 当 `order`（`KeyMappingHolder` 为此新增一个转发到原版五参 `KeyMapping(...)` 的构造器），于是显示名可以是老实的「槽位 1」…「槽位 12」，顺序在任何语言下都固定。**下标 `i` = 页内格子 `i`，而键名里的数字是 `i + 1`**——配置界面给 12 格的编号是 `1` 在正上方、顺时针，所以"槽位 1"就是当前页的第 0 格。按下槽位键 N = **在当前页上选中第 N 格并立刻用掉**（等于"把指针指过去再按 `V`"）：`useSlotKey(...)` 先算出编号（`page * 12 + sector`）、取出那一格、`WheelSelectionState.selectSector(sector)`（HUD 金框立刻跟过去，编号随之跨会话持久化），再 `entry.onSelected(...)`；空格子按下什么都不做也不提示（与 `V` 同一口径）。**`R`/`V`/左右切换与这 12 把键都由 `WheelMenuController` 裸轮询，不走 `KeyMappingHolder` 回调**：`setAll()` 会伪造一次按下，对施法就是白放一个技能（§6.3）；边沿照常采样、只在"没有别的界面打开，或轮盘正开着"时动作，于是在聊天栏里打字不会施法、按着槽位键开关一次界面也不会补放。
- 轮盘中间那一行在可用时写「按 `V` 使用」（键名取实际绑定），不可用时写「冷却中 4.3s」——剩余时间按 tick 读（附件里存的是冷却结束的那一 tick），由 `WheelDuration.seconds` 统一写成**永远一位小数**的秒数，与 tooltip 里的「冷却 / 施法」同一种写法。**环的上方另有一行页号**「轮盘 2/3：主手物品（两把切换键的实际绑定）」，键名同样取实际绑定；环本身画哪一页都长一个样，没有这一行就分不出自己站在哪。
- HUD 上有一个可拖动元素「**轮盘格**」（`screen/wheel/WheelSelectionEntry`，布局键 `wheel.selection`）：**永远是 4 列，行数随内容的格子数向下长**——它是**整张轮盘的一览**，不是当前页。**只有主盘画空格子**：那 12 个空框是玩家自己摆的布局，空着就是要看得见；从盘的页只画它真正贡献的那几格，所以 3 个技能的页就是 3 格，不是 3 格 + 9 个空框（因此页边界不再一定等于行边界）。**块的上方不写任何字**：它是拿来看的，哪一页由轮盘自己说。每格画图标或名字开头、底边一条类型色，不可用时压一层暗色；**编号此刻代表的那一格换成金色边框的贴图**（`slot_22_selected.png`，与配置界面选中的候选格、轮盘上指针所在格子的金色是同一套语汇）。它整块自绘（`renderBlocks()` 返回空、走 `render()`），默认位置是**窗口左边、竖直居中**；尺寸每帧按内容算（`layoutWidth` / `layoutHeight` 是动态的，`refreshPlacement` 里 `setSize` 回报给框架，长出去会被夹回窗口）。它在 `MiXianTuClient#init` 与资源条一起登记，关着也能按 `V` 这件事靠它才不盲目。

**按了没反应时会被告诉原因。** 轮盘这条路上有两处会拒绝请求，而客户端从画面上分不出来，所以两处都会说话：服务端重读来源后发现**这一项已经不在那个来源上**时，日志记一条 info、动作栏报「轮盘上的这一项已经失效了」；技能管线拒绝这次施放时（灵根不符、资源不足、冷却、条件不满足……），`AbilityService` 返回的 `UseResult` 不再被丢掉——日志记一条 info（含 `Failure` 名与缺的那个资源），动作栏报「施放失败：<原因>」（`actionbar.mxt.ability.failure.*` 一份原因一份文案，缺资源的还会点名是哪一门）。灵气发射失败同理报「灵气没能发射出去」，法器技能按不动时报「使用失败：<原因>」（`actionbar.mxt.artifact_skill.*`：不在身上 / 不认你 / 状态已经是这样 / 现在用不了）。这一条是照 `CultivationModeService#notifyFailure` 的口径做的：**拒绝要说出来，不能只有"按了没反应"**。

**加一种新条目**：实现 `WheelMenuEntry`，再让 provider 把它放进某个来源即可——轮盘的几何、分页、渲染、开合与选择语义都不用动，工具提示自己拼（`WheelTooltips` 里有共用的数值、消耗与元素写法）。**加一类新东西**（既不是技能、灵气也不是法器技能）才需要动 `WheelEntryKind`、`WheelService.trigger` 的 switch 与配置界面的分池。**加一个来源**是加一个 `WheelSource` 常量、一行 `grantSources(...)` 映射（以及一条显示名语言键）：来源 id 走 `AbilitySources`，读取、分页与校验都不用改。**加法器技能**是实现 `ToggableArtifactAbility`（一个新的 `mxt:artifact_ability_type` 条目）并给它一个 `key`——轮盘、配置池、触发与文案都会自己接纳它，唯一要记住的是"条目身份 = 法器 id + key"。

**格子里画什么。** 每个格子（环上的扇区与 HUD 轮盘格）先画条目的 `icon()`；**没有图标时改画名字**——`IconRenderer.renderName` 取 `title()` 里放得下的开头几个字，画在图标的位置上，所以一圈填满没有图标的条目也不会出现空格子。环上文字宽度按该半径上一扇的弧长减去留白算（`WheelMenuScreen#labelWidth`），因此相邻扇区的文字不会互相压；完整名字始终在轮盘中间与 tooltip 里。配置界面那一排 12 格同理（`IconRenderer.renderOrName`：有图标画图标，没有就画名字开头），22px 的格子只放得下两个汉字，全名看 tooltip。

编辑界面是 `WheelConfigurationScreen`：左边 6 列灵气池、右边 6 列"技能与法器技能"池（各自滚动、各自 tooltip，右池来自 `WheelContent#pool`：玩家持有的主动技能 + 双手与 Curios 上法器声明的技能），下面一排 12 格是**主盘**的 12 格（共用，任意放），`Esc` 保存并关闭；标题栏第二行写明「从盘（主手 / 副手 / 法器）自动生成，用 <键> / <键> 切换」，格子标题也从「轮盘 12 扇」改成「主盘 12 扇」。**只有主盘可编辑**：从盘的内容由随身物品决定，站在界面里预览的与战斗里看到的不是同一份，所以干脆不预览。两个入口都是**纯客户端**的（界面读的是同步过来的附件与注册表，服务端无事可做）：客户端命令 `/wheel`，以及按键 `key.mxt.wheel_configuration`（**默认未绑定**）。主盘上钉了一个法器技能、而那件法器已经不在身上时，那一格与"技能被撤销"同一表现：配置界面画 `?`、轮盘上什么都不画，按下去由服务端拒绝并提示「这件法器不在身上」对应的那类原因。
