---
title: 网络协议与服务端权威
---

客户端只发送意图，服务端重新解析 ID、检查附件和条件后结算。主要 C2S payload 包括：

| Payload | 用途 |
| --- | --- |
| `WheelActionC2SPayload` | 轮盘选中一项：`(source, kind, id)`——**哪个来源**、哪一类、哪个 id（由客户端在按下的那一刻从**当时那一格**解析出来）。技能与灵气**共用这一条通道**，服务端按 `kind` 分派（技能走 `runtime/ability/AbilityActivationService.activate`——`mxt:active` 在那里转成一次施放，`mxt:flight_control` / `mxt:storage` 在那里起剑 / 开箱；灵气走 `SpiritBurstService.fireOnce`——**请求只说"按了这一格"**，该开该关该打开哪个界面由服务端读一遍状态再决定），并在分派前先用 `source` 把那个来源重读一遍（主盘读存档布局、从盘的技能读现在的授予账、从盘的法器技能则要求那一页的装备此刻仍然提供这条技能），这项不在那里就整个请求作废。**格子编号不在这条路上**：它只用于存储。 |
| `BackSlotSwapC2SPayload` | 交换主手和背部槽位。 |
| `ForgingActionC2SPayload` | 锻造开始、敲击、完成和取消。 |
| `ChequeActionC2SPayload` | 支票桌存入/取出。 |
| `StationTradeC2SPayload` | 交易站结算。 |
| `PlayerTradeActionC2SPayload` | 已打开的一对一交易里改变请求方自己的状态。 |
| `CultivationToggleC2SPayload` | 请求切换修炼模式。 |
| `WheelActionC2SPayload` | 轮盘选中一项：`(source, kind, id, enabled)`——**哪个来源**、哪一类、哪个 id，加上一个**可选的**方向。`enabled` 为空＝"按了这一格"（轮盘走的就是这条，方向归服务端）；填了＝**点名一个状态**的直接请求（脚本或界面用，此时这一项按 id 寻址、不看来源那一页，但技能自己的闸门照过）。`FlightToggleC2SPayload` 已于 2026-09-25 合并进这个字段。 |
| `WheelLayoutC2SPayload` | 轮盘配置界面关闭时把完整的 12 格**主盘**布局送回服务端；服务端逐格校验后写进玩家附件。从盘没有对应的包，因为它们不存。 |
| `WheelSelectionC2SPayload` | 换了选中的格子（`Optional<Integer>` = **格子编号**，空 = 没选）：格子按整张轮盘连续编号、页会随装备来去，所以存的只是一个位置，服务端只做范围检查，不解析也不校验——"这个位置上现在什么都没有"是合法状态（客户端那边会自动落到最后一个有东西的格子）。 |
| `OwnerNameC2SPayload` | 问某个归属 UUID 叫什么名字（只带 id）。服务端只查在线玩家列表与持久化的名字缓存，**不查会话服务**——那是网络请求，而处理器跑在主线程上。 |
| `FlightDescendC2SPayload` | 飞行下降键（`key.mxt.flight_descend`，默认 `X`，可改绑）的**按住状态**：按下与松开各发一次，上座那一刻再补一次（"起剑前就按着"的那一次变化不会自己发生）。它不是原版输入，所以只能这样告诉服务端；服务端把它写在**驾驶者那份飞行记录**上（没在飞就没有记录、这条直接丢掉），下降仍由服务端的飞行 tick 每刻读一次。 |

服务端向客户端同步动态注册表、资源/灵气必要状态（`AuraStateS2CPayload`）和附件，并按需下发 `ItemPickerS2CPayload`（打开物品选择器，只带标题和分类 id，不带物品）与 `OwnerNameS2CPayload`（回答上一条：知道就回名字，没见过这名玩家就什么都不回——客户端把这个答案也记下来，于是一次会话只问一次，工具提示下一帧就能读到名字）。不要把客户端传入的数值当作可信结果；payload 只应传 ID、选择和操作意图。**轮盘配置界面不在这条路上**：它是客户端命令 `/wheel`（或那个默认未绑定的按键）自己打开的，服务端既不参与也没有对应 payload。

**S2C payload 的类型两端都要登记，但 handler 只在客户端登记。** 服务端是编码方，所以它必须知道这些 payload 的 codec；可它永远不会处理它们，而 `ClientNetworkHandler` 这类处理器会碰到 `Screen` 等客户端专属类——专用服务器的类加载器拒绝加载这些类，只要在注册时**构造**一次处理器，服务器就会在 mod 加载阶段崩掉（`NoClassDefFoundError: net/minecraft/client/gui/screens/Screen`）。`NetworkManager` 因此按 `FMLEnvironment.getDist()` 分两支：客户端用带 handler 的 `playToClient`，服务端用不带 handler 的那个重载，只登记类型。

**唯一的例外是物品选择器**，它借用原版的 `ServerboundSetCreativeModeSlotPacket`，所以物品内容确实由客户端给出，也不经过任何 mod payload。这条通道由服务端自己的能力开关把守：包在**解码层**被 `GameProtocols.HAS_INFINITE_MATERIALS` 拦下（服务端认为玩家不是创造模式就整包丢弃，不断线），`handleSetCreativeModeSlot` 再查一次 `hasInfiniteMaterials()` 并校验物品特性与堆叠上限。新增 mod payload 时**不要模仿它**——拿不到同等门禁的内容一律要走"客户端只报 id、服务端自己解析"。详见[客户端界面](screens)。
