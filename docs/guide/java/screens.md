---
title: 客户端界面
---

客户端界面统一放在 `com.iafenvoy.mxt.screen` 下：容器**菜单**在 `screen.menu`、它们的**界面**在 `screen.gui`，纯客户端信息界面在 `screen.information`，物品选择器在 `screen.picker`，方向性选择（12 扇轮盘）在 `screen.wheel`，HUD 元素在 `screen.hud`（框架）、`screen.resourcebar`（资源条）与 `screen.wheel`（轮盘格）。新增界面优先复用现成的 `Screen` 基类和原版组件，不要自己造滚动和文本输入。

## 可拖动 HUD 框架 `screen.hud`

模块自己画的 HUD 元素（快捷栏、资源条……）要让玩家能拖动并存档，就接上这套框架。框架只做四件事：**登记元素**、**每帧画它们**、**给编辑器提供命中与占位框**、**把位置写进客户端配置**。

**元素不自己算屏幕坐标，也不自己决定画在哪。** 子区域（比如资源条两列）只负责"提供对象"：把这一帧要显示的东西描述成一组 `RenderBlock`（各自的宽高 + 一个画法），由框架唯一的渲染器 `HudRenderer` 竖着堆进元素矩形——**垂直位置只有这一处算法**，块按自己的高度顺序叠，容器尺寸与内容排布因此永远是同一个数字；要留空隙就用 `RenderBlock.spacer`，不要把它加进某一条的高度。整块自己用图形 API 画的元素（比如以后可能接的快捷栏）走另一条口子：`renderBlocks()` 返回空列表，框架改成调 `render()` 让它自己画。

```java
public final class MyBar extends AbstractHudEntry {
    public MyBar() {
        // 布局键：同时是配置里的存储键与重复登记的检查依据，一个元素一个，跨版本别改
        super("my_bar", WIDTH, HEIGHT);
    }

    @Override public String displayName() { return Component.translatable("hud.mxt.my_bar").getString(); }
    @Override public int layoutWidth() { return WIDTH; }
    @Override public int layoutHeight() { return HEIGHT; }
    @Override public int defaultX() { return 4; }
    @Override public int defaultY() { return 4; }

    // 只描述：一块底、一行数值；位置由框架决定
    @Override
    public List<RenderBlock> renderBlocks() {
        return List.of(
                RenderBlock.fill(WIDTH, 6, 0xFF10131D),
                RenderBlock.label(Component.literal("42 / 100"), 0xFFFFFFFF));
    }
}

// 在客户端初始化（FMLClientSetupEvent）里登记一次；返回的就是这个实例，可以直接留引用
MyBar bar = HudManager.register(new MyBar());
```

要点：

- **位置是「窗口比例」存的，不是像素**，坐标原点在窗口**左上角**（框架内部、命中与绘制都按左上角说话）。`config/mxt/mxt-hud.json` 里 `hud.layout_v2` 的每一项是 `x,y,visible`，`x`/`y` 是 `0..1` 的比例，指向元素矩形的左上角，所以换分辨率、换 GUI 缩放后布局不会漂。像素值只活在内存里，并且每次读取都夹进窗口，元素不可能被拖到看不见的地方。键上的 `_v2` 是位置含义变过一次留下的：旧键不再被读，那批布局退回默认位置。
- **HUD 布局是独立的一份配置**（`MxtHudConfig`，写 `config/mxt/mxt-hud.json`），不在客户端设置里——它是拖出来的一行一个元素，而不是挨个填的设置项。其余配置也统一收进 `config/mxt/`：`mxt-client.json`、`mxt-server.json`。模组列表里那个「配置」按钮打开的选择界面（`ConfigSelectScreen`）**只有客户端与服务端两个槽位**——jupiter 的 builder 每个槽位只存一个容器，`client(...)` 写两次是后者覆盖前者（2026-09-22 真踩过：`MxtHudConfig` 把 `MxtClientConfig` 顶掉，点「客户端配置」进的是 HUD 布局页），所以 HUD 布局**不进那个界面**，改它用布局编辑器与 `/hud`。
- **锚点 `HudAnchor`** 决定 `defaultX()`/`defaultY()` 指的是矩形上哪个点，以及**尺寸变化时哪个点不动**。默认是左上角；会往上长的东西（一列资源条）用 `CENTER_BOTTOM`，这样它长高时下边缘钉在原地。
- **改动即存档**：拖动、方向键微调、显隐切换都会立刻写配置。没有「关界面时统一保存」，所以崩了也不会丢布局；反过来，手动改 JSON 之后要重开客户端才生效（元素只在构造时读一次）。
- **位置只有一个来源**：存档里有这个布局键就用键里的比例（并且只在窗口尺寸变化时重算），没有就用元素自己给的默认位置（每帧重算，所以跟着窗口走）。`resetToDefault()` 会把键从存档里删掉——复位必须跨重启有效，否则看起来就像「布局没保存」。（这里曾经多存了一个布尔标志表示「当前用的是存档位置」，它初值是 `false`、只有拖动才置真，于是**文件里的位置永远轮不到被读**：每次重启都退回默认。删掉那个标志、只留「键在不在」这一个事实，问题就没了。）
- **只有 `visible() && moveable()` 的元素会被 `moveableEntries()` 收进来**，也就是只画它、只让它被拖。自己算位置、不该被拖的元素（比如居中的快捷栏）重写 `moveable() { return false; }` 即可，它依然会被框架画；这类元素如果连尺寸都由世界状态决定，还要重写 `refreshPlacement()` 并调用基类的 `placeAtDefault()` 把默认位置真正应用上去——**只算尺寸不落位置的话，锚点会一直停在 `(0,0)`**，元素画在窗口左上角（本模组的 `resource_bars.target` / `resource_bars.boss` 就踩过这个）。
- **尺寸由元素自己说了算**：宽度随内容变化的元素在变化时调用 `setSize(w, h)`，框架据此重算夹取范围。它不会替你去量。**空元素也要给一个非零尺寸**，否则玩家在编辑器里看不到它、也就没法摆。
- **元素要在客户端初始化时登记**，不要等第一帧渲染：框架的 GUI 层只在世界里绘制，否则玩家在主菜单打开编辑器时框架还是空的，界面看起来就像坏了。
- **编辑界面 `HudEditScreen`** 由 `HudManager.openEditor()` 打开（按键 `key.mxt.hud_layout`，默认右 Shift，或客户端命令 `/hud open`）。里面左键拖动、方向键 1 像素（按住修饰键 10 像素）微调、`Esc` 放弃当前选中、点空白处取消选中。**还没有缩放**：这一版只做移动，KronHUD 那种拖角缩放与吸附参考线都没移植。
- 编辑器里元素**照常真实绘制**（GUI 层顺序在最后），编辑器只额外画半透明矩形和名字标签，所以看到的就是实际效果。
- 诊断用客户端命令 `/hud`：打出每个元素的布局键、位置、尺寸、当前块数与可见/可拖状态。「一个元素都没登记」和「登记了但这一帧没有内容」在界面上长得一样，只有它能分开。

不接内容时框架是空转的：没有登记任何元素时，编辑界面只有一句「当前没有可移动的 HUD 元素」。范围、未移植项与后续接法见 [`research/26_可拖动HUD框架设计.md`](../../../research/26_可拖动HUD框架设计.md)。

**已接入的元素**：资源条一共四个元素，全部登记进框架、由 `HudRenderer` 画——两列可拖的（`resource_bars.left` / `resource_bars.right`，键 `Anchor.LEFT` / `Anchor.RIGHT`）加两条不可拖的固定行（`resource_bars.target` / `resource_bars.boss`，`ResourceBarFixedEntry`，`moveable() == false`、位置每帧现算、永不入档）。资源条自己的那个 `mxt:resource_bars` GUI 层**已经删除**，`ResourceBarOverlay` 只剩纯工具方法。这四条可以当范例：`ResourceBarOverlay.column(anchor)`／`row(target, layout)` 只回答"哪些条、什么顺序"，条目用 `ResourceBarEntry.blocksWithGaps(...)` 把每条包成块并在条之间插 `spacer`，尺寸交给基类夹取。**位置在数据包那边没有字段**——`anchor` 只决定落进哪一列，列摆在哪是玩家自己的设置在客户端配置里。

第五个元素是轮盘的「轮盘格」（`screen/wheel/WheelSelectionEntry`，键 `wheel.selection`）：它**不走 `RenderBlock`**，`renderBlocks()` 返回空、自己用 `render()` 画**永远 4 列、行数随页数向下长的格子**（每格 22px，一页 12 格 = 三行；块宽固定 94、高按内容算，格子号 = 编号读序，**编号此刻代表的那一格换成金色边框**），**冷却中的格子则按原版物品那一套压一层白幕**——盖住图标的剩余比例、随时间从上往下退（剩余 ticks 读条目的 `cooldownTicks`，全长读新增的 `cooldownLength`：技能用上一次实际拿到的 `mxt:cooldown` 长度，灵气用固定发射间隔；答不出全长的条目画满整块，只说"在冷却"），其它原因不可用时才压那层暗色，所以它也是"整块自己画"那条口子的第一个范例，还是**尺寸随内容变**的第一个范例（`layoutWidth` / `layoutHeight` 每帧算，`refreshPlacement` 里 `setSize` 回报，长出去会被夹回窗口）；默认位置是**窗口左边、竖直居中**（`defaultX() = 4`、`defaultY() = (窗口高 - 块高) / 2`），块的尺寸同时是命中矩形和占位框。格子内容读 `WheelSelectionState.pages()`——每客户端刻解析一次的整张轮盘快照，所以它画的是**每一页**，而**块的上方不写任何字**（哪一页由轮盘自己说）。它由 `MiXianTuClient#init` 与资源条一起登记，理由同 §"登记时机"——不然从主菜单打开编辑器就看不到它。它对应的玩法（`R` 选、`V` 用、左右方向键切页）见 [`wheel.md`](./wheel.md)。

## 轮盘选择系统 `screen.wheel`

按键（`key.mxt.wheel`，**默认 R**）按住，屏幕上出现一个 **12 扇**的轮盘：指针**朝哪个方向**就选中哪一扇，选中的扇区变金色并向外扩一点，**这一扇的名字写在轮盘正中间**。它现在是技能（法器技能已并入其中）与灵气**唯一的触发入口**：原来"技能与灵气各有一条快捷栏、各有一个配置界面"的两套东西已经删除，`LAlt` 那个按键也一并取消（`V` 虽然重新被占用，但含义换成了"用掉轮盘当前选中的那一扇"，见下）。

**轮盘由"主盘 + 从盘"组成，用一套连续编号串起来**（2026-09-22 新增并按玩家口径重做，见 `research/31_多轮盘与轮盘来源设计.md` §10）：主盘是玩家自己摆的 12 格（编号 `0..11`），从盘（主手物品 / 副手物品 / 法器 / 契约灵兽）按随身装备与手里的御兽铃自动生成、**不存储**（内容是这些装备此刻授予的主动技能，加上它们作为法器声明的**技能**：飞行开关与储物，见 `research/32_法器开关与轮盘接线设计.md`；契约灵兽那一页是**手里御兽铃对准的灵宠认的行为**，2026-09-25 新增），格子从 `12` 起接着排；**一页 12 格**，一个来源占 `ceil(条目数 / 12)` 页（一条都没有就一页都不占），内容多了就**再开一页**而不是丢掉。切换是两把键（`key.mxt.wheel_previous` / `key.mxt.wheel_next`，默认键盘左右方向键，两头环绕），`R` **打开始终回到主盘（第一页）**。**页只是视图，编号才是选择**：轮盘画当前页、HUD 轮盘格画整张轮盘、槽位键作用于当前页，关着轮盘时的 `V` 用编号此刻代表的那一格（**编号越界时自动落到最后一个有东西的格子，且编号本身不改写**，所以物品拿回来就恢复原选择）。

**选择与使用分成两个键**：`R` 只负责选（松开或再按一次**只关闭、不触发**），`key.mxt.wheel_use`（默认 `V`）负责用——轮盘开着时用掉指针那一格且**不关轮盘**，关着时用掉**编号此刻代表的那一格**（客户端 `WheelSelectionState` 里是 `page` + `number` 两个值，`LoggingIn` 清空后由服务端记住的 `armed`（一个数字）填回来，见 [`wheel.md`](./wheel.md)），鼠标左键等同于它。这把键与两把切页键都由 `WheelMenuController` **裸轮询**（`InputConstants`/GLFW），因为任何 `Screen` 一打开原版就 `KeyMapping.releaseAll()`，而且"按着 `V` 松开 `R`"会让 `grabMouse()` 里的 `KeyMapping.setAll()` 补出一次假按下、多触发一次。可拖动的 HUD 元素「轮盘格」（`wheel.selection`）画的正是整张轮盘的格子：**金色边框那一格就是"现在按 `V` 会放什么"**（轮盘开着时它跟着指针走）。

框架（几何、扇环渲染、开合状态机、选择语义）在 `screen.wheel`，内容（技能与灵气怎样变成条目、每个来源贡献什么）在 `screen.wheel/content`，编辑界面是 `WheelConfigurationScreen`；接法与数据流见 [客户端轮盘](wheel)。三条界面语义值得记住：`isPauseScreen()` 返回 `false`（单人游戏里不暂停）；`extractBackground(...)` **留空**（默认背景会把这之前提取的整层 HUD 糊掉，`HudEditScreen` 当初也是为同一个模糊问题覆写 `isInGameUi()`）；**按住轮盘时角色会停下**（原版对任何 `Screen` 都 `KeyMapping.releaseAll()`，松开时 `grabMouse()` 会把物理按键状态同步回来，不用重新按）。

框架里两条不肯让步的约定：**判扇区只看方向、不看距离**（指针还在内圈里也算数，所以中间那块空地能一直显示"当前指着的扇区叫什么"），以及**指针方向 → 扇区号的换算只有 `WheelGeometry` 一处**（`sectorStart` / `sectorCentre` / `sectorAt` 由同一组常量推出，`sectorAt(sectorCentre(k)) == k` 恒成立；MineMenu 把这段抄了三处、其中一处约定还和另外两处不同）。

## 法器储物窗口 `MxtMenus.ARTIFACT_STORAGE`

轮盘上「储物」那一格按下去打开的就是它（2026-09-22 新增，见 `research/32_法器开关与轮盘接线设计.md`）：**没有自己的菜单类，也没有自己的界面类**——它就是原版箱子那一套，注册的是 `ChestMenu`，客户端注册的是 `ContainerScreen`（`generic_54.png` 那张贴图本来就支持 1..6 行），所以 176 宽、行数由 `getRowCount()` 决定，标题由开窗包带过去（`screen.mxt.artifact_storage` = 「储物 · 法器名」）。行数走 `IMenuTypeExtension`，由服务端在开窗时写进附加数据：`capacity / 9`，客户端据此重建一个同样大小的 `SimpleContainer` 镜像，格子内容照常走菜单同步。

内容那一侧是 `runtime/artifact/ArtifactStorageContainer`（扩展 `SimpleContainer`）：开窗时从法器的 `mxt:artifact_storage` 组件读进来，之后**每一次改动都在 `setChanged()` 里整份写回**（`ArtifactStorageService#replace`，一次组件更新而不是每格一次）。它**故意不持有那个物品堆**：法器的位置是会变的，往一个没人拿着的栈里写就是物品消失的经典成因——所以每次读写都按"这件法器还在不在玩家身上"重新解析（`ArtifactService#carried` 扫双手、背包与 Curios），`stillValid` 一旦为假，服务端每刻的菜单检查就会把窗口关掉。储物格数由定义决定：**按 9 向上取整、最多 6 行（54 格）**，容量与窗口永远是同一个数。


## 物品选择界面 `ItemPickerScreen`

原版创造模式搜索 tab 的复刻，**只有内容是本 mod 的**：界面继承 `AbstractContainerScreen`，菜单 `PickerMenu` 就是 `CreativeModeInventoryScreen.ItemPickerMenu` 的形状——5×9 的槽位网格，内容是结果列表的一页窗口，外加底部一排 9 格的**玩家真实快捷栏**。物品、数量、模型种子、悬浮高亮（`container/slot_highlight_back/front`）和提示框全部由原版基类从槽位里读出来，界面自己一处都没画。

底图直接贴 `minecraft:textures/gui/container/creative_inventory/tab_item_search.png`（195×136）。这张贴图里**网格槽框和快捷栏槽框都已经画好了**：快捷栏那排在 `y=111..128`、`x=9..170`，正好对应 `addInventoryHotbarSlots(inventory, 9, 112)`，所以只要把槽加进去，原版渲染就会把物品画进现成的框里。搜索框是 `setBordered(false)` 的 `EditBox`，放在贴图自带的凹槽 `(82, 6)` 上（凹槽本身就是框和底）；滚动条用 tab 自己的 `container/creative_inventory/scroller`（九宫格 6×32，按 `12×15` 拉伸）贴在与原版相同的 `leftPos + 175`、`topPos + 18`，滑动行程 `112 - 15 - 2`。**面板不走代码绘制**，所以在任何重绘该贴图的资源包下都跟着变。

两个入口，区别只在内容从哪来：

```java
// 任意物品列表：顺序即显示顺序，空的堆会被丢掉
Minecraft.getInstance().setScreen(ItemPickerScreen.over(
        Component.translatable("screen.mxt.example_picker"),
        candidates));                    // List<ItemStack>

// 服务端指定的分类：网格在客户端用已同步的注册表构建
// opening 在还没进世界时返回 null（菜单要一份玩家物品栏），所以要判空，别直接塞给 setScreen
ItemPickerScreen screen = ItemPickerScreen.opening(payload.title(), payload.categories());
if (screen != null) Minecraft.getInstance().setScreen(screen);
```

`over` 是静态工厂而不是构造函数，只是因为分类那条路要用同样的形状处理自己的条目类型，而两个构造函数不能只靠 `List` 的元素类型区分。

## 物品怎么离开这个界面

界面自己一件物品都不发。点网格**只改客户端的携带堆**（carried），这是纯粹的本地幻觉；携带堆挂在玩家真正的 `InventoryMenu` 上（`PickerMenu.getCarried` / `setCarried` 代理过去），而这个 `PickerMenu` 服务端根本不存在（`super(null, 0)`）。

物品变真实只有两条路，都是原版创造模式那条：

1. **落进快捷栏**：把携带堆点到快捷栏格上，或用 1-9 / 副手键。界面在 `init()` 里给 `player.inventoryMenu` 挂了一个 `CreativeInventoryListener`（原版那个类，直接用），之后 `inventoryMenu.broadcastChanges()` 的槽位 diff 会把它发现的变化转发成 `ServerboundSetCreativeModeSlotPacket`；`removed()` 里摘掉；
2. **丢出面板**：同样的包，槽位 `-1`。

> `MultiPlayerGameMode.handleCreativeModeItemDrop` 在「打开了除创造界面以外的任何容器界面」时拒绝发包，而这个界面正是「别的容器界面」，所以丢弃不能走它，由 `ItemPickerScreen.throwCreative` 自己拼包——守卫条件与它是同一套。

## 手势对照（与原版创造物品栏完全一致）

| 手势 | 效果 |
| --- | --- |
| 左键网格格 | 拿起 1 个到携带堆 |
| Shift + 左键网格格 | 拿起一整堆 |
| 中键（拾取方块键） | 携带堆填成一整堆 |
| 左键同种网格格（携带堆非空） | 携带堆 +1；Shift 时直接补满 |
| 右键同种网格格（携带堆非空） | 携带堆 -1 |
| 左键空格 / 右键 | 放下携带堆 / 减少 1 |
| 悬停网格格按 1-9 或副手键 | 一整堆直接写进对应快捷栏槽 / 副手 |
| Q / Ctrl+Q | 从网格丢出 1 个 / 一整堆，不动携带堆 |
| 把携带堆放上快捷栏格 | 落进真实物品栏 |
| 点击面板外部 | 丢出携带堆（左键整堆，右键 1 个） |
| Shift + 左键快捷栏槽 | 清空该槽（原版行为） |

没有别的数量入口。格子里显示的堆就是拿到的数量，所以图和结果不会不一致。

## 为什么这不会变成绕过创造模式的口子

`ServerboundSetCreativeModeSlotPacket` 在**协议编解码层**就被 `GameProtocols.HAS_INFINITE_MATERIALS` 这个 codec modifier 拦下：解码时若服务端认为玩家不是创造模式就抛 `SkipPacketDecoderException`，包被静默丢弃（不断线、只打一条 debug 日志）；`handleSetCreativeModeSlot` 里还会再查一次 `hasInfiniteMaterials()`，并校验 `isItemEnabled`、槽位范围 1..45（0 号合成结果槽被排除）和数量上限。所以非创造客户端既发不出去也占不到便宜。完整分析见 `research/20_原版创造模式的信任模型.md`。

`/picker` 仍然要求 gamemaster 权限，并要求 `player.hasInfiniteMaterials()`——后者不是额外的保守，而是和那个包被检查的条件对齐：开一个服务端每个动作都会拒绝的面板，比不开更糟。

`ItemPickerManager` 只负责「注册表 → 可选项」的映射，现在只是**界面内容**的来源，服务端不再需要它。它产出的每一项是 `PickerItem(stack, names)`：**要画的堆**，加上**这一行能被哪些名字搜到**。堆本身保持原样，**不往物品上写任何东西**（没有自定义名称、没有后缀）——同一件替身物品代表好几个定义时靠搜索区分，不靠名字上的标记。名字交给目录自己给：

- 物品/方块注册表的条目本身就是物品，堆上已经写着它叫什么，于是名字就是「它显示的名字 + 它的注册 id」；
- 数据驱动定义没有自己的物品，堆上根本看不出它代表谁，于是名字由 `DefinitionText` 从它的 `Holder` / `ResourceKey` 生成翻译键得到——`mxt:fire` 在 `mxt:aura` 里就查 `aura.mxt.mxt.fire`（末两段之外的那一段 `mxt` 是注册表命名空间）——再补上它的 id。定义自带 `name` 的（19 张注册表的定义，名字写在数据包里）就用那个名字；
- 标签匹配展开出来的行，名字里既有那个物品自己的名字，也有它所属定义的名字和 id。

用列表而不是单个名字，是因为一行可以有好几种叫法。界面不再需要从「注册表 key + 条目 id」去反推任何东西；只有 `over(...)` 那条路没有目录可问，界面自己补上「展示名 + item id」。

翻译键的拼法统一由 `com.iafenvoy.mxt.util.DefinitionText` 决定：类别就是注册表自己的 path，没有例外表。手里已经有 `Holder` / `ResourceKey` 时直接 `DefinitionText.name(holder)`，只有拿到的是一根光秃秃的 `Identifier` 时才需要把类别当参数传进去（`DefinitionText.name(id, "resource")`）。自带 `name` / `description` 字段的定义（19 张注册表，含 `quality` 与 `quality_chain`）不走这条路：文本由数据包给，或由 `ContextNameCodec` 按 id 生成**同一个键**（`quality.mxt.<命名空间>.<路径>` 这类），所以两套名字键不再是两套。

分类就是注册表本身，`/picker <分类 id>` 可以只列出某一个（如 `/picker mxt:aura`、`/picker mxt:artifact`、`/picker mxt:item_binding`），不写则给出全部已注册分类。

## 界面细节

- `over` 会过滤掉 `isEmpty()` 的堆，并保留传入堆上的组件（品质、灵气等），不会重建物品。
- 界面不主动关闭，可以连续取多件，由玩家自己关。
- 搜索框是原版创造模式搜索栏那套写法：`EditBox` 无边框、`setCanLoseFocus(false)`、开局就 `setFocused(true)`，`charTyped` / `keyPressed` / `preeditUpdated` 都先转给它，只有 Esc 会落到父类去关界面。
- 过滤规则：空格分词，全部命中才显示；普通词匹配该行**声明过的名字**的子串，`@` 开头的词只匹配物品命名空间（如 `@minecraft diamond`）。一行如果一个名字都没声明，就退回用它的展示名，所以「看得见的就能打出来」对每一行都成立。
- 候选列表为空的分类不显示任何占位文本，网格就是空的。
- 界面尺寸固定为原版 tab 的 195×136，由 `AbstractContainerScreen` 居中，不做窗口裁剪——和原版一样，窗口过小时面板会超出画面。
- 标题画在贴图左上角 `(8, 6)`，和搜索框同一行——原版搜索 tab 的那一行本来只有搜索框，没有标题位；标题在 widget 之后绘制，所以不会被搜索框盖住。
- 搜索框不放进 widget 绘制队列（用 `addWidget` 而不是 `addRenderableWidget`），改在 `extractBackground` 里手动画，这样它留在背景层、永远在物品下面，和原版一致。
- `containerTick` 里一旦玩家不再拥有无限材料就切回 `InventoryScreen`——原版创造界面也是这么做的，避免面板开着时被切回生存、还留着一个每个动作都会被服务端拒绝的界面。
- 网格槽是 `GridSlot`，`mayPickup` 会拒绝特性开关之外的物品和被 `CREATIVE_SLOT_LOCK` 标记的物品，和原版 `CustomCreativeSlot` 一致。

新增界面时注意两点：窗口缩放会走 `Screen.resize` → `rebuildWidgets` → `init`，任何界面状态（如搜索词、滚动位置）都要在界面字段里自己保留；容器界面把「面板底图 + 组件」交给 `extractBackground`、把「槽位、悬浮高亮、提示框」交给 `extractContents`，顺序是原版定好的——要复用原版的渲染层级就不要自己拆开重画。
