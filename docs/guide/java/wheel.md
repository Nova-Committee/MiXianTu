---
title: 客户端轮盘
---

12 扇轮盘是技能与灵气的**唯一触发入口**：框架在 `screen/wheel`，内容在 `screen/wheel/content`。它替换掉了原来"技能与灵气各有一条客户端 Hotbar、各有一个配置界面"的两套东西（过程见 `research/28_技能与灵气归一化设计.md`）。

**框架不决定轮盘上有什么。** `WheelMenuProvider` 回答"这个玩家的 12 扇现在各是什么"，唯一实现是 `WheelContent`，由 `MiXianTuClient#init` 里的 `WheelContent.register()` 登记。`WheelMenuEntry` 是条目契约：

```java
public interface WheelMenuEntry {
    WheelEntryKind kind();                       // ABILITY / AURA
    Identifier id();                             // 定义 id
    Component title();                           // 轮盘中间显示的名字
    Optional<IconReference> icon();              // 扇区里画的图标（可无）
    List<Component> tooltip(Player player);      // 类型 + 具体数值
    long cooldownTicks(Player player);           // 还剩几 tick，0 = 就绪
    default boolean usable(Player player);       // 默认 cooldownTicks <= 0
    void onSelected(WheelSelection selection);   // 使用回调（轮盘仍开着）
}
```

两个实现是 `AbilityWheelEntry`（技能：授予、消耗、冷却）与 `AuraWheelEntry`（灵气：发射量、余量、元素），两者不同的部分留在各自里面；轮盘、配置界面与网络只认 `kind + id`。

**12 格内容存在服务端。** 玩家附件 `WheelLayoutAttachment`（`wheel_layout`）存一份 12 格 `WheelLayout`，每格是 `WheelSlot`（`WheelEntryKind` + id，空扇区用 `EMPTY` 哨兵），另有一个 `selection` 字段（`Optional<WheelSlot>`）存**当前激活的那一条**。配置界面关闭时发 `WheelLayoutC2SPayload` 上送，服务端 `WheelService.sanitize` 把大小强制成 12、逐格校验 id 能否在对应注册表里解析，再写回附件；附件同步给本人，因此轮盘与配置界面读到的就是服务端会执行的那一份。没保存过（附件里没有）时客户端读到的就是 **12 个空扇区**——**不做任何默认填充**（2026-09-22 按用户要求删掉原来的"前 6 个灵气 + 前 6 个技能"派生填充，理由见 `research/28` §4.2：放什么是玩家的决定，而"按当前可用池现算"的那份填充还会在第一次保存前自己换位置）。代价是空轮盘按 `R` 不开（`WheelMenuContent#isEmpty`），新档第一次要先用 `/wheel` 放条目。

**选中的是条目，不是扇区号。** 扇区号离开它当时的那份布局就没有意义，所以 `selection` 存 `kind + id`，由 `screen/wheel/content/WheelSelectionSync` 负责两个方向：登录后从同步过来的附件里把记住的那一条找回来（`WheelEntryKind#exists` 再解析一次 id，解析不到——定义被删，或它已不在你的 12 格里——就记一条 warning、按"没选中"处理并把空值写回服务端，所以警告只出现一次）；轮盘关着时指针换了扇区才上送 `WheelSelectionC2SPayload`（`Optional<WheelSlot>`，空扇区发空），配置界面保存布局时强制重发一次（存下来的含义是"那个扇区装的东西"，布局一换就可能过期）。附件在玩家加入之后才发过来，所以"还没收到"与"这个玩家从没选过"在头几刻看起来一样：恢复在那之前每刻查一次附件，一旦本次会话自己选了东西就再也不查，不会把玩家刚选的扇区冲掉。服务端那条路照旧不信任客户端（`WheelService.sanitizeSelection` 与布局校验共用 `WheelEntryKind#exists`），失败只记 warning 不报错。

**触发只有一条通道**：`WheelActionC2SPayload(kind, id)` → 服务端 `WheelService.trigger`：技能走 `AbilityService.use`（内部再校验授予、条件、消耗、冷却与施法时间），灵气走 `SpiritBurstService.fireOnce`（校验元素、使用条件、冷却与余量后发一发 `SpiritBurstEntity`）。

**两个键分工（`research/27` §6）。** `key.mxt.wheel`（默认 `R`）只负责**选**：按住打开轮盘、指针决定扇区，松开（HOLD）或再按一次（TOGGLE）**只关闭、不触发**。`key.mxt.wheel_use`（默认 `V`）负责**用**：轮盘开着时用掉指针当前那一扇且**不关轮盘**，关着时用掉**记住的那一扇**；鼠标左键等同于它。因此：

- 选中项存在客户端 `WheelSelectionState`（一个 `int`，`-1` = 没选过）里：轮盘打开期间每帧写入指针所在扇区（空扇区也写，因为"指着空的就是没选"比偷偷留着上一扇更诚实），关轮盘后保留，`ClientPlayerNetworkEvent.LoggingIn` 清空，随后由服务端记住的 `selection` 填回来（跨会话那半见上一节）。同一份状态里还存着**这一客户端刻解析出来的 12 扇整表**（`sectors()`，恒 12 长、`null` = 空扇区），因为 HUD 那张网格每帧要读全部 12 格，而解析要走一遍灵气注册表与"能不能发射"的公式，所以只在每刻刷新一次（`refresh`）；条目在**用之前**才解析，所以授予被撤销 / 定义被删 / 灵气付不起时那一扇当场变成"没有目标"，按 `V` 什么都不做。
- **两个键都读原始输入**（`WheelMenuController` 用 `InputConstants`/GLFW 轮询），不走 `KeyMapping`：屏幕一打开原版就 `KeyMapping.releaseAll()`（§1.4 的老坑），而且"按着 `V` 松开 `R`"会让 `grabMouse()` 里的 `KeyMapping.setAll()` 把 `V` 重新置为按下、补出一次假按下，等于多触发一次。一个键只在一处判边沿就没有这个问题。关着的时候还要求没有任何界面打开，否则在聊天栏打 `v` 就会放技能。
- **按了没生效时给一句动作栏提示**（2026-09-22 追加，见 `research/27` §9）：只有两种情况——`open(...)` 的空轮盘分支发 `actionbar.mxt.wheel.empty`，`use(...)` 里"从没选过扇区"那一支发 `actionbar.mxt.wheel.no_selection`（带实际绑定的轮盘键名）。**刻意不给提示的第三种情况**：选中的/指针所在的扇区当前解析不出条目（空格子、定义被删、授予被撤销）——那在轮盘格上本来就画成空框，按 `V` 也不关轮盘，所以 `usePointed(...)` 的 `selection == null` 分支保持完全静默（第一版曾给它加过 `actionbar.mxt.wheel.no_entry`，用户看过之后点名去掉）。走**客户端** `Minecraft#gui#setOverlayMessage`，不发包——服务端从头到尾不知道有这次按键。**有别的界面打开时也保持沉默**（裸轮询照样读得到物理按键，但那时是在聊天栏打字/翻背包，不是请求）。
- `release_to_select` 配置**已删除**（松开不再选中）；`mode`（按住 / 切换）保留。`WheelSelection.Method` 由 `RELEASE`/`CLICK` 改为 `KEY`/`CLICK`，只表示"是键盘还是鼠标要求的"。
- **12 个槽位各有一把键，默认全部未绑定、单独一个分类**（`research/27` §10）：`MxtKeyMappings.WHEEL_SLOTS` 是 `List<KeyMappingHolder>`，由 static 块里的一个 `for` 按 `WheelGeometry.SECTORS` 填出（键名 `key.mxt.wheel_slot.1` … `.12`，分类 `mxt:wheel_slot` =「**觅仙途：轮盘槽位**」/ "MiXianTu: Wheel Slots"——分类名带模组名，因为原版把分类平铺在按键设置里）。**分类内的顺序由 `order` 决定**：原版按键列表是 `Arrays.sort` → `KeyMapping#compareTo`，同一分类里**先比 `order`、`order` 相同才比"翻译后的显示名"**（`I18n.get(name)`），所以第一版不补零会排成 `1、10、11、12、2、3…`（用户实测截图发现）。做法是给每把键传 `sector` 当 `order`（`KeyMappingHolder` 为此新增一个转发到原版五参 `KeyMapping(...)` 的构造器），于是显示名可以是老实的「槽位 1」…「槽位 12」，顺序在任何语言下都固定。**下标 `i` = 扇区 `i`，而键名里的数字是 `i + 1`**——配置界面给 12 格的编号是 `1` 在正上方、顺时针，所以"槽位 1"就是扇区 0。按下槽位键 N = **选中第 N 扇并立刻用掉**（等于"把指针指过去再按 `V`"）：`useSlotKey(...)` 先 `WheelSelectionState.select(sector)`（HUD 金框立刻跟过去，选中项随之按 §7 跨会话持久化），再 `entry.onSelected(...)`；空扇区按下什么都不做也不提示（与 `V` 同一口径）。**`R`/`V` 与这 12 把键都由 `WheelMenuController` 裸轮询，不走 `KeyMappingHolder` 回调**：`setAll()` 会伪造一次按下，对施法就是白放一个技能（§6.3）；边沿照常采样、只在"没有别的界面打开，或轮盘正开着"时动作，于是在聊天栏里打字不会施法、按着槽位键开关一次界面也不会补放。
- 轮盘中间那一行在可用时写「按 `V` 使用」（键名取实际绑定），不可用时写「冷却中 4.3s」——剩余时间按 tick 读（附件里存的是冷却结束的那一 tick），由 `WheelDuration.seconds` 统一写成**永远一位小数**的秒数，与 tooltip 里的「冷却 / 施法」同一种写法。
- HUD 上有一个可拖动元素「**轮盘格**」（`screen/wheel/WheelSelectionEntry`，布局键 `wheel.selection`）：**3 行 4 列的 12 格**，格子号 = 扇区号按读序（左上角是第 1 扇），每格画图标或名字开头、底边一条类型色，不可用时压一层暗色；**选中的那一格换成金色边框的贴图**（`slot_22_selected.png`，与配置界面选中的候选格、轮盘上指针所在扇区的金色是同一套语汇），空格子只有框。它整块自绘（`renderBlocks()` 返回空、走 `render()`），默认位置是**窗口左边、竖直居中**；12 格的内容来自 `WheelSelectionState.sectors()`，每客户端刻解析一次。它在 `MiXianTuClient#init` 与资源条一起登记，关着也能按 `V` 这件事靠它才不盲目。

**加一种新条目**：实现 `WheelMenuEntry`，再让 provider 把它放进某一扇即可——轮盘的几何、渲染、开合与选择语义都不用动，工具提示自己拼（`WheelTooltips` 里有共用的数值、消耗与元素写法）。**加一类新东西**（既不是技能也不是灵气）才需要动 `WheelEntryKind`、`WheelService.trigger` 的 switch 与配置界面的分池。

**扇区里画什么。** 每个扇区在环上先画条目的 `icon()`；**没有图标时改画名字**——`IconRenderer.renderName` 取 `title()` 里放得下的开头几个字，画在图标的位置上，所以一圈填满没有图标的条目也不会出现空扇区。文字宽度按该半径上一扇的弧长减去留白算（`WheelMenuScreen#labelWidth`），因此相邻扇区的文字不会互相压；完整名字始终在轮盘中间与 tooltip 里。配置界面那一排 12 格同理（`IconRenderer.renderOrName`：有图标画图标，没有就画名字开头），22px 的格子只放得下两个汉字，全名看 tooltip。

编辑界面是 `WheelConfigurationScreen`：左边 6 列灵气池、右边 6 列技能池（各自滚动、各自 tooltip），下面一排 12 格是轮盘的 12 个扇区（共用，任意放），`Esc` 保存并关闭。两个入口都是**纯客户端**的（界面读的是同步过来的附件与注册表，服务端无事可做）：客户端命令 `/wheel`，以及按键 `key.mxt.wheel_configuration`（**默认未绑定**）。
