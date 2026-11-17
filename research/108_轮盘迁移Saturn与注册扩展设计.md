# 108 · 轮盘迁移 Saturn 与注册扩展

## 范围与归属

Saturn 提供通用轮盘：12 格布局、派生分页、输入、HUD、原生 Screen、玩家附件和三个 C2S 协议。MiXianTu 只提供灵气、契约行为，以及能力 tooltip 的元素、授予来源和法器品质说明。轮盘不依赖 AUI，不增加依赖与 Mixin；本体其他 AUI 界面不受影响。

按本次要求直接替换旧入口，不迁移 `mxt:wheel_layout`，不保留旧协议、类型别名和本体轮盘配置。MiXianTu 已从临时本地 Maven 验证切回 Curse Maven 的 Saturn 发行产物，并同步声明包含通用轮盘的最低依赖版本。

## 注册契约

| 扩展点 | 注册入口 | 所在线程与职责 |
| --- | --- | --- |
| `WheelEntryKind` | `WheelEntryKinds.register(kind)` | 两端模组初始化；身份解析、存在性检查、普通与定向触发 |
| `WheelSource` | `WheelSourceTypes.register(source)` | 两端模组初始化；授予来源、承载物和服务端 `offers` |
| `WheelEntryProvider` | `WheelContent.register(kind, provider)` | 客户端初始化；统一候选池与已保存条目的解析 |
| `WheelMenuProvider` | `WheelMenuContent.register(source, provider)` | 客户端初始化；某个派生来源的有序条目 |
| `AbilityWheelTooltip` | `AbilityWheelTooltips.register(owner, provider)` | 客户端初始化；追加业务说明，不接管触发 |
| 装备与能力 | 现有 `AbilityHooks` | 复用通用 `itemAbilities` / `equippedItems`，不再读取本体法器表 |

kind、source 和候选提供者按 id 首次注册生效；页面提供者按 source id 替换，tooltip 按 owner 替换。注册表加锁，调用提供者时使用快照。客户端扩展在 `enqueueWork` 注册。

内建 source 为 `saturn:configured`、`saturn:main_hand`、`saturn:off_hand`、`saturn:equipped`；kind 为 `saturn:empty` 和 `saturn:ability`。MiXianTu 注册 `mxt:aura`、`mxt:behavior` 和 `mxt:contract`，不将业务类型上移。

`WheelEntryProvider.options` 是可配置候选；`available` 还可包括不允许新放入、但仍能解析已有槽位的条目。每格按 kind id + entry id 比较，不依赖实现对象的引用。界面条目只提供展示数据，按下操作统一发请求，不允许每个条目自行接一套发送回调。

## 触发与状态

所有网络请求统一执行：解析注册 id → `kind.exists` → `source.offers` → 普通 `trigger` 或定向 `directed`。指定目标状态也不能绕过来源检查。能力入口只接受 `Togglable`，再检查玩家仍持有该能力，复用现有 `AbilityActivationService` 的前置条件、消耗、冷却与行为。

主盘 source 检查玩家保存的布局；手持及扩展装备 source 检查当前授予账本。配置载体可从全部原版装备槽和扩展装备读取；仅由当前双手授予的能力留在对应手持页，护甲来源可以放入主盘。保存布局并不授予能力。

12 格与选中编号保存在 Saturn 的单一 `wheel_layout` 附件中，复制死亡数据并同步。短布局补空、长布局截断；未知或非法 kind 解析为空，保存时清除不存在的定义。服务端限制选中编号边界。

## 原生界面

轮盘直接使用原版 Screen 和 GUI 绘制。圆环几何在初始化时生成水平线段，帧内复用，不读取 DOM 或创建 AUI 文档。派生页只绘制实际存在的扇区；指针方向与分页、选择的规则继续共用一份实现。此次原生轮盘不复刻原来的 CSS 开合动画。

配置页上方为一个合并候选网格，所有已注册 kind 混合去重；不再左右分栏。候选区裁剪、滚轮与可拖动滚动条只移动候选条目。底部为固定 12 格，22 px 格子、24 px 间距，保留条目颜色条；窄窗口自动换行，低窗口隐藏辅助行以给候选区留空间。左键选候选再放入槽位，右键或未选候选时点击槽位清空；关闭时保存一次。

轮盘 HUD 按页计算可见格数：主盘未配置或12格全空时只省略主盘，自动识别的从盘技能继续显示；至少配置一项后恢复主盘12格，清空后再次省略主盘。高度与绘制共用各页的 HUD 格数，从盘向上收拢，但触发、高亮与保存的选择仍使用原始页编号。该判定不修改 HUD 的用户可见性开关，布局编辑器仍保留占位框。

输入继续使用 `R` 选择、`V` 使用、左右方向键翻页；快捷槽与配置键默认未绑定。槽位1–12单列「Saturn：轮盘槽位」，其他按键统一使用 Saturn 主分类。三个客户端选项归 Saturn：`wheel.hold`、`wheel.scroll_switch`、`wheel.wrap_pages`。

## 验证状态

- 两仓主代码与 MiXianTu 探针编译通过，本地 Maven 完整链路通过。
- Saturn 独立检查 204 项通过：框架 120、玩家视图 84；框架中 76 项覆盖四种配置界面尺寸的裁剪、槽位命中和末行可达性。
- 新增 4 组 GameTest：来源分派、通用装备钩子、布局及协议往返、配置布局；原有扩展注册探针与相关业务探针已迁至 Saturn API。已实跑 17/17 组通过：轮盘 5、法器飞行 3、契约 9。
- 主盘 HUD 显隐调整后，5/5 组轮盘 GameTest 回归通过；协议探针新增7项断言，覆盖默认空布局、同步配置、清空最后一格及重新配置，确认主盘在0格与12格之间切换、从盘始终保留3格。两仓重新编译、本地 Maven 更新与双语文档构建通过，日志为 `build/reports/wheel-hud-gametest.log` 与 `wheel-hud-docs-build.log`。
- 切回 Curse Maven 发行产物后，已核对 Jar 内版本与轮盘实现，MiXianTu 主代码及探针重新编译通过；移除临时 `mavenLocal()`，最低 Saturn 依赖与发行产物对齐。本次依赖切换未重新运行 GameTest。
- 未启动客户端，原生界面的实际观感、输入与长候选池拖动需要玩家复验。

验证日志位于 `build/reports/wheel-gametest.log`、`wheel-flight-gametest.log`、`wheel-contract-gametest.log`，各文件均输出 `All N required tests passed`。两仓打包及中英文翻译键集合检查通过（MiXianTu 1312、Saturn 192），双语文档站构建通过，日志位于 `build/reports/wheel-docs-build.log`。
