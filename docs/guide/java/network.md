---
title: 网络协议与服务端权威
---

客户端只发送意图，服务端重新解析 ID、检查附件和条件后结算。主要 C2S payload 包括：

| Payload | 用途 |
| --- | --- |
| `AbilityActionC2SPayload` | 技能使用或取消。 |
| `SpiritBurstC2SPayload` | 指定一种灵气开始或停止释放。 |
| `BackSlotSwapC2SPayload` | 交换主手和背部槽位。 |
| `ForgingActionC2SPayload` | 锻造开始、敲击、完成和取消。 |
| `ChequeActionC2SPayload` | 支票桌存入/取出。 |
| `StationTradeC2SPayload` | 交易站结算。 |
| `PlayerTradeActionC2SPayload` | 已打开的一对一交易里改变请求方自己的状态。 |
| `CultivationToggleC2SPayload` | 请求切换修炼模式。 |
| `FlightToggleC2SPayload` | 请求开关一种飞行状态；服务端仍校验请求的 `archetype`。 |
| `HotbarLayoutC2SPayload` | 配置界面关闭时把完整的快捷栏布局送回服务端。 |

服务端向客户端同步动态注册表、资源/灵气必要状态（`AuraStateS2CPayload`）和附件，并按需下发 `HotbarConfigurationS2CPayload`（让客户端打开某个模式的快捷栏配置界面）与 `ItemPickerS2CPayload`（打开物品选择器，只带标题和分类 id，不带物品）。不要把客户端传入的数值当作可信结果；payload 只应传 ID、选择和操作意图。

**唯一的例外是物品选择器**，它借用原版的 `ServerboundSetCreativeModeSlotPacket`，所以物品内容确实由客户端给出，也不经过任何 mod payload。这条通道由服务端自己的能力开关把守：包在**解码层**被 `GameProtocols.HAS_INFINITE_MATERIALS` 拦下（服务端认为玩家不是创造模式就整包丢弃，不断线），`handleSetCreativeModeSlot` 再查一次 `hasInfiniteMaterials()` 并校验物品特性与堆叠上限。新增 mod payload 时**不要模仿它**——拿不到同等门禁的内容一律要走"客户端只报 id、服务端自己解析"。详见[客户端界面](screens)。
