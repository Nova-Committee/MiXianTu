# 63 炼丹界面改用 ApricityUI 设计

> 状态：已落地（2026-09-30）。取代 [`58_丹炉部件分工与异火供热设计.md`](58_丹炉部件分工与异火供热设计.md) §5 的「LDLib 原生可编辑界面」。
> 一句话：四个丹炉页面从 LDLib2 的 `.ui.nbt` 模板改成 ApricityUI 的 HTML/CSS 页面，**菜单、网络包、状态机一行不动**——换掉的只有「谁来画这块面板」。

## 一、为什么要换、为什么不换菜单

旧实现的形状是：布局全在 `.ui.nbt` 模板里，Java 只按稳定 ID 抓节点（`AlchemyUiTemplates` + LDLib 的 `UITemplate`/`UI`/`IModularUIHolderMenu`），并且把游戏目录下的那份模板注册进 LDLib 的编辑器资源管理器，让作者能在游戏内改布局。

新实现换成 ApricityUI 的 HTML/CSS 页面，理由是作者侧便宜得多：页面是文本、能热重载（`[debug] autoReload`）、能用 DevTools（F12）、能引主题，不必进游戏点编辑器。

**菜单侧刻意不动**：`AlchemyFurnaceMenu` 仍然是原版 `AbstractContainerMenu`，四个视图、槽位顺序、`PartSlot.mayPlace` / `mayPickup`、`moveIntoMachine`（不吃原版合并循环）、温度 `DataSlot` 纪元、`AlchemyFurnaceView` 的 S2C 快照与 `AlchemyActionC2SPayload` 全部原样保留。换成 ApricityUI 自己的容器菜单（`ApricityUI.menu(player, path).bind(b -> b.blockEntity(pos).player())`）会丢掉三样东西，所以没有走那条路：

1. 槽位规则只能表达成数据源上的 `FilterUtil` / `IItemHandler`，`canPlaceFire` / `canTakeFire` / 各仓 `canPlaceItem` 这套语义要重写；
2. `quickMoveStack` 会换成 ApricityUI 的「主容器优先 + 原版合并循环」，而这里恰恰是刻意避开原版合并的地方；
3. 温度、进度、状态、目标炉温这些**非槽位数据没有通道**——ApricityUI 的页面只能拿到槽位内容，`<container>` 之外没有服务端变量绑定。

保留菜单之后，Java 侧的活只剩「把状态写进 DOM、把点击读回网络包」：温度纪元/草稿/拒绝那套状态机原样搬过来，只是 `Label.setText` 变成 `Element.setTextContent`。

## 二、页面放在哪：种到游戏目录

ApricityUI 1.2.5 的页面解析顺序是（`Loader.getResourceStream` → `ClientLoader.getResourceStream`）：

1. 开发目录 `src/main/resources/assets/apricityui/apricity/`（从游戏目录向上找）；
2. 开发工程根（`build.gradle` / `.git` 所在目录）下按路径后缀探测；
3. `<游戏目录>/apricity/<逻辑路径>`；
4. classpath `assets/apricityui/apricity/<逻辑路径>`。

第 4 条**只认 `apricityui` 这一个命名空间**（`ResourceService.parseLocation` 对没有冒号的路径一律补 `apricityui`），所以别的模组把页面打在自己的 `assets/mxt/apricity/…` 里是打不开的——`listResourcePaths("apricity", …)` 能列出跨命名空间的文件，`openResource` 却只会去 `apricityui` 里找。资源包层同理。

于是走第 3 条：客户端启动时把缺失的页面从 jar 里种到 `<游戏目录>/apricity/mxt/alchemy/`，已有文件不覆盖（与旧 `.ui.nbt` 的播种口径一致）。这样开发环境与发行版同一条路径，作者改的是游戏目录里那一份，热重载也照常工作。代价与新版本升级时页面不会覆盖旧副本——这与旧实现相同，页面本身有必需节点校验兜底。

## 三、页面契约

四个页面：`monitor.html`、`main_input.html`、`auxiliary_input.html`、`output.html`，共用一份 `alchemy.css`，引 `apricityui/theme/ae/1.0.0.css`（随 ApricityUI 分发的像素风主题：`.area` 面板、`.button`、`input`、`slot` 贴图，尺寸正好是 18×18 的经典槽位）。

- **槽位是 `<container>` + `<slot>`**：`#player_inventory`（`bind="player"`，两条 `repeat` 槽位按 `9..35`、`0..8` 展开成 36 格，正好是原版背包三行 + 快捷栏）、`#machine`（`primary="true"` + `size=N`，一格一排，N 由各页自带 `<style>` 决定宽度与 left）。`slot-index` 就是原版背包序号 / 机器槽位序号，Java 按 `menu.menuIndex(id)` 映射到菜单槽位。
- **Java 每帧读 DOM 几何**：`Position.of(<slot>)` → 换算成 GUI 坐标 → 写 `Slot.x/y`（物品是 16×16，落在 18×18 格里，所以要 `+1`）。所以页面想怎么排就怎么排，Java 不写死坐标。
- **物品由原版画**：本屏幕不是 `ApricityContainerScreen`，`AbstractContainerScreenMixin` 不管它，`extractSlot` / 悬停高亮 / 手上那摞 / tooltip 全走原版。
- **非槽位控件**：`title`、`quality`、`temperature`、`limit`、`status`、`progress` + `progress_fill`、`target`（`<input>`）、`apply` / `start` / `abort`（`.button`）。文本由 Java 写 `textContent`（`Component.getString()`，颜色取组件的顶层颜色写进 `color`），按钮点击用 `Element.addEventListener("click", …)`，目标炉温读 `input.getValue()`，tooltip 用 `Tooltip.bind(...)`。

必需节点缺失时**一个槽都不绑**，并按旧行为在屏幕中央画一行 `screen.mxt.alchemy.template_invalid`（缺哪个写哪个）；`isHovering` 在没绑定时恒假，所以不会有幽灵槽位接管点击。

## 四、五个反直觉点（都写进了代码注释）

1. **页面不能写 `aui-mouse-events: intercept`。** 写了之后，只要鼠标下命中的 DOM 元素存在，ApricityUI 就会 `consumeNative`，`Client.mouseButton` 随即取消原版事件——原版 `Screen.mouseClicked` 收不到，槽位点击、拖拽、双敲全部失效。我们的按钮点击**不依赖**这个 meta（`MouseEvent.tiggerEvent` 无论 meta 如何都会派发 DOM 事件），而槽位必须让原版拿到事件，所以四个页面只写 `aui-viewport`，不写 `aui-mouse-events`。代价是同一个点击既进 DOM 也进原版：按钮都在面板内、不在任何槽位上方，原版那边算「面板内、无槽位」→ 什么也不做。
2. **层级靠 `AuiLinkedScreen` + 自己提交 PIP。** 本屏幕实现 `AuiLinkedScreen`，在 `extractRenderState` 里**先** `ApricityGuiLayers.submitUi(graphics)` 再 `super.extractRenderState(...)`：26.1 的 `GuiRenderState` 按包围盒把后提交、且与已有元素相交的内容放到上一层，所以全屏的页面在下、物品与 tooltip 在上。若不做这一步，`Client.drawScreen` 会在 `ScreenEvent.Render.Post`（也就是原版物品之后）提交页面，面板会把物品盖掉。
3. **页面撑满视口要用视口单位，不能用百分比。** `Size` 只在"包含块高度明确"时才应用百分比高度（`!isPercent(height) || definiteParentHeight != null`），而 ApricityUI 的 `<html>` 没有显式高度，于是 `body { height: 100% }` 整条被忽略、body 高度塌成内容高度，flex 的交叉轴就没有剩余空间可分——表现是面板**只水平居中、贴着顶边**。写 `height: 100vh`（`gui` 模式下 `100vh` 就是整块 GUI 视口；ApricityUI 自己的 devtools 页也是这么写的）。
4. **重写 `extractBackground` 必须调 `super`。** 26.1 里 `AbstractContainerScreen` 已经不再画任何背景，那块半透明暗化渐变（`Screen.extractTransparentBackground`，游戏内 UI 走的就是这一支）只在 `Screen.extractBackground` 里画。本屏幕为了画"页面缺节点"的报错重写了它，第一版直接 `return`，于是玩家看到的就是**面板后面全透明**；现在先 `super.extractBackground(...)` 再画报错。
5. **开界面那一帧不能读槽位几何。** ApricityUI 不在 `Document.create` 时算布局：元素的位置与大小是**绘制阶段**才提交的（`LayoutCommit.commit` → `Rect.createAndCache` → `Position.forRender`，提交结果按 `rectDependency` 盖戳，`RenderElement.getCommittedRectIfValid()` 才回答得出"这份几何现在算不算数"），`Position.of` 只是按需计算 + 缓存。而本屏幕的 `extractRenderState` 跑在那一帧的绘制**之前**：第一遍读到的还是布局前的缓存值（面板偏移拿不到 flex 居中、槽位在文档原点附近），Java 又把它当成屏幕坐标减去 `leftPos/topPos` 写进 `Slot.x/y`，物品就被画到屏幕左上角闪一帧。所以绑定后**第一遍一律不读**（`awaitingLayout`），之后按 `getCommittedRectIfValid()` 继续等（上限 `LAYOUT_WAIT_LIMIT` 帧，避免一个永远不合法的戳把物品永久藏住）；同时菜单建的槽位坐标都是 `(0, 0)`，绑定/清绑时先把它们挪到面板外（`parkSlots`），这样"还没读到几何"这个状态本身就不会画出任何东西。

## 五、与旧实现的差异

- 少了 LDLib2 依赖：`build.gradle`、`gradle.properties` 的 `ldlib2_version`、`neoforge.mods.toml` 里的必需前置一起换成 `apricityui`。
- 少了「LDLib 编辑器里改布局」这条路径，换成文本页面 + 热重载 + 游戏内 F12 DevTools。
- 画布尺寸保持旧值：监控 188×211，三个仓页 188×146（`box-sizing: border-box`，AE 主题的 `.area` 自带 7px 边框，所以外框是 202×225 / 202×160）。旧模板里那些坐标（标题 7,7、背包 13,y、机器槽 18px 一格、槽间距 4px、按钮 18 高）都按同样数值搬到 CSS。
- 文本渲染是 AWT 字体（ApricityUI 1.2.5 的 `Font`/`FontDrawer` 用 `java.awt.Font`），不是原版字体图集；1.2.5 里**没有** `aui-font-mode` 的实现（SKILL 文档描述的是更新分支），所以页面不写那个 meta。
- **`Slot.x` / `Slot.y` 在 26.1 是 `final`**，写槽位坐标需要访问转换器：本模组自带 `src/main/resources/META-INF/accesstransformer.cfg`（`public-f` 两条）并在 `build.gradle` 的 `accessTransformers` 与 `neoforge.mods.toml` 的 `[[accessTransformers]]` 里登记；ApricityUI 自己那份 AT 有同样两条，规则相同、合并无冲突。**删了这份文件编译就过不去。**
- 依赖口径：`ldlib2` 从必需前置换成 `apricityui`，并且是 `side = "CLIENT"`——专用服务端不画界面，不必装。

## 六、验证

- 编译：`gradlew.bat compileJava compileTestModJava processResources processTestModResources` 通过。
- 实机（2026-09-30，用户自己看的第一次）：页面被正常解析、面板画了出来，但报了两个问题——**面板只水平居中、贴顶边**，以及**面板后面没有那块半透明暗化遮罩**。两处都定位到根因（见 §4 第 3、4 条）并已修。
- 实机（同日第二次）：布局与遮罩正常，但**开界面时物品在屏幕左上角闪一帧**。根因见 §4 第 5 条，已加"第一遍不读几何 + 未读到几何时把槽位挪到面板外"两道处理；修完没有再进游戏确认。
- 仍未实机确认的部分：槽位是否落在槽框正中（`ITEM_INSET` 那一步）、背包 4 行是否 18px 无缝、按钮与输入框的点击与文字基线、tooltip、热重载。`research/58` §8 那份实机证据属于旧 LDLib 版，不能当作新版的验收。
- 改完页面之后要在游戏里看到新版本，得先删掉 `<游戏目录>/apricity/mxt/alchemy/` 里那几份旧副本：播种口径是"缺了才写、已有不覆盖"，所以升级不会覆盖作者改过的那一份。
- 日志要看的东西：`[AUI HTML]` / `[AUI CSS]` / `[AUI JS]` 前缀（页面解析错误）、`[AUI Resource] scanned extension=html`（页面是否被扫到）。
