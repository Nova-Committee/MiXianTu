# 26_可拖动 HUD 框架设计

审计/编写日期：2026-09-22。

## 0. 基准

对着两份东西写：

- **参考实现**：`E:\Java\AxolotlClient-mod`（AxolotlClient，HUD 模块部分自述 "This implementation of Hud modules is based
  on KronHUD"、GPL-3.0）。读的是它 `26.1` 那一份（与本仓 MC 版本同代）：
  `common/.../modules/hud/gui/component/{HudEntry,Positionable,Configurable}.java`、`gui/entry/AbstractHudEntry.java`、
  `HudManagerCommon.java`、`versions/26.1/.../hud/{HudManager,HudEditScreen,HudEntryWidget}.java`。
- **本仓现状**：`com.iafenvoy.mxt.screen.overlay.hotbar.HotbarOverlay`（`BOTTOM_OFFSET = 104`，居中写死）与
  `screen.overlay.resourcebar.ResourceBarOverlay`（`position(...)` 按锚点与布局现算）。两者各自注册成独立的 `GuiLayer`，位置都是
  **代码里算出来的常量**，玩家无从调整。

## 1. 结论

移植**框架**，不接内容：一套"模块登记 HUD 元素 → 框架每帧画它 → 编辑器让玩家拖 → 位置落进客户端配置"的最小骨架。

本轮明确**不做**缩放（用户要求）。KronHUD 的位置系统是"比例 + 缩放"两件事耦合在一起的（`Positionable.getTrueX()` 就是
`getX() * getScale()`），只做移动时整个缩放维度（`getScale` / `setScale` / `supportsScaling` / 四角抓手 / 拖角改变 `scale`
）都没有意义，一并去掉——留下的坐标语义是干净的"窗口比例 → 像素"。

落地的文件（`com.iafenvoy.mxt.screen.overlay.hud`）：

| 文件                      | 职责                                                                           | 对应 KronHUD                                                       |
|-------------------------|------------------------------------------------------------------------------|------------------------------------------------------------------|
| `ScreenBounds.java`     | 屏幕矩形（缩放像素、`xEnd`/`yEnd` 为开区间）                                                | `util/Rectangle`（去掉缩放相关分量）                                       |
| `HudAnchor.java`        | 元素矩形上"哪个点代表它"：默认位置按它算，尺寸变化时它不动                                               | KronHUD 靠 `Positionable` 的 raw/true 两套坐标顺带表达，本仓独立成一个枚举           |
| `HudLayout.java`        | 位置的比例化存取、`x,y,visible` 文本格式、窗口尺寸查询                                           | `util/DefaultOptions` + KronHUD 的 `Option` 体系（本仓改用既有 Jupiter 配置） |
| `HudEntry.java`         | 元素契约：布局键、显示名、尺寸、渲染块、默认位置、显隐、能否拖动                                             | `gui/component/HudEntry`（去掉 `Configurable`、`dependsOn*`）         |
| `RenderBlock.java`      | 元素交给布局去画的一个"东西"：宽高 + 画法，外加相对所属堆叠的 `left()`/`top()`                           | 无对应物，是这次拆分出来的                                                    |
| `HudRenderer.java`      | **唯一的渲染器**：把一组 `RenderBlock` 竖着堆进元素矩形（`renderColumn`，底部锚点用 `renderStanding`） | 原本散在各模块的绘制代码                                                     |
| `AbstractHudEntry.java` | 位置的夹取与持久化、命中判定、默认位置回退                                                        | `gui/entry/AbstractHudEntry`（去掉缩放、依赖图）                           |
| `HudManager.java`       | 登记表、渲染、命中、编辑器开关、GUI 层注册                                                      | `HudManagerCommon` + `HudManager`                                |
| `HudEditScreen.java`    | 拖动与方向键微调                                                                     | `HudEditScreen`（去掉缩放与吸附）                                         |

配套（框架以外，改动很小）：

- `config/MxtHudConfig.java`（新）：**HUD 布局单独一份配置文件** `config/mxt/mxt-hud.json`，只有 `hud.layout_v2`
  一项。它与客户端设置分开的理由见 §1.6。
- `config/MxtClientConfig.java`：去掉 `hud` 分类，文件路径统一到 `config/mxt/mxt-client.json`；`MxtServerConfig` 同步到
  `config/mxt/mxt-server.json`。文件名保留 `mxt-` 前缀，这样"挪目录"没有顺手把玩家已经有的配置文件改名。
- `command/HudCommand.java`（新）：客户端命令 `/hud`（列出/复位/打开编辑器），是"编辑器里什么都没有"的唯一诊断入口。
- `registry/MxtKeyMappings.java` 新增 `key.mxt.hud_layout`（**默认右 Shift**：摆放 HUD 要一边看着 HUD 一边调，编辑器必须能在游戏里按到；右
  Shift 也是别的客户端 HUD 编辑器用的键）。
- `MiXianTuClient#init` 在客户端初始化时登记资源条两列，并注册 `MxtHudConfig`。
- 两份 lang 各加 13 个键；`HudManager` 把 GUI 层 `mxt:hud_framework` 注册成 `registerAboveAll`。

## 1.4 渲染只在布局里发生（第二次修正）

第一版把"画自己"留给了元素：`ResourceBarEntry.render()` 自己遍历这一列、自己给每个条算绝对坐标、自己调渲染器。用户的要求是*
*子区域只提供对象，渲染统一由布局系统完成**，于是拆成三层：

1. **子区域提供对象**：`ResourceBarOverlay.column(anchor)` 回答"这一列有哪些条、什么顺序"，
   `ResourceBarEntry.renderBlocks()` 把每条包成一个 `ResourceBarBlock`。整个过程中没有任何一个屏幕坐标被算出来——块只报自己的宽高（以及水平上相对所属堆叠的
   `left()`）。
2. **布局负责放置与绘制**：`HudManager.render` 拿到块列表，按元素的锚点决定堆叠的落点（`HudRenderer.renderColumn` /
   `renderStanding`），**由渲染器的游标自上而下推进**，逐个调 `draw`。
3. **渲染器只认绝对坐标**：`ResourceBarRenderer` 一族仍然只知道"给我 x/y 我画一条"，它们的调用方换成了 `ResourceBarBlock`
   ，而块的位置是渲染器给的。

为什么要有 `RenderBlock` 这一层，而不是让 `ResourceBarEntry` 直接调 `HudRenderer`：如果有两个模块各自实现"画一列"
，就会有两份"往上堆"的循环、两份锚点处理、两处要跟着布局语义改。有了块列表，"画什么"和"放哪里"就分开了——`RenderBlock` 是"
画什么"的最小表达，`HudRenderer` 是"放哪里"的唯一实现。资源条的四个元素（两列 + 目标/Boss 两条）全部登记进框架，所以"往上堆"
现在只有一份代码。

`HudEntry.render()` 仍然保留：它给"无法表示成块列表"的元素留了口子（比如以后要接的快捷栏可能整块自绘），此时
`renderBlocks()` 返回空列表，框架改为调 `render()`。

### 1.4.1 三条硬约束（都是踩出来的）

**① 容器的尺寸与内容的排布必须共用同一份算法。** 出过一次：`stack()` 里累积了一个"槽位 y"，而渲染器自己又有游标，`boundsOf`
把槽位当成偏移**再加一遍**，于是列高 30 而实际只画 15——表现为第三条下面多出一截空间。现在**垂直方向只有一处算法**
：块按自己的高度顺序叠，`RenderBlock.boundsOf` 就是"高度相加"，两者的数字是同一份。为了不再留第二条路，`RenderBlock` **没有垂直偏移
**（只有水平的 `left()`）；块与块之间的空隙用 `RenderBlock.spacer`（一个只占高度、不画东西的块）表达，它同样走"高度相加"这一条路。

**② 框架在构造期不向子类问任何东西。** 出过一次：`AbstractHudEntry` 在构造器里调 `defaultX()`，而
`ResourceBarEntry.defaultX()` 读自己的 `layout` 字段——该字段按 Java 规则在 `super(...)` 之后才赋值，于是启动即
`NullPointerException`。位置现在改为第一帧由 `refreshPlacement()` 惰性套用；`HudEntry` 的接口注释把这条写成保证。固定行（
`ResourceBarFixedEntry`）override 了 `refreshPlacement()`：它的尺寸每帧现算、位置固定，所以除了 `setSize()` 还要调基类的
`placeAtDefault()` 把默认位置真正落成锚点——只算尺寸不落位置的话锚点停在 `(0,0)`，行会画到窗口左上角。

**③ 同一个事实只留一份状态，尤其是"位置从哪来"。** 出过一次，而且症状最容易被误诊：位置**写盘一直是好的**（`mxt-hud.json`
里就是拖动后的比例），但重启总回到默认。原因是 `AbstractHudEntry` 除了 `storedPlacement` 之外又存了一个
`usingStoredPlacement` 布尔，用来回答"当前用的是存档位置还是类给的默认位置"——它初值是 `false`（Java 默认值），只有
`setPosition()` 里才置真，而"从存档比例换算位置"那一支恰好被它把关，于是**文件里的位置永远轮不到被读**。删掉那个布尔之后，"
存档里有没有这个键"（`storedPlacement == null`）是唯一判据：有键就按比例算（只在窗口尺寸变化时重算），没有键就用 `defaultX()`/
`defaultY()`（每帧重算，好跟着窗口走）。同理，`resetToDefault()` 必须把键从文件里**删掉**
——只清内存的话复位跨不过重启，看起来又是"没保存"。

## 1.5 HUD 布局独立成文件（第三次修正）

布局从 `config/mxt-client.json` 挪到 `config/mxt/mxt-hud.json`（`MxtHudConfig`），整个 `config/` 下也统一收进 `config/mxt/`
。理由是它和别的客户端设置**不是一类东西**：别的设置是"挨个填的选项"，布局是"拖动过程中不断写、一行一个元素"
的数据；混在一个文件里，一份坏掉的布局会把真正的设置一起带下去。分开之后 `mxt-client.json` / `mxt-server.json` /
`mxt-hud.json` 各自可以单独删、单独看。

## 1.6 已接入的元素：资源条两列 + 目标/Boss 两条（同日）

资源条从"一个覆盖层统一算位置、自己画"改成**四个登记进框架的元素**，原有那个 `mxt:resource_bars` GUI 层**已删除**：

| 文件                                                         | 改了什么                                                                                                                                    |
|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `screen/overlay/resourcebar/ResourceBarEntry.java`（新）      | 一列资源条的元素：默认位置 = 屏幕中心 ±（20 + 半列宽）、底部向上 47；`blocksWithGaps()` 把这一列包成块列表（条之间插一个 `spacer`）并量出尺寸；锚点是**下边缘中点**                                |
| `screen/overlay/resourcebar/ResourceBarFixedEntry.java`（新） | 目标/Boss 两条固定行：`moveable() == false`（不进编辑器、不出占位框）、位置每帧由 `placeAtDefault()` 现算、`visible()` 回答"现在有没有东西可显示"所以永不入档；间距与列共用 `blocksWithGaps()` |
| `screen/overlay/resourcebar/ResourceBarBlock.java`（新）      | 一条资源条交给布局去画：高度是条**实际画出来的** 5px（量过 `resource_bar.png`：条体占第 0..4 行，下一格从第 10 行开始），而不是 render data 声明的 8px                                  |
| `screen/overlay/resourcebar/ResourceBarOverlay.java`       | 从 `enum implements GuiLayer` 改成**纯工具类**：`column(anchor)` / `row(target, layout)` / `rowX(...)`，只回答"哪些条、什么顺序"                            |
| `screen/overlay/resourcebar/ResourceBarRenderer.java`      | 渲染器仍然只认绝对坐标；调用方变成 `ResourceBarBlock`                                                                                                    |

条间距是 `ResourceBarEntry.BAR_GAP = 5`（GUI 像素），以 `spacer` 块的形式插在条之间——不是加在每条高度上，否则最后一条会多留一格。三列条的列高因此是
`5×3 + 5×2 = 25`。

位置语义（写进 `docs/数据包格式.md` 了）：`anchor` 现在只决定"这一条落进哪一列"；两列各自是 `resource_bars.left` /
`resource_bars.right`，位置按窗口比例存在客户端配置里。**数据包不需要写任何尺寸或位置字段**
——旧字段一个没加、一个没删，所以老包的行为完全不变，只是这两列现在能被玩家拖。

目标/Boss 两条**不参与拖动**：它们钉在准心所指的实体上，是"被看的东西"
的属性而不是玩家的排布，没有可决定的东西也就没有要存的档。顺带修掉一个旧实现的缺陷：三个布局原先在同一个循环里算
x，右锚点的目标条会被画到左边；现在按 `anchor` 分开算，各画各的。

## 2. 与 KronHUD 的差异（都是刻意的）

1. **没有缩放**：见上。由此也去掉了 `Positionable` 里 `getRawTrueX`/`getTrueX`/`offsetTrueWidth` 那一整套"raw 与 true
   两套坐标"——不缩放的场合两者恒等，留着只是噪音。
2. **没有边界依赖图**（KronHUD 的 `addBoundsDependency` / `SnapAnchorType` / `hud_dependencies.json`
   ）。它存在的唯一目的是让"贴着别人右边缘的元素"在缩放与吸附时跟着走；没有缩放、没有吸附，它就是死代码。
3. **没有吸附参考线与链接线**（`snapping/SnappingHelper`、`hudEditScreen` 分类里的 `snapping` 开关）。这是本轮**最大的功能缺口
   **，也是最可能下一轮补的：编辑时对齐到别的元素/屏幕中线。
4. **没有每元素配置项**。KronHUD 给每个元素生成一个配置分类（`enabled`/`scale`/`x`/`y`/`hide`，元素还可以自己追加）。本仓的配置体系是
   Jupiter 的 `AutoInitConfigContainer`（字段在类初始化时就固定），按元素动态生成分类不合适，于是位置的持久化走**一张 map**
   ，其余每元素设置留给元素自己（现在的做法：元素自己的模块配置分类）。
5. **位置的存储介质不同**：KronHUD 走它自己的 `Option` 框架与 `hud_dependencies.json`；本仓走 `config/mxt/mxt-hud.json` 里的
   `hud.layout_v2`。**写了就立刻落盘**（拖动过程中每一帧都可能写），没有"关闭界面时统一保存"。
6. **编辑界面不隐藏原版 HUD**，而且**不加背景模糊**。KronHUD 的 `HudEditScreen`
   世界里那些原版元素是它自己接管的；本仓没有接管任何原版元素，所以进编辑器时原版血条/快捷栏照常显示（`hideGui` 由玩家自己按
   F1）。模糊是另一件事：框架的 GUI 层在**任何界面之前**就被提取，而"游戏内界面之外"的屏幕会给背景加模糊，那一刀正好把整层
   HUD 一起糊掉——玩家要摆的元素反而看不清。所以 `HudEditScreen` 覆写 `isInGameUi()` 返回 `true`
   （容器界面、书界面也是这么做的），原版据此只画半透明渐变。接管原版元素仍是后续内容接线的事。
7. **`render` 的入口**：KronHUD 在 `HudManager#render` 里判断"当前界面不是编辑器才画元素"，本仓反过来——**编辑器里也照画**
   （元素是真实渲染，编辑器只叠加占位框与名字）。这样"拖的时候看到的就是实际效果"，也省掉"占位框画得像不像元素"这个问题。

## 3. 位置语义（唯一容易踩的地方）

- 存的是**窗口比例**，不是像素：`config/mxt/mxt-hud.json` 里 `hud.layout_v2` 的每一项形如 `"0.4375,0.9,true"`，含义是"横向
  43.75%、纵向 90%、可见"，**指向元素矩形的左上角**。
    - 理由：KronHUD 也这么做，而它解决的是本仓同样存在的问题——分辨率与 GUI
      缩放变化时框架收不到通知，只有比例才是自洽的。像素方案在"窗口变小"时会静默丢布局。
- **坐标原点在窗口左上角**，x 向右、y 向下；框架内部、命中判定、绘制与编辑器全部按"左上角"说话。存储键带 `_v2`
  是因为位置的含义变过一次（见下面的锚点）：旧键静静地不再被读，那批布局退回默认位置——与本仓"改名 / 删字段后旧键不生效"
  是同一套口径。
- **锚点（`HudAnchor`）** 是元素自己声明的"矩形上哪个点是它想待的地方"，只影响两件事：`defaultX()` / `defaultY()` 指的是哪个点，以及
  **元素改变尺寸时哪个点不动**。默认是左上角；资源条两列用**下边缘中点**
  ，所以列变高时往上长、下边缘钉在原地。存的是左上角，所以老布局不会因为锚点变化而整体位移；读回来时再换算回锚点，之后的尺寸变化才按锚点走。
- 读出来的像素在**每次读**的时候夹进窗口（`Mth.clamp`），所以元素不可能被拖到看不见的地方，也不会在窗口变小时跑到屏幕外。
- **窗口变小不会改写存档**：夹取只发生在内存里，只有当玩家自己移动/切换显隐时才写比例。窗口恢复后元素回到原来的位置——这正是像素方案做不到的那件事。
- 没存过的元素（map 里没有这个键）用元素自己给的 `defaultX()`/`defaultY()`，**并不立刻写档**；写档只由玩家的动作触发，而且*
  *每帧重新套用默认值**，所以默认位置随窗口走、改代码就能改，不会被第一次启动固化。
- **"位置从哪来"只有一个判据**：存档里有没有这个键。有键 → 按比例算（第一帧就套用，之后只在窗口尺寸变化时重算）；没键 →
  每帧问元素自己的默认位置。**存过档的元素在读档那一刻就必须生效**——`registerConfigHandler()` 会同步 `load()`，元素在
  `FMLClientSetupEvent` 里构造时文件已经读完，所以直接读得到；这里出过一个 bug（多存了一个布尔标志把关，见 §1.4.1 ③），症状是"
  文件里明明有值，重启却回默认"。
- **复位（`resetToDefault()`）会把这个键从文件里删掉**：复位若只改内存，重启就会把旧位置带回来，和"没保存"
  无法区分。删掉之后元素重新跟着窗口走（默认位置本来就定义在窗口上），直到玩家再次拖动它。
- 手动改 JSON：解析失败（少段、非数）→ 当成"从没放过"，退回默认位置，不抛异常、不阻断启动。这也和本仓"配置读坏不崩"的一贯口径一致。
- **空元素也要能被看见**：资源条两列在没有条可画的时候仍然返回 71×8
  的矩形，所以在编辑器里照样有框、有名字、能拖——第一版曾让空列返回零尺寸（"没东西就不该有框"
  ），结果是玩家在没内容的存档里打开编辑器一片空白，看起来像功能坏了。这条推翻的是当时的取舍：**编辑器的职责是让玩家看到"
  这里有一个可以摆的东西"，不是替玩家判断它现在有没有内容。**

## 4. 扩展点（内容接线时照着做）

接一个元素只有三步：

```java
public final class FooEntry extends AbstractHudEntry {
    public FooEntry() { super("foo", WIDTH, HEIGHT); }
    // displayName / layoutWidth / layoutHeight / defaultX / defaultY
    // 二选一：renderBlocks() 提供对象（推荐），或 render() 整块自绘
}
// 在客户端初始化里登记；布局键跨版本不要改
HudManager.register(new FooEntry());
```

- **可拖动**：默认就是。**不该被拖**（比如居中的快捷栏）重写 `moveable() { return false; }`——它依然会被框架画，只是不进
  `moveableEntries()`、不出占位框、不参与命中。
- **提供对象而不是自己画**：重写 `renderBlocks()`，每个块报宽高，水平偏移用 `left()`，位置交给 `HudRenderer`。块之间的空隙用
  `RenderBlock.spacer(w, h)`，**不要**把它加到某一条的高度里。
- **尺寸会变**：自己调 `setSize(w, h)`。框架不会替你量，也不会在渲染里反推。**空元素也要给非零尺寸**，否则编辑器里看不到。
- **登记时机**：客户端初始化（`FMLClientSetupEvent`），不要等第一帧渲染——GUI 层只在世界里画。
- **只在编辑器里才画的东西**：`editMode()`（`AbstractHudEntry` 提供）在编辑器打开时为真。想画参考框的元素用它。
- **被拖动时的样子**：重写 `setDragging(boolean)`。
- **诊断**：登记完可以 `/hud` 看一眼它有没有出现、位置尺寸对不对。

## 5. 不做或推迟的部分

- **缩放**（用户明确本轮不做）：拖角改大小、`scale` 配置项、`supportsScaling`。
- **吸附与对齐参考线**：见 §2.3，是下一轮最自然的补充。
- **元素级显隐开关**：框架的存储格式里**已经带了** `visible` 字段（`setVisible` 会写档、不显示的元素不出占位框也不参与命中），但
  **编辑界面里没有切换入口**。谁把入口做出来（右键菜单 / 配置开关 / 编辑器里的列表）都不需要再改存储格式。
- **自定义 HUD 文本**（KronHUD 的 `CustomHudEntry`）：那是"玩家自己写一行文本"，与本仓的玩法无关，不移植。
- **接管原版元素**（血条、物品栏、Boss 条……）：KronHUD 有一大套 `mixin` 把原版 HUD 拆成可拖动元素。本仓**一个都没接**
  ，本轮也不打算动——那属于"内容接线"，且每个原版元素都要单独写 mixin。
- **配置界面里的入口**：Jupiter 的 HUD 配置页目前只有那一个 layout 表；加一个"打开编辑器"的按钮是可行的后续项（`/hud open`
  已经有了）。

## 6. 验证方式

- **编译**：`gradlew.bat compileJava compileTestModJava processTestModResources`（必做）。
- **lang**：两份键集合必须一致（当前 `keys identical (668)`）。
- **实机（已验证）**：`gradlew runTestClient` 已跑通到进世界并正常退出，交付前用两种一次性手段定位过 bug，都可以照抄：
    1. **几何日志**：临时在 `ResourceBarEntry.renderBlocks()` 里把每条打印成一行（`id slot=… blockH=… dataH=…`），跑一次
       `runTestClient`、在游戏里开一次编辑器，然后看 `run-test-client/hud-debug.log`。列高是"实际两倍"这件事就是这条日志一眼看出来的——
       **截图量不出来，日志可以**。
    2. **输入日志**：临时在 `HudEditScreen` 的 `mouseClicked`/`mouseDragged` 里打印事件坐标与命中结果。它把"
       点击没到达界面"与"到达了但位置被覆盖"分开了（当时是后者）。
    3. **先看文件，再看读的那一侧**：用户报"布局没有正常保存、重启就恢复"时，第一步是打开
       `run-test-client/config/mxt/mxt-hud.json` 看内容与 mtime——文件里已经是拖动后的比例，于是"没保存"直接被排除，问题只在
       **读**：构造时读到没有、读到了有没有被套用。凡是"存了又好像没存"的报告，都先做这一步，能省掉在写侧乱改。
  > 前两处日志都是脚手架，确认修好后应当删掉（本轮已删）。留一条记录在这里，是为了下次遇到同类问题时不必重新发明。
- **要看的输出**：进世界后按 `key.mxt.hud_layout`（默认右 Shift）或打 `/hud open`，应看到两个可拖元素——「左列资源条」「右列资源条」——各自带半透明框与名字，且
  **背景不模糊**；`/hud` 打出这两行（外加两条不可拖的固定行）。三列条的列高应为 `5×条数 + 5×(条数-1)`（三条时
  25）；把窗口拉小应看到列贴边但不消失、还原后回到原处。
- **存档往返必须重启一次才算验证**：拖完元素、退出客户端、再进来，元素应当还在拖到的位置（这条正是"保存"与"读回"
  的分界；只在一次会话里看到位置对，说明不了任何事）。`/hud <键> reset` 之后再重启一次，应当仍在默认位置。

## 7. 与现状的差异 / 下一步

- 数据包**字段没有任何变化**：`anchor` 的语义从"自我 HUD 的左列或右列"扩到"三个布局通用的左右分列"，目标/Boss
  两条因此从一个循环拆成按锚点分开算。老包不用改，行为只在"右锚点的目标条画在左边"这一点上被修正。
- 玩家可见的变化：客户端配置多了一份独立的「HUD 布局」（`config/mxt/mxt-hud.json`，也在配置界面里作为单独一页），「编辑 HUD
  布局」按键默认右 Shift，新增客户端命令 `/hud`，左右两列资源条可以拖动并存档，编辑器背景不再模糊。配置文件统一挪进
  `config/mxt/`。
- 已完成：① 资源条接成元素；② 渲染收进布局（§1.4）；③ 布局独立成文件（§1.5）；④ 目标/Boss 两条也成了元素、资源条自己的 GUI
  层删除（§1.6）；⑤ 编辑器去掉背景模糊（§2.6）；⑥ 存档读回（用户报"重启就恢复"）：位置来源只认"存档里有没有这个键"，复位会删键（§1.4.1
  ③、§3）。
- 还没做、需要拍板：给编辑器补吸附与对齐参考线；给编辑界面补显隐切换入口；把快捷栏等其它覆盖层接进来（快捷栏居中，更适合
  `moveable() == false`，即"框架画、不给拖"）。
- 遗留的自身问题：编辑界面里没有复位入口（现在只能 `/hud <键> reset` 或手改
  JSON）；拖动时除了一声抓取音效没有别的反馈；拖动过程中每一帧都可能写一次配置文件（换来"崩了也不丢布局"
  ，代价是频繁小写入），若以后改成"松手时写"就要重新考虑崩溃丢档；`BAR_GAP` 这类间距是写死的常量，若以后要让内容方调，得先想清楚它属于数据包还是玩家设置。

