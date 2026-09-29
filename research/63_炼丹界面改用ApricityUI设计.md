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

四个页面：`monitor.html`、`main_input.html`、`auxiliary_input.html`、`output.html`，共用一份 `alchemy.css`，引 `apricityui/theme/ore/ore.css`（ApricityUI 自带的两套主题之一，见下一节）。

- **槽位是 `<container>` + `<slot>`**：`#player_inventory`（`bind="player"`，两条 `repeat` 槽位按 `9..35`、`0..8` 展开成 36 格，正好是原版背包三行 + 快捷栏）、`#machine`（`primary="true"` + `size=N`，一格一排，N 由各页自带 `<style>` 决定宽度与 left）。`slot-index` 就是原版背包序号 / 机器槽位序号，Java 按 `menu.menuIndex(id)` 映射到菜单槽位。**背包三行无缝、与快捷栏之间那条 4px 的缝**（旧模板里快捷栏 68 减背包末行 46+18）由 `grid-template-rows` 的一条空轨道给出、快捷栏按 `slot-index` 点名落到第 5 行——`container` 默认就是 grid（用户代理层 `global.css` 的 `container { display: grid; gap: 2px }`），所以槽位排布是网格的事，不是块级流。
- **Java 每帧读 DOM 几何**：`Position.of(<slot>)` → 换算成 GUI 坐标 → 写 `Slot.x/y`（物品是 16×16，落在 18×18 格里，所以要 `+1`）。所以页面想怎么排就怎么排，Java 不写死坐标。
- **物品由原版画**：本屏幕不是 `ApricityContainerScreen`，`AbstractContainerScreenMixin` 不管它，`extractSlot` / 悬停高亮 / 手上那摞 / tooltip 全走原版。
- **非槽位控件**：`title`、`quality`、`temperature`、`limit`、`status`、`progress` + `progress_fill`、`target`（`<input>`）、`apply` / `start` / `abort`（`.button`）。文本由 Java 写 `textContent`（`Component.getString()`，颜色取组件的顶层颜色写进 `color`），按钮点击用 `Element.addEventListener("click", …)`，目标炉温读 `input.getValue()`，tooltip 用 `Tooltip.bind(...)`。

必需节点缺失时**一个槽都不绑**，并按旧行为在屏幕中央画一行 `screen.mxt.alchemy.template_invalid`（缺哪个写哪个）；`isHovering` 在没绑定时恒假，所以不会有幽灵槽位接管点击。

### 皮肤与字体：ore 主题 + 原版字体

ApricityUI 1.2.5 一共只有两套主题（jar 里的样式表就三份：`global.css` 用户代理层 + `theme/ae/1.0.0.css` + `theme/ore/ore.css`；26.1 分支最新的 1.2.5 就是它）。第一版用的是 `ae`——40 行、只管 `.area`（九宫格面板）/`.button`/`input`/`slot` 四件事的旧皮，不带字体；2026-09-30 换成 ore：

- 页面引 `apricityui/theme/ore/ore.css` 并把 `<body>` 标成 `class="ore-theme"`——ore 的每条规则都以 `.ore-theme` 为作用域，不带这个类等于没引。
- ore 是**网页尺度**的完整组件库（按钮 42px、槽位 44px、字号 16px、根上 `line-height: 1.5`、`body { min-width: 320px }`），容器尺度要逐条压回来。**特异性是硬约束**：凡与 ore 冲突的规则都得写成 `body.ore-theme X` 去压它的 `.ore-theme X`，只写 `.input` / `.progress` 这种单类选择器会被它整条盖掉；`min-width: 320px` 在 GUI 缩放 5 以上时比视口还宽、会把面板推得偏右，所以显式写 `min-width: 0`。
- 面板用 ore 的 `.panel`（3px 描边 + 石面 + `inset 2px 2px` 高光），进度条用 `.progress`/`.progress-bar` 压到 6px，按钮用 `.button`（start 就是它的绿）+ `.button-secondary`（apply，紫）/ `.button-danger`（abort，红）。禁用态直接用 ore 自己的 `.button[disabled]`——Java 的 `Element.setDisabled` 写的正是 `disabled` 属性；从 42px 压到 18px 时连同 `:active` 的内边距与阴影一起压，否则按下会跳回 ore 那套 6px 压边。
- **槽位不用 ore 的 `.slot`**：它是 44px 的 class 选择器，而机器槽由 `size=N` 经 `ContainerExpander.appendAutoSlots` 生成、拿不到 class；改成按 `slot` 元素写，把 ore 的凹面格压成 18px（1px 描边 + 1px 内阴影，正好给 16px 物品留出格心，物品仍由原版画在页面之上）。
- 字体走 AUI 的**默认字体后端**（页面上不写 `font-family` 就是它）：这样画出来的是**原版位图字体**（`FontDrawer` 见 `fontFamily` 为 `unset` 就转 `Client.drawDefaultFont` → `Minecraft.getInstance().font`，与原版界面同一套字形，中日韩走原版自带的点阵回退），ore 自带的两个像素字体（`OreRegular` = `minecraft-regular.otf`、`OreDisplay` = `minecraft-ten.ttf`）反而**不用**——它们是 `@font-face` 注册进 AWT 的，走的是"按 48px 光栅再缩放"那条路，而且 `minecraft-regular.otf` 里没有 CJK（AWT 量 `丹` 的 advance 只有 0.9、墨迹 0×0），中文会掉到 `Font` 的 `default` 兜底——也就是 **Microsoft YaHei 9px**（实测墨迹 10px 高、上升部 9.5px），比正文的拉丁字母（7px）大一圈。换回原版字体后中文与原版聊天同高，反而小了。三条硬约束记在 `alchemy.css` 里：
  1. **重置字体写 `font-family: initial`，不能写 `unset`**：`font-family` 是继承属性，而 `ComputedStyleResolver.resolveCssWideKeyword` 对继承属性上的 `unset` 取的是"继承父级"（与 `inherit` 同路），只有 `initial` 会回到 `INITIAL_VALUES` 里的 `font-family: unset`；
  2. **这个后端下 `font-size` 是缩放不是像素**：`Text.FONT_SCALE_BASE = 9`、`defaultFontScale() = fontSize / 9`，所以 9px = 原版 1×、18px = 2×，只有 9 的整数倍才是整数倍缩放（非整数倍会把位图非整数缩放、笔画会掉），正文与标题统一 9px；
  3. **它不像光栅路径把墨迹在行框里居中**：原版字形从行框顶画（`renderedAscent` 对默认后端取 `fontSize * 0.8` = 7.2px），所以文本盒统一 `display: flex; align-items: center`，靠 flex 直接文本节点的交叉轴居中（`Flex.resolveCrossOffset` = (可用交叉轴 − 行框)/2）补回那 1px。输入框不用管：`Input` 自己按 `(内容高 − lineHeight) / 2` 居中，光标与选区高度也取 `lineHeight`，所以那里 `line-height: 9px` 就是"文字与光标的高度"。
  - 原版字体**没有阴影**（`drawInBatch` 的 `dropShadow` 传的是 `false`），AUI 也没有实现 `text-shadow` 属性，所以按钮上原来那条 `text-shadow` 是死代码，已删。
- 颜色一律写成 `var(--token, 兜底值)`：ore.css 没解析到时退回 ore 自己那套默认色，面板不会变成透明。

## 四、七个反直觉点（都写进了代码注释）

1. **页面不能写 `aui-mouse-events: intercept`。** 写了之后，只要鼠标下命中的 DOM 元素存在，ApricityUI 就会 `consumeNative`，`Client.mouseButton` 随即取消原版事件——原版 `Screen.mouseClicked` 收不到，槽位点击、拖拽、双敲全部失效。我们的按钮点击**不依赖**这个 meta（`MouseEvent.tiggerEvent` 无论 meta 如何都会派发 DOM 事件），而槽位必须让原版拿到事件，所以四个页面只写 `aui-viewport`，不写 `aui-mouse-events`。代价是同一个点击既进 DOM 也进原版：按钮都在面板内、不在任何槽位上方，原版那边算「面板内、无槽位」→ 什么也不做。
2. **层级靠 `AuiLinkedScreen` + 自己提交 PIP。** 本屏幕实现 `AuiLinkedScreen`，在 `extractRenderState` 里**先** `ApricityGuiLayers.submitUi(graphics)` 再 `super.extractRenderState(...)`：26.1 的 `GuiRenderState` 按包围盒把后提交、且与已有元素相交的内容放到上一层，所以全屏的页面在下、物品与 tooltip 在上。若不做这一步，`Client.drawScreen` 会在 `ScreenEvent.Render.Post`（也就是原版物品之后）提交页面，面板会把物品盖掉。
3. **页面撑满视口要用视口单位，不能用百分比。** `Size` 只在"包含块高度明确"时才应用百分比高度（`!isPercent(height) || definiteParentHeight != null`），而 ApricityUI 的 `<html>` 没有显式高度，于是 `body { height: 100% }` 整条被忽略、body 高度塌成内容高度，flex 的交叉轴就没有剩余空间可分——表现是面板**只水平居中、贴着顶边**。写 `height: 100vh`（`gui` 模式下 `100vh` 就是整块 GUI 视口；ApricityUI 自己的 devtools 页也是这么写的）。
4. **重写 `extractBackground` 必须调 `super`。** 26.1 里 `AbstractContainerScreen` 已经不再画任何背景，那块半透明暗化渐变（`Screen.extractTransparentBackground`，游戏内 UI 走的就是这一支）只在 `Screen.extractBackground` 里画。本屏幕为了画"页面缺节点"的报错重写了它，第一版直接 `return`，于是玩家看到的就是**面板后面全透明**；现在先 `super.extractBackground(...)` 再画报错。
5. **读槽位几何要拿"画出来的那份布局"对账，不能按帧数放行。** ApricityUI 不在 `Document.create` 时算布局：元素的位置与大小是**绘制阶段**才提交的（`LayoutCommit.commit` → `Rect.createAndCache` → `Position.forRender`，提交结果按 `rectDependency` 盖戳），而每个元素的**偏移是记忆化的**（`RenderElement.position` 这个 `Cache` 只在样式/布局变化时清，且 `expandClear` 会把子元素的偏移一起清掉），`Position.of` 只是按需计算 + 读缓存。本屏幕的 `extractRenderState` 跑在那一帧的绘制**之前**，所以早期读到的可能是"槽位还在文档原点、面板已经居中"的**混合快照**：Java 把它当屏幕坐标减去 `leftPos/topPos` 写进 `Slot.x/y`（`slot.x = 槽位 - 面板`），槽位那一半是旧值、面板那一半是新值，物品就全被画到屏幕角上。
   第一版的处理是"绑定后先等一帧 + 按 `getCommittedRectIfValid()` 继续等、上限 `LAYOUT_WAIT_LIMIT` 帧"，但**上限一到就会照着混合快照写**——于是这个 bug 又回来了。现在改成**内容对账、永不按帧数放行**：以**面板真正被画出来的那份矩形**（`Rect.getVisualBounds()`，文档坐标）为准，要求每一个槽位的读值都落在它里面（±2px 取整余量），否则**一个字节都不写**，菜单槽位保持在面板外（`parkSlots`）并下一帧重试。判据成立的条件很硬：面板画在别处而槽位读成文档原点时，它们不可能同时成立；反过来只要页面真的排好了，`13px`、`7px` 这些内缩量都在容差之外。`GEOMETRY_WARN_FRAMES` 只决定什么时候记一条 `warn`（带上 `panelCommitted` 与面板坐标），不再决定"放弃等待"。最坏情况因此从"物品画到屏幕角上"变成"物品不画 + 日志里有一条"——后者可诊断，前者会连点击一起错位。
   **但"读不到几何"时只在"还没读到过任何一份可用几何"时才把槽位挪走**，读到过就保持上一份已对账的几何：AUI 的提示框每次跟随鼠标都会 `markDirty(element, RELAYOUT…)`，而 `Drawer` 对 `RELAYOUT` 的处理是 `e.forEachRoute(… invalidateLayoutVersion())`，也就是把 `body`/`html` 的 `layoutVersion` 一起推进——于是**整块面板的 committed rect 每帧都失效**。这时候挪槽位就是"物品（以及 hover／点击，因为 `slotsDrawn()` 也吃这个标志）跟着指针每帧闪一次"（用户实机看到的就是这个）。
7. **提示框的字体与盒子改在 Java 侧传参，不是样式表。** 页面上 `Tooltip.bind(...)` 挂出来的提示框是 AUI 自己在 `document.body` 上现建的 `<div class="aui-tooltip">`，带一条内联 style（`Tooltip.BASE_STYLE`：雅黑 11px、行高 15px、8px/10px 内边距、2px+3px 边框、4px 阴影）。按代码里的层叠顺序（`Style.mergeCascade`：样式表普通 → 内联普通 → 样式表 `!important` → 内联 `!important`）样式表里的 `!important` 本该压过它，**但实机加过那条规则、字号没变**；能确定生效的是 `Tooltip.Options.style()`——那段样式被拼在**同一条内联声明的最后**（`applyStyle` 里 `BASE_STYLE + … + options.style()`），按"同一内联块里后写的普通声明覆盖先写的"直接取胜。所以四个页面上的 tooltip 全部改成 `Tooltip.bind(…, TOOLTIP_OPTIONS)`（`AlchemyFurnaceScreen`），`alchemy.css` 里那条 `!important` 规则只当兜底（AUI 自己挂的提示框不走我们的 `Options`）。字号同样只能落在 9 的整数倍上：9px = 原版 1×，**这就是这个后端的下限**（再小就是非整数缩放位图）。

## 五、与旧实现的差异

- 少了 LDLib2 依赖：`build.gradle`、`gradle.properties` 的 `ldlib2_version`、`neoforge.mods.toml` 里的必需前置一起换成 `apricityui`。
- 少了「LDLib 编辑器里改布局」这条路径，换成文本页面 + 热重载 + 游戏内 F12 DevTools。
- 画布尺寸保持旧值：监控 188×211，三个仓页 188×146（`box-sizing: border-box`，ae 的 `.area` 自带 7px 边框，所以外框是 202×225 / 202×160）。旧模板里那些坐标（标题 7,7、背包 13,y、机器槽 18px 一格、槽间距 4px、背包与快捷栏之间的 4px、按钮 18 高）都按同样数值搬到 CSS。换 ore 后面板边框变成 3px，内容盒大了一圈——四页的子节点全是绝对定位（以面板 padding box 为基准），多出来的余量无害，坐标一个没动。
- 文本渲染用**原版位图字体**（AUI 的默认字体后端，见上一节最后两条）。1.2.5 里**没有** `aui-font-mode` 的实现（SKILL 文档描述的是更新分支），所以页面不写那个 meta——选哪套后端只看计算出来的 `font-family` 是不是 `unset`。字号 9px，正好是原版 1×。
- **`Slot.x` / `Slot.y` 在 26.1 是 `final`**，写槽位坐标需要访问转换器：本模组自带 `src/main/resources/META-INF/accesstransformer.cfg`（`public-f` 两条）并在 `build.gradle` 的 `accessTransformers` 与 `neoforge.mods.toml` 的 `[[accessTransformers]]` 里登记；ApricityUI 自己那份 AT 有同样两条，规则相同、合并无冲突。**删了这份文件编译就过不去。**
- 依赖口径：`ldlib2` 从必需前置换成 `apricityui`，并且是 `side = "CLIENT"`——专用服务端不画界面，不必装。

## 六、验证

- 编译：`gradlew.bat compileJava compileTestModJava processResources processTestModResources` 通过。
- 实机（2026-09-30，用户自己看的第一次）：页面被正常解析、面板画了出来，但报了两个问题——**面板只水平居中、贴顶边**，以及**面板后面没有那块半透明暗化遮罩**。两处都定位到根因（见 §4 第 3、4 条）并已修。
- 实机（同日第二次）：布局与遮罩正常，但**开界面时物品在屏幕左上角闪一帧**。根因见 §4 第 5 条，已加"第一遍不读几何 + 未读到几何时把槽位挪到面板外"两道处理。
- 皮肤（同日第三次）：用户看过现状后选了"整体换 ore"。`ae` → ore 的改动只有四个页面（主题 link、`<body class="ore-theme">`、面板/按钮/进度条的类名）与重写的 `alchemy.css`；Java 侧一行没动（绑的还是那些 id）。**这批改动同样没有进游戏看过**——按 ore 的网页尺度压回 18px 的那几处（按钮的压边、槽位的凹面、输入框的文字基线）是最需要眼睛确认的地方。
- 字体（同日第四次）：用户看过 ore 版之后要求"换成原版字体、字号也改小"。改动只在 `alchemy.css`：`body.ore-theme` 与四个文本规则写 `font-family: initial`，文本盒加 `display: flex; align-items: center`，字号统一 9px（标题原来 10px，并改成 `font-weight: bold` 补回层级），按钮从 `padding: 1px 2px 3px` + `line-height: 12px` 改成 `padding: 0 2px` + `line-height: 9px` 的 flex 居中，输入框 `line-height` 14px → 9px，删掉那条不生效的 `text-shadow`。Java 与页面 HTML 都没动。**没有实机确认**：原版字形在 9px 下是否清晰、中文字面大小、标题加重的观感、按钮按下时那 1px 位移。
- 槽位几何与提示框（同日第五次）：用户反馈"槽位又卡在左上角"，并要求提示框字体也改。前者是 §4 第 5 条那个帧数上限的漏洞（上限一到就照混合快照写），改成**内容对账**：`AlchemyFurnaceScreen.syncGeometry` 现在返回"这份几何能不能用"，判据是"面板被画出来的那个矩形 + 每个槽位都落在它里面"，不能用就 `parkSlots()` + 下一帧重试，`LAYOUT_WAIT_LIMIT` 换成只负责记一条 warn 的 `GEOMETRY_WARN_FRAMES`；后者是 `alchemy.css` 里新增的 `.aui-tooltip` 覆盖（提示框自带雅黑 11px 的内联 style，只能靠 `!important` 压，见 §4 第 6 条）。Java 只动了这一个类。**没有实机确认**：槽位是否稳定落在格心、物品会不会因对账失败而不画、提示框的字号与那圈内边距是否协调。

- 背包与快捷栏之间那条缝（同日第六次）：用户对着截图圈出背包末行与快捷栏之间的位置，要求「统一一下，缝隙大一些」。旧模板的槽位坐标（`533c43c5^` 的 `alchemy_monitor.ui.nbt`，直接解 NBT 读出来的 `inline` 坐标）是：背包三行顶 `10 / 28 / 46`、快捷栏 `68`——也就是说**快捷栏本来就与背包隔 4px**（46+18 → 68），AUI 版把 36 格压成一个 `gap: 0px` 的网格，这条缝丢了。现在改 `alchemy.css`：`#player_inventory` 的 `grid-template-rows` 写成 `18px 18px 18px 4px 18px`（第 4 行是空轨道）、高度 72 → 76，并按 `slot-index` 把 0..8 那九格点进第 5 行；Java 与页面 HTML 一行没动，四页共用同一份 CSS。**没有实机确认**：缝宽、以及显式 `grid-row` 点过去的快捷栏是否照旧落在格心。

- 提示框字体与"动一下槽位就闪"（同日第七次）：用户反馈"tooltip 字号还是太大"，并且**渲染 tooltip 时只要动一下鼠标、所有物品槽位就会闪**。闪的根因是 §4 第 5 条末尾那条：提示框每帧 `markDirty(RELAYOUT)` → `Drawer` 把 `body`/`html` 的 `layoutVersion` 一起推进 → 面板的 committed rect 当帧失效 → 旧的 `syncGeometry` 失败即 `parkSlots()`。改成"读到过几何就保持上一份"，只有从没读到过才挪走。字体与盒子改在 Java 侧：新增 `AlchemyFurnaceScreen.TOOLTIP_OPTIONS`（9px 原版字体、4px/6px 内边距、1px+2px 边框、2px 阴影、`maxWidth` 200），六处 `Tooltip.bind*` 都传它——§4 第 6 条说明为什么不能靠样式表的 `!important`。`alchemy.css` 里那条规则同步改成同样的数字（只当兜底）。**没有实机确认**：闪是否消失、tooltip 的字体与盒子是否合适、以及"移动鼠标时点击/悬停是否照常"。

- 界面文字不可选中（同日第八次）：用户反馈"界面的文字变得可以选择了"。AUI 的 `user-select` 不是继承属性（不在 `Style.INHERITED_PROPERTIES` 里），判定走 `Interaction.getUserSelect`：从元素自己往上取第一个不是 `unset` 的值，都没写就退成 `auto`（＝可选中）。所以整页禁选只需在 `body.ore-theme` 上写 `user-select: none`，同时在 `.input` 上写回 `user-select: text`——`Input` 的拖选与 Ctrl+A 要先过 `canSelectText`（`AbstractText` → `Interaction.isUserSelectable`）。改动只有 `alchemy.css` 两处。**没有实机确认**：拖框是否彻底选不动、输入框里拖选数字是否照旧。

- 仍未实机确认的部分：槽位是否落在槽框正中（`ITEM_INSET` 那一步）、背包三行是否 18px 无缝且与快捷栏之间正好隔 4px、按钮与输入框的点击与文字基线、tooltip 的字号／盒子与"移动鼠标不再闪"、界面文字是否彻底不可选中、热重载。`research/58` §8 那份实机证据属于旧 LDLib 版，不能当作新版的验收。
- 改完页面之后要在游戏里看到新版本，得先删掉 `<游戏目录>/apricity/mxt/alchemy/` 里那几份旧副本：播种口径是"缺了才写、已有不覆盖"，所以升级不会覆盖作者改过的那一份。
- 日志要看的东西：`[AUI HTML]` / `[AUI CSS]` / `[AUI JS]` 前缀（页面解析错误）、`[AUI Resource] scanned extension=html`（页面是否被扫到）。
