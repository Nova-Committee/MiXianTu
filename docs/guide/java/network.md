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

服务端向客户端同步动态注册表、资源/灵气必要状态和附件。不要把客户端传入的数值当作可信结果；payload 只应传 ID、选择和操作意图。
