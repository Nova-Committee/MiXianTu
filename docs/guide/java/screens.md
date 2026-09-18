---
title: 客户端界面
---

客户端界面统一放在 `com.iafenvoy.mxt.screen` 下：容器**菜单**在 `screen.menu`、它们的**界面**在 `screen.gui`，纯客户端信息界面在 `screen.information`，物品选择器在 `screen.picker`，HUD 覆盖层（快捷栏、资源条）在 `screen.overlay.hotbar` / `screen.overlay.resourcebar`。新增界面优先复用现成的 `Screen` 基类和原版组件，不要自己造滚动和文本输入。

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
- 数据驱动定义没有自己的物品，堆上根本看不出它代表谁，于是名字由 `DefinitionText` 从它的 `Holder` / `ResourceKey` 生成翻译键得到——`mxt:fire` 在 `mxt:aura` 里就查 `aura.mxt.fire`——再补上它的 id。定义有自己名字的（品质，名字写在数据包里）就用那个名字；
- 标签匹配展开出来的行，名字里既有那个物品自己的名字，也有它所属定义的名字和 id。

用列表而不是单个名字，是因为一行可以有好几种叫法。界面不再需要从「注册表 key + 条目 id」去反推任何东西；只有 `over(...)` 那条路没有目录可问，界面自己补上「展示名 + item id」。

翻译键的拼法统一由 `com.iafenvoy.mxt.util.DefinitionText` 决定：类别默认取注册表自己的 path，少数不是的（`mxt:item_quality` 一直按 `quality` 翻译）在它里面的 `CATEGORIES` 声明一次。手里已经有 `Holder` / `ResourceKey` 时直接 `DefinitionText.name(holder)`，只有拿到的是一根光秃秃的 `Identifier` 时才需要把类别当参数传进去（`DefinitionText.name(id, "resource")`）。

分类就是注册表本身，`/picker <分类 id>` 可以只列出某一个（如 `/picker mxt:aura`、`/picker mxt:currency`、`/picker mxt:item_binding`），不写则给出全部已注册分类。

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
