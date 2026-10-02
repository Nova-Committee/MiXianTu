---
title: 客户端界面
---

客户端界面统一放在 `com.iafenvoy.mxt.screen` 下：容器**菜单**在 `screen.menu`、它们的**界面**在 `screen.gui`，纯客户端信息界面在 `screen.information`，物品选择器在 `screen.picker`，方向性选择（12 扇轮盘）在 `screen.wheel`，HUD 元素在 `screen.hud`（框架）、`screen.resourcebar`（资源条）与 `screen.wheel`（轮盘格）。新增界面优先复用现成的 `Screen` 基类和原版组件，不要自己造滚动和文本输入。

## ApricityUI 页面

**已经交给页面的界面**（都在 `assets/mxt/apricity/` 下，**一屏一个目录、同族的放一起**）：丹炉四页（`mxt/alchemy/`）、轮盘与它的配置页（`mxt/wheel/`：`wheel.html` + `wheel_config.html`）、锻造台（`mxt/forging/`）、灵气工作台（`mxt/spirit_crafting/`）、人物信息（`mxt/information/`）、结构预览（`mxt/multiblock/`：`structure.html`，整幅窗口都是页面）、**经济组**（`mxt/economy/`：交易站两页 `station_customer` / `station_owner`、兑换站 `exchange`、支票台 `cheque`、玩家交易 `trade`）、**画符工作站**（`mxt/talisman/`：符方列表、站内符纸槽与颜料槽两格、两层 canvas 的纸面）；**每一页都链共用的 `mxt/common/theme.css`**（轮盘的扇与光环自成一套，但也链它拿画布与 `.icon` / `.track` 这些工具类）。播种只有一处（`screen/aui/AuiPages`，启动时种到 `<gameDir>/apricity/`，**缺了才写、永不覆盖**），新增一页只要往 `AuiPages` 的清单里加一行。

**页面宿主的共享部分写在接口 `screen/aui/AuiWrappedScreen` 上**（文档生命周期、按稳定 ID 绑定、页面缺失时的红字兜底、元素小助手），两个基类各继承一个原版父类再实现它：**带槽位的继承 `screen/aui/AuiContainerScreen`**（`extends AbstractContainerScreen`，管槽位几何对账、tooltip 登记与输入闸门），**没有菜单、整幅窗口都是页面的继承 `screen/aui/AuiScreen`**（`extends Screen`，管原版灰底与 `pagePath` / `pageName`）。接口不能有实例字段，所以状态放在它自己的 `AuiWrappedScreen.State` 里、由两个基类各持一份，行为是接口的默认方法；**文档本身不进 `State`**——`AuiLinkedScreen.getLinkedDocument` 已经是它唯一的出口，两个基类各拿自己的 `Document` 字段实现它，接口只在建 / 弃文档时经 `auiSetDocument` 写回，其余每处都从那一个方法取。**写页面元素只有一份实现**：`screen/aui/AuiElements`（`style` / `setText` / `setClass`，一律先比再写——每次 DOM 写都会重跑样式），宿主、滚动列表与行绑定都调它，别在宿主里再抄一份 `put` / `flag` / `setText`；页面只写不读。子类只写"这一屏有哪些节点、每拍要写什么状态"。要点：

- **子类按 ID 绑定，缺项抛异常**：`bindPage()` 里写 `this.field = this.getOrThrow("id")`；对节点标签有要求时用 `this.getOrThrow("id", Canvas.class)`（标签不对等同缺项，消息里会带上期望的标签），允许缺席的节点用 `find("id")`。不是"某一个 id"的结构性问题（容器一格都没有、`slot-index` 菜单里没有对应槽位）自己 `throw this.missing("...")`，造的是同一个异常。`auiRebind()` 接住 `NoSuchElementException`：丢掉这份绑定（**已经解析出来的那半份也一起丢**）、把缺的那一项按 `screen.mxt.page.invalid` 画成中间那行红字，并记住当前文档代数、不每帧重试。所以 `bindPage()` 没有返回值，失败也别写成别的异常——别的异常照旧直接崩（那是宿主自己的 bug，不是页面的）。`auiBound()` 答"绑上了没有"（输入闸门用它），`auiPageWritable()` 每帧问一次（热重载后重建 DOM 时它顺手重绑，那一拍答 false）。
- **滚动列表走 `AuiWrappedScreen.scrollList`**：`this.scrollList(prefix, rootId, count, binding)` 一次解析列表根与全部行元件，**缺哪件就抛哪件**（页面本身、列表、某一行、某个标签不对的元件），由同一条 `auiRebind` 画成兜底红字——所以调用点一个判空都不写。行为在 `screen/aui/AuiScrollList`：`Binding` 只管"每行怎么找"（`bind(AuiWrappedScreen screen, prefix, count)` 用 `screen.getOrThrow(...)`）、行高、列数与列间距，列表自己管可见窗口、像素滚动偏移、选中态与命中矩形（`rebuild` / `layout` / `scrollBy` / `hoveredCell` / `contains`）；绑定与列表都不接 `Document`，都向宿主取（列表连"热重载换了文档"这件事都不用管）。
- **行数由数据决定的列表，别把行数写死在页面里**：页面只声明**一行原型**（给它一个 id），Java 按需要 `prototype.cloneNode(true)` 再 `appendChild`（ApricityUI 的 `repeat` 只认 `<slot>`，没有 `<template>`；它自己展开重复槽位就是 clone + `removeAttribute("id")`），用完的行写 `display: none` 而不是删掉，行距按实际行数现算。灵气工作台是第一处：8 行写死在 132px 高的面板里，实机漏到面板外面去。两个细节：**原型要在 `bindPage()` 里先解析掉**（复制出来的元素会短暂顶着同一个 id，`getElementById` 随后指向副本），行里的节点用 `root.querySelector(".name")` 这类选择器取、取不到就 `throw this.missing(...)`。
- **槽位坐标由页面决定**：每帧读 `<slot>` 在页面里的位置写回 `Slot.x/y`，物品、悬停、拖拽与提示框仍是原版的；读到的几何必须整份落在**已经画出来的**面板矩形里，否则一个字节都不写（ApricityUI 的元素偏移是记忆化的，早期读到的是混合快照）。
- **页面文件在每次资源重载后一次性预热**：`AuiPages` 在客户端资源重载（启动、F3+T、换资源包）**结束后的第一个 tick** 上 `seedAll()` + `warmUpAll()`——把全部 16 个页面读成模板、把它们的样式表（每页两份：`common/theme.css` 加自己的）读进来并按视口编译好，所以"第一次打开某一页"只剩建文档这一件事，而不是当场读文件 + 解析 + 编译 + 因为样式表还在路上白等两拍。排这一趟的是 `AddClientReloadListenersEvent`，但**预热本身不写在监听器里**：ApricityUI 在同一趟重载里会清掉已编译样式表、并跑它自己那轮预热，写进监听器就可能被排在它后面的监听器清掉，晚一个 tick 跑就不会（这个标志初值为真，所以启动后第一个 tick 必定预热一次，事件没送到也不影响）。
- **建文档前先预热样式表，并且头两拍不画**：页面用 `<link>` 引的样式表由 ApricityUI 的工作线程读取、**下一个客户端 tick** 才生效，而 `Document.create` 当帧的样式重算只带用户代理层的 `global.css`——早画的帧用的是没有样式的 DOM（格子全堆在文档原点，容器页据此读回的槽位几何也全在左上角）。这三步现在都在 `AuiWrappedScreen.auiInit` 里，宿主只管在自己的 `init` 里 `super.init()` 之后把 `pagePath()` / `pageName()` 交给它：`Document.create` **之前** `AuiPages.warmUpStyles(path)`（预热过的样式在 `attach` 里走同步分支，首帧就是有样式的），把返回值交给 `styleHold.restart(...)`，再由基类的 tick 钩子（`containerTick()` / `tick()`）`auiTick()` 推进，`extractRenderState` 里 `auiReadyToDraw()` 为假时整帧直接 `return`（预热没成功就白等两拍；页面缺失的红字兜底照常画）。这一步现在只兜底两种情况：那次页面级预热跑了之后才加的页面，和预热失败——上面那条跑过之后，这里是一次纯缓存命中（因此 `prepared` 为真、不再白等两拍）。
- 页面里 `<slot>` 的位置 = **菜单坐标 − 1**（物品 16×16 画在 18×18 凹面的 +1 处，Java 写 `Slot.x/y` 时再 +1）；容器要写 `primary="true"` 才会被 ApricityUI 展开，否则 `repeat` 不生效。Java 每帧写回去的 `left/top` 一律是**页面坐标**（相对面板的 padding box）：别按某个子元素（轨道、格子）的局部坐标去算——锻造台两个滚动条滑块就这么画到了面板顶边、跑到标题旁边（`SCROLL_TOP - RECESS_Y + 1` 恒等于 1）。
- 页面**不能**写 `aui-mouse-events=intercept`；面板**不要写 `border`**（绝对定位的包含块是 padding box，边框会把页面里每个坐标整体推进去，边框用 `box-shadow: inset` 画）；别用 `overflow: hidden` 裁内容（实机会画出暗块）。
- **皮肤只有一套，写在 `mxt/common/theme.css`**（唯一的主题文件，`dark`＝深灰半透明底（面板 `#202020`）；页面都在 `mxt/<页名>/` 下，所以写 `<link rel="stylesheet" href="../common/theme.css">` 再链自己那份）：扁平半透明深灰（面板 `rgba(32,32,32,0.85)`（深灰 `#202020`）+ 1px 内嵌亮边、格子底是很淡的加白、没有浮雕与渐变、文字纯白），**页面自己的那份样式只写几何**（`left/top/width/height`、字号、页面特有的动画与布局）。共用的皮按类收在主题里：`.panel` / `.subpanel` / `.recess`（面板、子面板、凹槽）、`.t` / `.dim` / `.label` / `.title` / `.t-end` / `.t-center`（文字）、`slot` / `.grid` / `.inv`（槽位与容器）、`.button` / `.button-danger` / `.tab`（按钮、危险动作、页签）、`.cell` / `.frame` / `.row`（有状态的格子、静态格子底、列表行）、`.icon` / `.tex`（物品与贴图图标位，`display` 的开关只归主题，Java 写 `icon-item` / `icon-texture`）、`.track` / `.scroller` / `.thumb`（滚动条）、`.bar` / `.fill`（条与填充）、`.input`（文本输入框）。**不透明度是整体一套数**（每档约为参照值的 1.5 倍，按"所有界面统一调高"抬过一次）：抬的时候**深浅层次要一起抬**（凹槽 / 槽底 / 按钮是一组），只抬面板会把格子与按钮的对比压掉，照抄 `theme.css` 里那一组；**面板底色是唯一单独动过的例外**（0.68 → 0.85、纯黑 → 深灰 `#202020`，透明度不变；理由写在主题文件头）；**文字色的 alpha 不在这套里**。主题之外还有两处同款底色要跟着改：`MultiblockStructureView.SCENE_BACKDROP`（Java，`0xD9202020`，与结构预览的 `.overlay` 同色同深度）、轮盘页的 `.sector .fill`（与面板同色同档）。**不再引 ApricityUI 自带的 `ae` / `ore` 主题**；`slot` 要写 `background-image: none`（用户代理层给它挂了一张 `img/gui/area.png` 的原版凹面）。宿主屏幕**先画原版那层整屏暗化再交页面**：`AuiScreen.extractBackground` 默认就画 `AuiStyles.extract(this, graphics)`（＝`Screen.extractTransparentBackground` 那块半透明灰），容器宿主在自己的 `extractBackground` 里画同一块加兜底红字，**都不要调 `super.extractBackground`**——普通 `Screen` 宿主的 `isInGameUi()` 为 false，super 会走"模糊 + 菜单底图"那一支，把已经画好的 HUD 一起糊掉。**例外只有轮盘菜单**：它是边玩边读的，`extractBackground` 覆写成空的，世界与 HUD 都按原亮度显示。
- **页面侧的已知限制**（都在实机上见过）：`setTextContent` 只写字符串，**富文本会被压平**（锻造台"品质：<档位名>"那一行因此整行涂成档位色）；列表**不裁剪**，可见行数按 `列表高 / 行高` 取整，旧版被裁掉的那半行不画；人物信息两条列表**没有滚动条**（旧版是原版 `ObjectSelectionList` 的 6px 条），滚轮照旧。
- **`<texture>` 是分词器里的 void 元素**（与 `img` / `input` 同类）：只写开标签，别写 `</texture>`——分词器把开标签当自闭合、再遇到结束标签就判成 unmatched closing tag，每次建/刷新文档都为每个标签刷一条 warn（DOM 不变，纯噪音）。
- **整页文字默认不可选中**：`theme.css` 里那条 `* { user-select: none }` 就是全部——`user-select` 不在 AUI 的继承属性表里，元素没写过它时 computed 会被定型成初值 `auto`（**不是 `unset`**），`Interaction.getUserSelect` 的自下而上查找在元素自己身上就停住，**写在 `body` 上完全没用**（2026-10-01 纠正）；要靠 AUI 拖选 / Ctrl+A 的输入控件得自己写回 `user-select: text`（主题里 `input` / `textarea` 与 `.input` 都写了），否则 `AbstractText.canSelectText` 会把拖选与 Ctrl+A 一起拒掉。
- **提示框一律走原版渲染器，AUI 那套一处都不用**（2026-09-30 全仓统一）：`Tooltip.bind` → `.aui-tooltip` 把一段字符串塞进一个元素、字体与盒子由 AUI 自己的内联样式定死（雅黑 11px、白底深字 `#1a1a1a`、按网页字宽估出来的固定宽度），换到 9px 位图字体后文字沉进黑底、盒子比正文宽出近一倍，实机上文字与盒子都对不上；**而且 `AuiLinkedScreen` 的页面根本不会替 `<item>` 弹物品提示框**（那条路只在 `ApricityContainerScreen` 里），所以页面上的物品格要自己给。做法只有两种：① 容器页把"哪个元素、哪些行"登记给宿主——`AuiContainerScreen.tooltip(Element, Supplier<List<Component>>)`，宿主在 `extractTooltip` 里按元素的**实时矩形**（`Position.of` / `Size.of`，指针先 `screenToDocumentPosition` 转进页面坐标）命中，再调 `setComponentTooltipForNextFrame`；② `AuiScreen` 的整页界面自己命中自己调（结构预览的格子、人物信息的装备四格与功法行、轮盘与它的配置页的条目；装备四格画的是那一堆物品自己的提示框，用 `setTooltipForNextFrame(font, stack, …)`）。**命中判定不要用 committed rect**（ApricityUI 只在元素自己的依赖变化时重新提交，那个矩形可能整场都是旧的），页面固定几何（`AuiScrollList` 在 `show` 时记下的每行矩形、轮盘给出去的格子矩形）也可以。**同一帧里先排队的提示框赢**（`setTooltipForNextFrameInternal` 只在 `deferredTooltip == null` 时写入），所以宿主先让 `super.extractTooltip(...)` 画悬停槽位的物品提示框、页面登记的那份落在后面——**占着的格子要自己判空**，否则会把物品的提示框顶掉（丹炉的机位格就是 `machineHint` 里先看 `hasItem()`）。
- **未做 / 未专门验过**：锻造台两个选择格与数值尺仍然**重抄了菜单常量**（Java 读常量校验留待下一轮；改菜单必须同步改页面），交易站两页的**槽位下标映射写死在页面注释里**（同样要同步改）；`<texture>` 分支（锻造台方法图标若是贴图）一直没专门看过；页面里的 `<item>` 格没有自己的悬停提示框（见上一条），要提示就得自己给——锻造台、丹炉、人物信息已经给了，**兑换站的 12 个条目格还没有**。
- 共用的 `.inv`（3 行主背包 + 4px 空轨道 + 快捷栏）**只写排布、不带 `left` / `top`**，坐标由各页自己给：绝对定位的容器一旦拿不到 `left`，会退回静态位置（贴面板左沿），和它旁边的标签错开十几像素——丹炉四页就踩过这条。
- 人物信息没有菜单、面板随窗口缩，所以它的几何仍由 Java 现算后写成行内样式，两个列表是 DOM 行，人物预览与装备格里的物品仍是原版/AUI 的物品渲染。**列表的列宽（`AuiScrollList.Binding.prepare`）必须在拿到真实宽度之后再算一次**：面板几何是每帧才写进页面的，第一次 rebuild 时宽度还是 0，算出来的列会把名字和值都缩成 `...`，要等一个刷新周期（默认 1 秒）才恢复——`AuiScrollList.layout` 因此按"宽度变了就重跑 prepare"来做。它现在是**两页**（`Page.INFO` / `Page.TECHNIQUES`，左上角两个页签「人物信息」/「习得功法」切换，整块面板共用，面板不再有单独那行标题）：第二页就是原来的功法界面，所以 `TechniquePanelScreen` 已删除、功法页只由面板左上角的「习得功法」页签进入（原先那个"打开功法面板"的专属按键已删除）；两页的行都是同一套 `AuiScrollList`（像素滚动、按可见行数隐藏），只有行的内容由各自的 `Binding` 写。**"没有条目的格子"也必须写 `display: none`**：行元素从页面加载起就在 DOM 里，功法行自己带图标框、进度槽与分隔线，只把文字清空的话这些盒子照旧画出来（实机表现为列表下方一片残留的空框），所以 `clear` 不看"这一格之前显没显过"、一律隐藏；选中高亮同理，**按格子里的条目 id 在每次 layout 之后重贴**，否则滚动一行高亮就落在别人身上。
- **人物信息面板的开关是同一把键**：`key.mxt.information_panel`（默认 `Z`）在没有别的界面打开时打开它，面板开着时再按一次关掉它（`Esc` 照旧能关）。它和轮盘那几把键一样**裸轮询物理按键**（`MxtKeyMappings.KeyMappingHolder.isPhysicallyDown()`）：`setScreen` 对屏幕上的那次按下会 `releaseAll`，而 `grabMouse` 又 `setAll()` 把还按着的键重新按一次——走 `KeyMapping` 的状态就会把这次"重新按下"当成新的一次，面板刚关就又弹开。
- **要绑页面监听的界面，别在窗口缩放时重绑**：`init()` 在缩放时会再进来一次，而 ApricityUI 的 `addEventListener` 只往列表里追加、从不去重（`applyViewport` 也不重建 DOM），所以缩放之后一次点击会跑两遍监听——上一版每一处绑监听的界面都有这个问题（容器页的键、人物信息的两个页签与每一行；读 ApricityUI 的 `EventRegistry` 发现的，缩放一次就会暴露）。缩放路径因此只 `applyViewport(true)`（`auiInit` 在文档已存在时只做这一件事），元素没换就不重绑；真正被重建的文档由 `refreshGeneration` 抓（`auiPageWritable()` 里那道检查会重绑），**热重载才是唯一需要重绑的时机**。容器页的槽位本来就是每帧回读的，人物信息的面板几何也是每帧重算的，所以缩放不需要别的工作。

## 结构预览 `screen.multiblock`

阵法 `/formation show` 打开的（`MultiblockStructureScreen`，由 `FormationStructureS2CPayload` 触发）是另一类页面：**整幅窗口都是页面**（`assets/mxt/apricity/mxt/multiblock/structure.html`，同样链 `mxt/common/theme.css`），上下两条压边、标题、层号、提示、五个按键与时间轴都由它画，**只有三维场景仍归 Java**——场景是一份 picture-in-picture 渲染状态，提交在页面留出的 `#scene` 那块矩形里（**通栏**：两条压边之间全是它，所以整幅窗口没有一处透出世界），它下面那层暗底也在 Java 侧、紧挨着 PIP 画（所以 `#scene` **必须保持透明**：谁给它底色就把场景盖掉）。页面里**一个坐标都不写**：几何的唯一来源是 `MultiblockStructureView`，窗口尺寸一变 `MultiblockStructureScreen#writeLayout` 就把这些盒子重写一遍，之后每帧只写真的会变的东西（三行字、四个按键的字、时间轴已播放的长度与滑块位置），而且都先比较再写。**按键的悬停与按下交给 CSS**（`.button:hover` / `:active`），Java 不再逐帧算 hover。**只有两件输入仍由 Java 命中判定**（页面别写 `aui-mouse-events=intercept`）：时间轴的点击跳层要用指针的 x 坐标，场景里的拖动转视角要用指针位移；五个按键是普通的 DOM `click`。

## 可拖动 HUD 框架 `screen.hud`

模块自己画的 HUD 元素（快捷栏、资源条……）要让玩家能拖动并存档，就接上这套框架。框架只做四件事：**登记元素**、**每帧画它们**、**给编辑器提供命中与占位框**、**把位置写进 HUD 布局文件**。

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
    // 默认落在窗口锚点（这里不写就用左上角）加这个像素偏移；玩家在编辑器里拖过之后以存档为准
    @Override public int defaultOffsetX() { return 4; }
    @Override public int defaultOffsetY() { return 4; }

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

- **位置是「窗口锚点 + 像素偏移」存的**：`config/mxt/mxt-hud.json` 的根是一个 `version` 加一张 `layout` 表，每一项是一个对象——`{"anchor": {"horizontal": "center", "vertical": "bottom"}, "offset_x": -55, "offset_y": -47, "visible": true}`。锚点是窗口的八个点之一（四角 + 上下左右四个边中点，JSON 里写成两个轴的对象，而不是一个拼好的字符串），偏移是**从那个窗口点到元素自己同名点的像素距离**，所以换分辨率、换 GUI 缩放后布局不会漂，拖动也不会被比例取整吃掉一像素。显示的像素每次读取都夹进窗口，元素不可能被拖到看不见的地方；**夹取不改写存档**，窗口缩小时贴边、还原后回到原处。文件由模组自己读写（`screen/hud/HudLayoutFile`，不走 jupiter 配置框架），根上的 `version` 是 `3`：旧版那种 `hud.layout_v2` 的 `x,y,visible` 字符串**不再被读**，那批布局退回默认位置。
- **HUD 布局不进配置框架**：它是模组自己的一个 JSON 文件（`config/mxt/mxt-hud.json`），一行一个元素的对象，编辑器拖动一步就写一次，而不是挨个填的设置项。其余配置也统一收进 `config/mxt/`：`mxt-client.json`、`mxt-server.json`。模组列表里那个「配置」按钮打开的选择界面（`ConfigSelectScreen`）**只有客户端与服务端两个槽位**——jupiter 的 builder 每个槽位只存一个容器，`client(...)` 写两次是后者覆盖前者（2026-09-22 真踩过：`MxtHudConfig` 把 `MxtClientConfig` 顶掉，点「客户端配置」进的是 HUD 布局页），所以 HUD 布局从来就不该进那个界面，改它用布局编辑器与 `/hud`。
- **锚点 `HudAnchor`** 是窗口的八个点（四角 + 上下左右四个边中点），它同时管两件事：存下来的偏移**从哪个窗口点量起**，以及**尺寸变化时元素的哪个点不动**。默认是左上角；会往上长的东西（一列资源条）用 `CENTER_BOTTOM`（下边中点），轮盘格用 `LEFT_CENTER`（左边中点）。玩家在编辑器里把元素拖到某个锚点方框上就**自动绑定**到它：绑定**不移动元素**，只把偏移按新锚点重算，所以拖过锚点的瞬间不会跳，之后窗口变化时这个元素就跟着那个点走。元素当前的锚点由 `anchor()` 回答，`/hud` 会打出来。
- **改动即存档**：拖动、方向键微调、显隐切换都会立刻写布局文件。没有「关界面时统一保存」，所以崩了也不会丢布局；反过来，手动改 JSON 之后要重开客户端才生效（元素只在构造时读一次）。
- **位置只有一个来源**：存档里有这个布局键就用键里的锚点与像素偏移（**窗口变化时不需要重算**：两个数都是绝对的），没有就用元素自己给的默认（`defaultAnchor()` + `defaultOffsetX()` / `defaultOffsetY()`，每帧重算，所以跟着窗口走）。`resetToDefault()` 会把键从存档里删掉——复位必须跨重启有效，否则看起来就像「布局没保存」。（这里曾经多存了一个布尔标志表示「当前用的是存档位置」，它初值是 `false`、只有拖动才置真，于是**文件里的位置永远轮不到被读**：每次重启都退回默认。删掉那个标志、只留「键在不在」这一个事实，问题就没了。）
- **`moveableEntries()` 收的是所有 `moveable()` 的元素，隐藏的也在内**：编辑器要把隐藏的也画成半透明、并用它右上角的勾选框把它收回来，在这里过滤掉就再也点不到了。自己算位置、不该被拖的元素（比如居中的快捷栏）重写 `moveable() { return false; }` 即可，它依然会被框架画；这类元素如果连尺寸都由世界状态决定，还要重写 `refreshPlacement()` 并调用基类的 `placeAtDefault()` 把默认位置真正应用上去——**只算尺寸不落位置的话，锚点会一直停在 `(0,0)`**，元素画在窗口左上角（本模组的 `resource_bars.target` / `resource_bars.boss` 就踩过这个）。
- **尺寸由元素自己说了算**：宽度随内容变化的元素在变化时调用 `setSize(w, h)`，框架据此重算夹取范围。它不会替你去量。**空元素也要给一个非零尺寸**，否则玩家在编辑器里看不到它、也就没法摆。
- **元素要在客户端初始化时登记**，不要等第一帧渲染：框架的 GUI 层只在世界里绘制，否则玩家在主菜单打开编辑器时框架还是空的，界面看起来就像坏了。
- **编辑界面 `HudEditScreen`** 由 `HudManager.openEditor()` 打开（按键 `key.mxt.hud_layout`，默认右 Shift，或客户端命令 `/hud open`）。里面左键拖动、**把元素拖到窗口边上的八个锚点方框上即自动绑定、并画一条从锚点到它的连接线**（线只画选中/正在拖的那一个，八个方框则常显）、**元素右上角框内的勾选框切换显隐**（隐藏的元素在编辑器里半透明显示、勾选框本身不透明，所以隐藏不是单向操作）、方向键 1 像素（按住修饰键 10 像素）微调、`Esc` 放弃当前选中、点空白处取消选中。**选中在松开鼠标后保留**：不然方向键只能在按着鼠标的时候用，连接线也只在拖动的一瞬间看得到。**还没有缩放**：这一版只做移动与绑定，KronHUD 那种拖角缩放与吸附参考线都没移植。
- 编辑器里元素**照常真实绘制**（GUI 层顺序在最后），编辑器只额外画半透明矩形和名字标签，所以看到的就是实际效果。
- 诊断用客户端命令 `/hud`：打出每个元素的布局键、绑定的**锚点**、位置、尺寸、当前块数与可见/可拖状态。「一个元素都没登记」和「登记了但这一帧没有内容」在界面上长得一样，只有它能分开。

不接内容时框架是空转的：没有登记任何元素时，编辑界面只有一句「当前没有可移动的 HUD 元素」。范围、未移植项与后续接法见 [`research/26_可拖动HUD框架设计.md`](../../../research/26_可拖动HUD框架设计.md)。

**已接入的元素**：资源条一共四个元素，全部登记进框架、由 `HudRenderer` 画——两列可拖的（`resource_bars.left` / `resource_bars.right`，键 `Anchor.LEFT` / `Anchor.RIGHT`）加两条不可拖的固定行（`resource_bars.target` / `resource_bars.boss`，`ResourceBarFixedEntry`，`moveable() == false`、位置每帧现算、永不入档）。资源条自己的那个 `mxt:resource_bars` GUI 层**已经删除**，`ResourceBarOverlay` 只剩纯工具方法。这四条可以当范例：`ResourceBarOverlay.column(anchor)`／`row(target, layout)` 只回答"哪些条、什么顺序"，条目用 `ResourceBarEntry.blocksWithGaps(...)` 把每条包成块并在条之间插 `spacer`，尺寸交给基类夹取。两列的默认锚点是**下边中点**（`CENTER_BOTTOM`），偏移分别是 ∓(`CENTRE_GAP` + 列宽/2) 与 −47（也就是站在快捷栏与血条上方）。**位置在数据包那边没有字段**——`anchor` 只决定落进哪一列，列摆在哪是玩家自己的设置在客户端配置里。

第五个元素是轮盘的「轮盘格」（`screen/wheel/WheelSelectionEntry`，键 `wheel.selection`）：它**不走 `RenderBlock`**，`renderBlocks()` 返回空、自己用 `render()` 画**永远 4 列、行数随页数向下长的格子**（每格 22px，一页 12 格 = 三行；块宽固定 94、高按内容算，格子号 = 编号读序，**编号此刻代表的那一格换成金色边框**），**冷却中的格子则按原版物品那一套压一层白幕**——盖住图标的剩余比例、随时间从上往下退（剩余 ticks 读条目的 `cooldownTicks`，全长读新增的 `cooldownLength`：技能用上一次实际拿到的 `mxt:cooldown` 长度，灵气用固定发射间隔；答不出全长的条目画满整块，只说"在冷却"），其它原因不可用时才压那层暗色，所以它也是"整块自己画"那条口子的第一个范例，还是**尺寸随内容变**的第一个范例（`layoutWidth` / `layoutHeight` 每帧算，`refreshPlacement` 里 `setSize` 回报，长出去会被夹回窗口）；默认锚点是**左边中点**（`LEFT_CENTER`，`defaultOffsetX() = 4`、`defaultOffsetY() = 0`），也就是窗口左边、竖直居中，块的尺寸同时是命中矩形和占位框。格子内容读 `WheelSelectionState.pages()`——每客户端刻解析一次的整张轮盘快照，所以它画的是**每一页**，而**块的上方不写任何字**（哪一页由轮盘自己说）。它由 `MiXianTuClient#init` 与资源条一起登记，理由同 §"登记时机"——不然从主菜单打开编辑器就看不到它。它对应的玩法（`R` 选、`V` 用、左右方向键切页）见 [`wheel.md`](./wheel.md)。

## 轮盘选择系统 `screen.wheel`

按键（`key.mxt.wheel`，**默认 R**）按住，屏幕上出现一个 **12 扇**的轮盘：指针**朝哪个方向**就选中哪一扇，选中的扇区**底色变深**（灰阶高亮）并向外扩一点，**这一扇的名字写在轮盘正中间**。它现在是技能（法器技能已并入其中）与灵气**唯一的触发入口**：原来"技能与灵气各有一条快捷栏、各有一个配置界面"的两套东西已经删除，`LAlt` 那个按键也一并取消（`V` 虽然重新被占用，但含义换成了"用掉轮盘当前选中的那一扇"，见下）。

**轮盘由"主盘 + 从盘"组成，用一套连续编号串起来**（2026-09-22 新增并按玩家口径重做，见 `research/31_多轮盘与轮盘来源设计.md` §10）：主盘是玩家自己摆的 12 格（编号 `0..11`），从盘（主手物品 / 副手物品 / 法器 / 契约灵兽）按随身装备与手里的御兽铃自动生成、**不存储**（内容是这些装备此刻授予的主动技能，加上它们作为法器声明的**技能**：储物这类；**御器之术不在从盘里**——它是功法这类授予来源给的一条普通技能，见 `research/48_飞行法器与御器术重设计.md`，法器开关的接线见 `research/32_法器开关与轮盘接线设计.md`；契约灵兽那一页是**手里御兽铃对准的灵宠认的行为**，2026-09-25 新增），格子从 `12` 起接着排；**一页 12 格**，一个来源占 `ceil(条目数 / 12)` 页（一条都没有就一页都不占），内容多了就**再开一页**而不是丢掉。切换是两把键（`key.mxt.wheel_previous` / `key.mxt.wheel_next`，默认键盘左右方向键，两头环绕），`R` **打开始终回到主盘（第一页）**。**页只是视图，编号才是选择**：轮盘画当前页、HUD 轮盘格画整张轮盘、槽位键作用于当前页，关着轮盘时的 `V` 用编号此刻代表的那一格（**编号越界时自动落到最后一个有东西的格子，且编号本身不改写**，所以物品拿回来就恢复原选择）。

**选择与使用分成两个键**：`R` 只负责选（松开或再按一次**只关闭、不触发**），`key.mxt.wheel_use`（默认 `V`）负责用——轮盘开着时用掉指针那一格且**不关轮盘**，关着时用掉**编号此刻代表的那一格**（客户端 `WheelSelectionState` 里是 `page` + `number` 两个值，`LoggingIn` 清空后由服务端记住的 `armed`（一个数字）填回来，见 [`wheel.md`](./wheel.md)），鼠标左键等同于它。这把键与两把切页键都由 `WheelMenuController` **裸轮询**（`InputConstants`/GLFW），因为任何 `Screen` 一打开原版就 `KeyMapping.releaseAll()`，而且"按着 `V` 松开 `R`"会让 `grabMouse()` 里的 `KeyMapping.setAll()` 补出一次假按下、多触发一次。可拖动的 HUD 元素「轮盘格」（`wheel.selection`）画的正是整张轮盘的格子：**金色边框那一格就是"现在按 `V` 会放什么"**（轮盘开着时它跟着指针走）。

框架（几何、开合状态机、选择语义）在 `screen.wheel`，环与动画自 2026-09-30 起由 ApricityUI 页面 `assets/mxt/apricity/mxt/wheel/` 画（见 [客户端轮盘](wheel)），内容（技能与灵气怎样变成条目、每个来源贡献什么）在 `screen.wheel/content`，编辑界面是 `WheelConfigurationScreen`；接法与数据流见 [客户端轮盘](wheel)。三条界面语义值得记住：`isPauseScreen()` 返回 `false`（单人游戏里不暂停）；`extractBackground(...)` **留空**（默认背景会把这之前提取的整层 HUD 糊掉，`HudEditScreen` 当初也是为同一个模糊问题覆写 `isInGameUi()`）；**按住轮盘时角色会停下**（原版对任何 `Screen` 都 `KeyMapping.releaseAll()`，松开时 `grabMouse()` 会把物理按键状态同步回来，不用重新按）。

框架里两条不肯让步的约定：**判扇区只看方向、不看距离**（指针还在内圈里也算数，所以中间那块空地能一直显示"当前指着的扇区叫什么"），以及**指针方向 → 扇区号的换算只有 `WheelGeometry` 一处**（`sectorStart` / `sectorCentre` / `sectorAt` 由同一组常量推出，`sectorAt(sectorCentre(k)) == k` 恒成立；MineMenu 把这段抄了三处、其中一处约定还和另外两处不同）。

## 法器储物窗口 `MxtMenus.ARTIFACT_STORAGE`

轮盘上「储物」那一格按下去打开的就是它（2026-09-22 新增，见 `research/32_法器开关与轮盘接线设计.md`）：**没有自己的菜单类，也没有自己的界面类**——它就是原版箱子那一套，注册的是 `ChestMenu`，客户端注册的是 `ContainerScreen`（`generic_54.png` 那张贴图本来就支持 1..6 行），所以 176 宽、行数由 `getRowCount()` 决定，标题由开窗包带过去（`screen.mxt.artifact_storage` = 「储物 · 法器名」）。行数走 `IMenuTypeExtension`，由服务端在开窗时写进附加数据：`capacity / 9`，客户端据此重建一个同样大小的 `SimpleContainer` 镜像，格子内容照常走菜单同步。

内容那一侧是 `runtime/artifact/ArtifactStorageContainer`（扩展 `SimpleContainer`）：开窗时按**这条技能自己的 id** 从承载物的 `mxt:storage` 组件里读出容器（类型 `mxt:container`），之后**每一次改动都在 `setChanged()` 里整份写回**（`ArtifactStorageService#replace`，一次组件更新而不是每格一次）。它**故意不持有那个物品堆**：法器的位置是会变的，往一个没人拿着的栈里写就是物品消失的经典成因——所以每次读写都按"这件法器还在不在玩家身上"重新解析（`ArtifactService#carried` 扫双手、背包与 Curios），`stillValid` 一旦为假，服务端每刻的菜单检查就会把窗口关掉。储物格数由定义决定：**按 9 向上取整、最多 6 行（54 格）**，容量与窗口永远是同一个数。


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

翻译键的拼法统一由 `com.iafenvoy.mxt.util.DefinitionText` 决定：类别就是注册表自己的 path，没有例外表。手里已经有 `Holder` / `ResourceKey` 时直接 `DefinitionText.name(holder)`，只有拿到的是一根光秃秃的 `Identifier` 时才需要把类别当参数传进去（`DefinitionText.name(id, "resource")`）。自带 `name` / `description` 字段的定义（24 张注册表，含药性、炉型、炉壁材料、灵植、丹药、丹药绑定与 `quality`；`quality_chain` 已删除）不走这条路：文本由数据包给，或由 `ContextNameCodec` 按 id 生成**同一个键**（`quality.mxt.<命名空间>.<路径>` 这类），所以两套名字键不再是两套。`mxt:alchemy` 配方不是数据包注册表，省略名字时用 `recipe.mxt.<命名空间>.<路径>`，路径里的 `/` 改成 `.`。丹炉四个页面走的是 ApricityUI 的 HTML，不是原版 `Screen` 的控件树：页面在 `assets/mxt/apricity/mxt/alchemy/`（种到游戏目录 `apricity/mxt/alchemy/`），Java 只按稳定节点绑状态、事件与真实槽位——契约见 `docs/数据包格式.md` 的丹炉一节，页面里的坑（`aui-mouse-events` 不能写 `intercept`、`Slot.x/y` 要靠访问转换器才可写）见 `AGENTS.md` §4 的 AUI 条款与本文前面那份宿主要点。

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
