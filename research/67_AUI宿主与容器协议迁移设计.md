# 67 —— 把界面宿主迁到 AUI 自带的 ApricityScreen / ApricityContainerScreen

状态：**已落地**（落地记录见 §6；以代码为准）。本文记的是"为什么换成这个形状、换掉了什么、哪些坑是 AUI 侧的硬约束"。

## 0. 一句话

ApricityUI 自带页面宿主 `com.sighs.apricityui.screen.ApricityScreen` 与容器宿主 `ApricityContainerScreen`：**后者绑死了它自己的 `ApricityContainerMenu`**（构造器与泛型都是它），所以"用上它"不是换个父类就完事，而是要把我们 8 个容器菜单改成 `ApricityContainerMenu` 的子类（自建 `SlotLayout` + `ContainerDataSource`）、9 个容器界面改成 `ApricityContainerScreen` 的子类，从而**删掉我们自己那套"每帧读 DOM 几何再写回 `Slot.x/y`"的对账层**；`AuiWrappedScreen` 从"自己一套宿主生命周期"降级为**helper 接口**（页面绑定、兜底行、元素小助手）。

## 1. 迁移前的形状（为什么要换）

- `screen/aui/AuiScreen`（继承原版 `Screen`）与 `screen/aui/AuiContainerScreen`（继承原版 `AbstractContainerScreen<T>`）各自持一份 `AuiWrappedScreen.State`，各自建/拆 `Document`、各自 `applyViewport`、各自 `applyViewport(true)` on resize；`AuiWrappedScreen` 里那套 `auiInit` / `auiRebind` / `auiClose` / `StyleHold` 等于把 AUI 宿主已经做过的事又做了一遍。
- 容器侧最重的一块是**槽位几何对账**：AUI 在绘制阶段才提交元素偏移、且偏移是记忆化的，所以"槽位还在文档原点、面板已经居中"的混合快照会把物品画到屏幕角上。我们的解法是每帧读 DOM、用面板实时矩形做包含判定、不通过就一个字节都不写、并保留上一份已对账的几何（`AGENTS.md` §4 那一长段）。
- AUI 侧其实已经把这件事做完了：`SlotDataBinder` 负责把 DOM 的 `<slot>` 与菜单槽位对上、把 DOM 几何写回 `Slot.x/y`、把物品交给页面自己的 `<item>` 元素画；它的混入（`AbstractContainerScreenMixin`）取消原版槽位绘制、把浮动物品并进 AUI 的 PIP 批次、并把槽位悬停改成按 DOM 矩形判定。

所以这次迁移的**收益就是删代码**：删掉我们那套对账、门控、tooltip 锚点与两套宿主。

## 2. AUI 侧事实（逐条核过 1.2.6 源码，别照记忆写）

1. `ApricityScreen(String templatePath)`：`init()` 里 `Document.create(templatePath)`（**每次 `init` 都先 `remove()` 再重建**，窗口缩放会重进 `init`）、`resize(w,h)` → `applyViewport(true)`、`onClose()` 触发 body 的 `unload` 事件并拆文档、`removed()` 也拆；`getLinkedDocument()` 是它私有的 `linkedDocument`，**没有 setter**；`extractBackground` 只在 `showDefaultBackground` 为真时画原版背景；`extractRenderState` 在 `super` 之后 `ApricityGuiLayers.submitUi(graphics)`；`mouseScrolled`/`keyPressed` 自带 Ctrl +/-/0 缩放与重载键。
2. `ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu>`：构造器只有 `(ApricityContainerMenu, Inventory, Component)`，窗口尺寸按**整窗**给（所以 `leftPos/topPos` 为 0）；`init()` 里 `Document.create(menu.getTemplatePath())`（**页面路径来自菜单的 `SlotLayout`**）→ `new SlotDataBinder(menu)` → `bindSlotsFromDocument` → `syncAllSlotPositions`；`extractRenderState` 每帧同步槽位状态/悬停 → `super` → `submitUi(graphics, floatingItems)` → `drawSlotHoverTooltipByElement`；`extractTooltip`/`extractLabels` 被它清空；`onClose`/`removed` 拆文档并 `slotBinder.clear()`。**没有**滚轮/按键缩放（那是 `ApricityScreen` 才有的）。
3. `ApricityContainerMenu extends AbstractContainerMenu`（**非 final**）：四个构造器都 `super(ApricityMenus.APRICITY_CONTAINER.get(), containerId)`，也就是说**菜单实例自报的类型是 AUI 的**。`AbstractContainerMenu.getType()` **不是 final** → 子类覆写成自己的菜单类型即可（开屏包用的是 `menu.getType()`，见下）。
4. `SlotLayout(String templatePath, List<ContainerEntry> containers, Map<String,List<String>> filterSelectors)`：`ContainerEntry(id, bindType, baseIndex, capacity, primary)`，`resolveGlobalSlotIndex(local) = baseIndex + local`；布局会随开屏数据序列化到客户端（`write` / `read`）。**每个容器是一段连续区间**，所以"自己与对方交错排列"这种槽位顺序表达不出来（迁移时把菜单槽位顺序改成按容器分组）。
5. `ApricityContainerMenu.initializeSlots`：按 `baseIndex` 排序，非玩家容器用 `containerSources.get(id)` 的 `createSlot(localIndex, 0, 0, filter)` 建槽（**没有数据源时**建 `UiSlot` 包一个一次性 `SimpleContainer`，客户端就是这样），玩家容器最后按 `PlayerInventorySlotOrder` 建 `UiSlot`；`playerInventoryIndexToMenuRelativeIndex` 把"背包逻辑下标 0..35"映射成"主背包 9..35 再快捷栏 0..8"，**与我们的页面 `<slot slot-index>` 用的原版背包下标一致**。
6. `SlotDataBinder.bindSlotsFromDocument`：只绑定**有直接 `<item>` 子元素**的 `<slot>`（且其祖先 `<container>` 的 `id` 能在布局里查到、`slot-index` 合法），其余是"展示槽位"；`document` 刷新后会经 `ForgeDocumentExpander` → `SlotContentRules.normalizeTemplate` **自动给每个 `<slot>` 补一个 `<item>` 子元素**（`minecraft:air`），所以我们的页面不用改标记。
7. AUI 的 `global.css` 已经给了 `slot { width:18px; height:18px; position:relative }` 与 `slot > item { position:absolute; left:1px; top:1px; width:16px; height:16px }`——正好是我们 `ITEM_INSET = 1` 的口径，物品由页面自己画，位置不用我们操心。
8. `AbstractContainerScreenMixin` 的三个取消（`extractSlot` / `extractSlotHighlight*` / `extractFloatingItem`）与 `isHovering(Slot,double,double)` 的改写**只在 `instanceof ApricityContainerScreen` 时生效**，所以"继承它"是让这些行为生效的唯一开关。
9. `ServerPlayer.openMenu` 的开屏包用的是 `menu.getType()`（26.1 已无 `NetworkHooks.openScreen`），客户端按这个类型找屏幕工厂——所以"菜单自报类型"这一处覆盖必须做对，否则客户端会去建 AUI 的菜单与屏幕。

## 3. 定案

1. **非容器页**：`AuiScreen extends ApricityScreen implements AuiWrappedScreen`。文档归 AUI（建/拆/缩放/输入都在它那儿），我们只保留"页面皮肤"（`AuiStyles.extract` 那块原版灰底，且不调 `super.extractBackground`）与 helper。
2. **容器页**：我们的菜单 `extends ApricityContainerMenu`，用**私有 Setup 模式**在 `super(...)` 之前把容器、`SlotLayout`、`ContainerDataSource` 一起造出来（Java 的 `super(...)` 只能用表达式，所以公开构造器 `this(...)` 转给一个接 `Setup` 的私有构造器，`super` 之后再 `setup.bindMenu(this)` 做**迟绑定**，供 `Ghost` 这类需要菜单指针的槽位用）。
3. **`getType()` 覆写**：每个菜单返回自己在 `MxtMenus` 里的类型。"容器页面归 AUI 的菜单类型"这条不成立——那样客户端只会建出 AUI 的屏幕，我们的页面绑定就没了。
4. **页面路径上移到菜单**：`ApricityContainerScreen.init()` 从 `menu.getTemplatePath()` 取页面，所以页面由菜单的 `SlotLayout` 决定（一个菜单一条页面；站台的店主/顾客、丹炉四页各自按自己的模式/视图选）。屏幕不再报页面路径，`AuiPages.page(...)` 的调用点从屏幕搬到菜单。
5. **`AuiWrappedScreen` 降为 helper**：去掉 `auiSetDocument`（文档不再由本接口拥有：非容器页是 AUI 的私有字段，容器页也是 AUI 的私有字段），`getLinkedDocument()` 仍由 `AuiLinkedScreen` 提供；留下的默认方法只有页面绑定与兜底（`auiPreparePage` / `auiPageOpened` / `auiRebind` / `auiClearBindings` / `auiTick` / `auiReadyToDraw` / `auiPageWritable` / `auiBound` / `pageMissing` / `pageInvalid` / `extractPageError` / `getOrThrow` / `find` / `missing` / `text` / `click` / `scrollList`）。`auiClose` 改成**只丢绑定**（拆文档已经是 AUI 的事）。
6. **删掉的**：`parkSlots` / `syncGeometry` / `reportBadCell` / `waitForGeometry` / `cells` / `bindCells` / `bindInventoryCells` / `cellsOf` / `inventoryMenuIndex` / `slotIndexOf` / `ITEM_INSET` / `extractSlot` 与 `isHovering` / `mouseClicked`·`mouseReleased`·`mouseDragged` 的门控（这些现在由 AUI 的混入与 `SlotDataBinder` 负责）。
7. **保留的四件**：
   - 页面绑定 helper 与**兜底红字**（AUI 没有"页面不合契约"这条线）。
   - **面板实时矩形**：屏幕的 `panel` 元素 + `Position.of/Size.of` 每帧算 `panelLeft/Top/Width/Height`。它同时喂 `hasClickedOutside`——AUI 的容器宿主是整窗尺寸，原版的"点面板外丢下手上物品"会失效，所以这一处必须自己按面板矩形判。
   - **额外元素的 tooltip**（丹炉的标题/温度/上限这类任意元素的圆角提示）：AUI 的 `drawSlotHoverTooltipByElement` 只认 `<slot>` 与 `MinecraftElement`（`<item>`/`<texture>`），所以这类锚点仍归我们，且必须在 `super.extractRenderState` 之后登记（同一帧先排队的赢，物品自己的提示框不能被顶掉）。
   - `refresh()`（`containerTick`）与 `boundGeneration()` 这类屏幕自己的节奏钩子。
8. **不引入** AUI 的 `ae` / `ore` 主题、不用它的 `Tooltip.bind`（这两条本来就有）、不改 AUI 的菜单类型注册。
9. **页面的槽位标记不用改**：`<slot slot-index>` 与 `<container id>` 保持原样，`normalizeTemplate` 会补 `<item>`；`repeat` 由 `ContainerExpander` 在文档刷新期展开（子元素一起克隆）。

## 4. 槽位顺序与映射（迁移时逐菜单改）

旧的屏幕用 `bindCells(容器 id, 本地下标 → 菜单下标)` 手写映射，新的映射由布局给，因此**菜单槽位顺序必须改成按容器分组**（`baseIndex` 连续区间），并且与页面 `<container id>` 的 `slot-index` 一一对上：

| 菜单 | 页面容器（`slot-index`） | 迁移后菜单槽位顺序 |
| --- | --- | --- |
| `ChequeTableMenu` | `currency` 0..14、`cheque_in` 0、`cheque_out` 0、`inventory` 原版背包下标 | currency(15) → cheque_in(1) → cheque_out(1) → 玩家背包 |
| `StationMenu` | `costs` / `rewards` 各 0..11、`display`/`stock`（店主页）、`inventory` | costs(12) → rewards(12) → display(1) → stock(1) → 玩家背包（**原来是与 rewards 交错**） |
| `PlayerTradeMenu` | `offer` 0..19、`partner` 0..19、`inventory` | offer(20) → partner(20) → 玩家背包（**原来是与 partner 交错**） |
| `SpiritCraftingMenu` | `crafting` 0..8、`result` 0、`inventory` | result(1) → crafting(9) → 玩家背包 |
| `ExchangeStationMenu` | `input`、`result`、`inventory` | input → result → 玩家背包 |
| `ForgingMenu` | `blueprints` 0..2、`tools` 0..2、`inputs` 0..11、`output` 0、`inventory` | 与旧顺序一致（蓝图 → 方法 → 材料 → 产物 → 玩家背包） |
| `TalismanWorkstationMenu` | `station` 0（**只有 1 格符纸**，页面实测；产物不从容器走）、`inventory` | 与旧顺序一致 |
| `AlchemyFurnaceMenu` | `machine` 0..N、`player_inventory` | 与旧顺序一致 |

其它一起改的：`quickMoveStack` 按新顺序重写（原来靠"偶数/奇数"或硬编码范围的必须改），页面上只做展示的容器（交易对方的出价、交易站的展示/库存、丹炉的机位格）照旧用自己的槽位类（`EconomySlots.Display` 等）由 `ContainerDataSource.createSlot` 造。

## 5. 风险、削弱与不做

- **几何就绪判据被削弱**：旧判据是"每个格子都落在面板矩形里"，现在只剩"面板实时矩形非退化且在视口内"（`AGENTS.md` 里那段"不要按帧数放行、要内容对账"的口径随之作废，改由 AUI 的 `SlotDataBinder` 负责）。失败形态从"物品画到屏幕角上"降级为"屏幕自己那几笔 Java 命中判定第一帧可能用错矩形"。
- **数据同步仍归我们**：`ApricityContainerMenu` 没有 `ContainerData`，所以进度/温度这类数值仍由各菜单自己 `addDataSlots`（子类可以调，`AbstractContainerMenu` 的方法是 `protected`），屏幕照旧读。
- **AUI 的菜单类型仍写在 `super` 里**：`getType()` 覆写只保证开屏包与屏幕工厂走我们的类型；`ApricityContainerMenu` 内部不读自己的类型，所以没有第二处影响（这条随 AUI 版本变化要重核）。
- **实机未验**：本次只到编译；槽位悬停、拖拽、快速合成、浮动物品、tooltip 这些行为都换成 AUI 的实现，必须在测试客户端里逐界面过一遍。

## 6. 落地记录

2026-10-03 落地。改动的形状与设计一致，补充/偏离的地方如下。

**宿主与接口**

- `screen/aui/AuiWrappedScreen` 去掉 `auiSetDocument`，`auiInit` 拆成 `auiPreparePage(path)`（播种 + 预热，必须在 `Document.create` 之前）与 `auiPageOpened()`（命名页面、按预热结果重启 `StyleHold`、绑契约）；`auiClose()` 只剩"丢绑定"。`State` 多一个 `stylesPrepared`。
- `AuiScreen extends ApricityScreen`：构造器收页面路径（原来是 `Component` 标题 + 抽象的 `pagePath()`），`init` 三步走；`tick()` 推 `auiTick`；`extractBackground` 仍画 `AuiStyles.extract`（灰底）。
- `AuiContainerScreen<T extends ApricityContainerMenu> extends ApricityContainerScreen`：只留页面契约、`panel` + 每帧读出的 `panelLeft/Top/Width/Height`（顺便写进 `leftPos/topPos`，AUI 的槽位绑定与悬停都按这一对算）、`slotsDrawn()`（弱化为"面板矩形可用"）、`hasClickedOutside`（按面板矩形）、`tooltip(Element, Supplier)` 锚点（在 `super.extractRenderState` 之后登记）、`refresh()`/`containerTick()`、`byIdPrefix`。**`menu` 字段用同名字段遮蔽**父类那个 `ApricityContainerMenu` 类型的字段，好让 9 个界面的 `this.menu.xxx()` 一行不用改。
- **删掉**：`parkSlots` / `syncGeometry` / `reportBadCell` / `waitForGeometry` / `cells` / `bindCells` / `bindInventoryCells` / `cellsOf` / `inventoryMenuIndex` / `slotIndexOf` / `ITEM_INSET` / `extractSlot` 与 `isHovering` 覆写 / 鼠标三件套门控 / 几何告警。

**菜单**

- 新类 `screen/menu/PageSlots`：`of(pagePath).container(id, container, slotFactory)….player(playerContainerId).build()` → `Layout{SlotLayout, Map<String, ContainerDataSource>}`；第一个声明的容器当 primary；玩家池固定 36（AUI 的 `PlayerInventorySlotOrder` 与原版背包下标一致）。
- 8 个菜单都用"公开构造器 → 私有 Setup 构造器"（`super(containerId, inventory, layout, sources, Map.of(), null)`）；**都覆写了 `getType()` 返回自己的菜单类型**。
- **客户端屏幕注册走了擦除**：`event.register(MenuType<M>, ScreenConstructor<M, U>)` 的界要求 `U extends AbstractContainerScreen<M>`，而每个界面都继承 `ApricityContainerScreen`（`AbstractContainerScreen<ApricityContainerMenu>`），这个界写不出来，所以 `MxtRenderers.page(...)` 里做了一次 raw 转换（`MenuType` / `MenuType` 的 `ScreenConstructor`），注释说明了"客户端拿到的就是自己那个类型的工厂造出来的菜单"。`MxtMenus` 的类型参数保持**自己的菜单类**（JEI 的 `addRecipeTransferHandler(Class<C>, MenuType<C>, …)` 因此不用改）。
- 槽位顺序：**PlayerTrade 与 Station 的"交错"被取消**（各自改成两段连续区间，含义变了但菜单下标数字不变：24 与 40 两处分界恰好重合），其余 6 个菜单的槽位顺序本来就与容器分组一致，`quickMoveStack` 多数不用改；需要真实菜单指针的槽位（`EconomySlots.Ghost`、`ForgingMenu.MachineSlot`、`AlchemyFurnaceMenu.PartSlot`）各自用迟绑定（Station 走覆写 `protected Slot addSlot(Slot)` 换掉占位槽，另两个让槽位持有 Setup、构造完再 `bindMenu/attach`）。
- 页面路径从屏幕搬到菜单（丹炉按 `View.getSlug()`、站台按 `Mode`）。

**同时删掉的基建**

- `src/main/resources/META-INF/accesstransformer.cfg` 与 `build.gradle` / `neoforge.mods.toml` 里的登记一起删除：我们这边再没有一处写 `Slot.x/y`（ApricityUI 自带同样的两条规则，运行时字段照样可写）。
- 页面与样式表里描述旧机制的注释（`ITEM_INSET`、"Java 写 Slot.x/y"、交错映射、"不进 bindCells"）已按新形状改写（只动注释）。
- 另外把 jar 里的页面从 `assets/mxt/apricity/mxt/<页名>/` 上移成 `assets/mxt/apricity/<页名>/`（`AuiPages.seed` 里去掉命名空间那一段；播种目标 `<gameDir>/apricity/mxt/<页名>/` 不变）。

**验证到什么程度**

- `.\gradlew.bat compileJava compileTestModJava --console=plain` → **BUILD SUCCESSFUL**。
- **未实机**：槽位悬停 / 拖拽 / 快速合成 / 浮动物品 / 槽位提示框 / 每页的页面写入全部换成 AUI 的实现，必须在测试客户端里逐界面过一遍（`runTestClient`）。
- 未跑探针（这次没有可静态验的东西）。

**开放项**

1. 几何就绪判据只剩"面板实时矩形可用"（设计稿 §5），第一帧的屏幕自绘命中判定可能用错矩形一瞬——要更严就得自己再读一遍槽位几何，那正是本次要删的东西。
2. `ForgingMenu` 的 `MACHINE_PITCH` / `SLOT_TOP` / `INPUT_X` / `INVENTORY_X` / `INVENTORY_Y` / `HOTBAR_Y` 在迁移后没有 Java 读者了（页面注释仍说"坐标照抄菜单常量"）：要么删掉并让页面自带坐标，要么继续留着当参考。
3. 三个迟绑定槽位（Ghost / MachineSlot / PartSlot）只在源码层面核过"构造期不会调用它们"，未实机。
4. 页面里多个容器都写 `primary="true"`，而 `PageSlots` 只标第一个——对已有菜单无影响（都覆写了 `quickMoveStack`），但这是一处口径。
5. 丹炉的机位格提示改成取"容器的直接子元素"（原来取"以该容器为最近祖先的全部 slot"）：这四页没有嵌套容器，两者同集合；将来页面若在 `machine` 里嵌容器会不同。

## 7. 追记：页面改从资源包读，不再播种（2026-10-04）

用户点名：**ApricityUI 自己就能读资源包内容，别再把页面种到游戏目录**（[资源管理](https://doc.sighs.cc/ApricityUI/guide/resource-manager)：资源包层就是 `assets/apricityui/apricity/...`，打进模组 jar，不可写）。

**为什么原来那个 jar 目录打不开**（读 1.2.6 源码核过，`Loader` / `ClientLoader` / `MultiPackResourceManager`）：

- **取内容**两条路都写死 `apricityui` 命名空间：`ClientLoader.getResourceStream(path)` → `AuiServices.resources().openResource("apricity/" + path)` → `parseLocation` 没有冒号就 `Identifier.fromNamespaceAndPath("apricityui", …)`；`Loader.getResourceStream` 的 classpath 兜底也拼 `assets/apricityui/apricity/`。
- **扫描**那一侧相反：`ClientLoader.loadFromResourcePack()` 用 `ResourceManager.listResources("apricity", …)`，而 `MultiPackResourceManager.listResources` 是**遍历每个命名空间**的 `FallbackResourceManager`，返回的 Identifier 带各自的命名空间、`ResourceService.listResourcePaths` 却只把 `apricity/` 这一段从 path 上剥掉（命名空间丢了）。
- 合起来就是：`assets/mxt/apricity/<页名>/` 这类页面**会被列进模板表**（路径还少了命名空间），内容**永远按 `apricityui:apricity/<路径>` 取**——要么取不到，要么取到别人同名的文件。所以"打包即用"是能做到的，但**只能放在 `assets/apricityui/apricity/` 下**。

**改了什么**

- 29 个页面文件从 `src/main/resources/assets/mxt/apricity/<页名>/` 移到 `src/main/resources/assets/apricityui/apricity/mxt/<页名>/`。**逻辑页面路径一个都没变**（相对 `apricity/` 仍是 `mxt/<页名>/…`），所以页面里的相对链接（`../common/theme.css`）与 Java 侧的 `AuiPages.page(...)` 一行都不用改；路径里保留 `mxt` 那一段是为了不与 AUI 自带的 `devtools/`、`screens/` 等撞名（扫描出来的相对路径不带命名空间）。
- `AuiPages` 从"页面清单 + 播种 + 预热"缩成**页面路径常量与助手 + 打开前那道 `warmUpStyles`**：删掉 `Folder` / `FOLDERS` / 各页文件清单 / `PAGES` / `pages()` / `directory` / `bundledFolder` / `seed` / `seedAll` / `reloadPending` / `RELOAD` / `onRegisterReload` / `onClientTick` / `warmUpAll` 与 `@EventBusSubscriber`。类注释现在只讲那条硬约束（资源包层只有 `apricityui` 命名空间能打开、游戏目录是玩家覆盖层）。
- 预热不用自己排了：AUI 的 `ClientLoader.reloadResourcesInternal` 每趟资源重载都 `HTML.scan()` + `prepareTemplates()` + 对每个模板 `warmUpTemplateStyles(…)`，它扫的就是资源包那一层，我们的页面跟着一起预热，于是原来那个"重载后第一个 tick 上 `seedAll()` + `warmUpAll()`"的钩子整段删除。
- `AuiWrappedScreen.auiPreparePage(path)` 只剩"记页面名 + `AuiPages.warmUpStyles(path)` 并把结果交给 `StyleHold`"（§6 上文那半句"播种 + 预热"现在只剩预热）。`warmUpStyles` 保留的兜底是**模板不在 AUI 表里**时 `HTML.reload` 读它一遍——`Document.create` 只认模板表，这一步不做的话页面会直接落到红字兜底——以及预热失败时白等那两拍。
- 上一版播种在 `run-test-client/apricity/mxt/` 留下的 29 份副本已删（与源码只差注释，是纯残留）。

**验证到什么程度**

- `.\gradlew.bat compileJava compileTestModJava processResources --console=plain` → **BUILD SUCCESSFUL**；产物里 `build/resources/main/assets/apricityui/apricity/mxt/…` 29 个文件，旧的 `assets/mxt/apricity/` 已不在产物中。
- **未实机**：`HTML.scan()` 是否真扫到资源包里的这些页面、dev 目录层（`src/main/resources/assets/apricityui/apricity`，`Loader.getDevResourceRoots` 从 `run-test-client/` 往上走一层就能找到）是否照样优先于资源包、首次打开的**第一帧**有没有样式（§8 之后没有等待层了，样式没赶上就会看见没样式的 DOM）——都只能在 `runTestClient` 里看（看 `[AUI Resource] scanned extension=html loaded=`、`[AUI HTML] template resource is missing`、以及 F10 资源管理器里这些页面的层标为 RESOURCE_PACK 还是 DEV_FOLDER）。

**开放项（接上文编号）**

6. **老安装/老开发目录的残留会盖住 jar**：`<gameDir>/apricity/mxt/` 里若有上一版播种出来的副本，本地层优先级高于资源包，必须先删掉一次才看得到 jar 里的新页面（本仓库的 `run-test-client/apricity/mxt/` 已删）。若 1.2.6 前后发过带播种的包，需要在玩家文档里加一句"删掉 `<实例>/apricity/mxt/`"。播种没了以后这个目录**只剩"玩家自己想覆盖"这一种含义**。
7. 玩家侧"不重打包就能改页面"仍在（往 `<gameDir>/apricity/mxt/…` 丢同名文件），但它不再是我们页面的副本，而是纯覆盖层；`docs/guide/java/screens.md` 与 `docs/数据包格式.md` 的丹炉一节已按这个口径改写。
8. `AuiPages.warmUpStyles` 现在与 AUI 自带的那趟预热**重复**（通常是一次纯缓存命中）：留着是为了"模板不在表里"（`HTML.reload` 那一步，不做的话 `Document.create` 找不到模板）这一种兜底。

## 8. 再追记：删掉 `StyleHold`（2026-10-04）

用户点名把 §7 开放项里那层"每屏状态机"删掉。**删除的东西**：`AuiPages.StyleHold` 与 `HOLD_TICKS`、`AuiWrappedScreen.auiTick()` 与 `auiReadyToDraw()`、`State.stylesPrepared` 与它那份 `styleHold` 字段、`auiPageOpened()` 里的 `restart(...)`、`AuiScreen.tick()`（只剩转发，整段删）、`AuiContainerScreen.containerTick()` 里那句 `auiTick()`、以及五处 `if (!auiReadyToDraw()) return;`（容器宿主一处 + 轮盘 / 轮盘配置 / 人物信息 / 结构预览各一处）与它们的注释。

**理由**：它防的是"样式表还在工作线程上、文档先用 UA 的 `global.css` 画了一两帧"（`StyleAsyncHandler.attach` 对没预热的样式表会 `queueTask` + `submitWorker`，下一拍才 `applyOnMainThread`）。但 §7 之后这份预热由 ApricityUI 每趟资源重载对**每个扫到的模板**做掉，`stylesPrepared` 只会在"预热失败"这一种残路上为假，为一层几乎恒不生效的等待留着跨五个类的 tick 钩子 + 状态字段不划算。

**保留的东西**：`AuiPages.warmUpStyles`（`auiPreparePage` 里调，返回值没人要了，已改成 `void`）——它仍是"建文档前把样式表编译好"的同步快路径，也是"模板不在 AUI 表里"时唯一能让页面打开的一步。代价：样式表万一真没赶上，第一两帧会画出没样式的 DOM（格子堆在文档原点），这正是被删掉的那层挡的东西；实机若在**首次**打开某一页时看到这种一闪，就说明 AUI 那趟预热漏了我们的页面，那时该查的是扫描而不是把这层加回来。

**验证**：`compileJava compileTestModJava` → BUILD SUCCESSFUL；全仓已无 `StyleHold` / `auiTick` / `auiReadyToDraw` / `stylesPrepared` 引用。**未实机**。

## 9. 再追记：删掉缺页兜底红字（2026-10-04）

用户点名："这个问题一般是开发者才遇到，所以直接这个提示删了。"（指 §3 里保留的那条兜底——页面缺失 / 页面不合契约时在屏幕中央画一行红字。）

**先核过 AUI 自己会不会兜**（读 1.2.6 源码）：**不会**。模板缺失时 `DocumentRegistry.create` 只打 `[AUI Document] cannot create document: template is missing` 并返回 null，`ApricityScreen.init` 只在**第一次** init 打一条 INFO（`doc=<null>`），容器宿主则直接 `return`（`slotBinder` 保持 null，槽位交互全废），两个宿主都不画任何东西、也不弹 toast（`ToastManager` 的调用点都在 devtools / 资源管理器里）；"页面不合契约"对 AUI 根本不存在——`getElementById` 找不到就返回 null，它不记日志、照常把页面画出来。所以那行红字一直是唯一的可见信号，删掉它就等于**这两种故障对玩家完全静默**。

**删除的东西**：`AuiWrappedScreen` 的 `pageMissing()` / `pageInvalid(String)` / `extractPageError(...)`、`State.pageError` / `errorLines` / `errorWidth`，以及五处调用点（容器宿主在 `extractBackground` 里那次、轮盘 / 轮盘配置 / 人物信息 / 结构预览各一次）；`WheelMenuScreen` 覆写的两条文案、`InformationPanelScreen` 那个只为画这行字而覆写的 `extractBackground` 也一起删。lang 里随之无用的四个键（`screen.mxt.page.missing` / `.invalid`、`screen.mxt.wheel.template_missing` / `.invalid`）从两份文件里删掉（键集合仍然一致，1091 条）。

**留下的东西**：`auiPageOpened()` 在文档为 null 时直接返回（AUI 已经 ERROR 过原因，不重复报）；`auiRebind()` 接住 `NoSuchElementException` 后走 `invalidate(missing)`——丢掉半份绑定、**保留当前文档代数**（否则 `auiPageWritable()` 会每帧重试并刷屏）、打**一条 WARN**：`Page <名> does not match its contract: <缺的那一项>`。也就是说：**作者看日志，玩家看空屏**。这与仓里"页面契约失败别把输入一起弄坏"的口径一致（`auiBound()` 仍然答 false，输入闸门照旧关着）。

**验证**：`compileJava compileTestModJava` → BUILD SUCCESSFUL；全仓已无 `extractPageError` / `pageMissing` / `pageInvalid` / `pageError` 引用；两份 lang 键集合一致（1091）。**未实机**。
