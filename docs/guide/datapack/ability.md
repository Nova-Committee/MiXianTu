---
title: Ability、Cost 与 Condition
---

Ability 的数值字段均可使用 NumberProvider/表达式。技能类型写在**顶层** `type` 上（如 `{"type": "mxt:active", ...}`，不是嵌套的 `ability` 对象），并通过 `costs` 声明资源或物品消耗。四个动作字段（`entity_action` / `target_selector` / `target_condition` / `bi_entity_action`）**由每个会跑动作的类型各自声明、各自在自己的时机跑**（2026-09-27 重设计起）：`mxt:active` 在按下时、`mxt:triggered` 在信号命中时、`mxt:channelled` 每次脉冲、`mxt:aura` 按半径逐目标、`mxt:interval` 按自己的 `interval` 反复跑。上一轮那个"动作只写在 `mxt:interval` 上、别的类型写 `effect` 指过去"的写法**已作废**（`effect` 字段不存在，写了没人读）。技能**只能在 `data/<命名空间>/mxt/ability/` 里定义一处**（2026-09-23 起取消了"写在法器定义里"的内联写法），别处要用它就在自己的字段里写它的 id 或 `#标签`——法器 `abilities` 就是这么一个字段。

```json
{
  "type": "mxt:active",
  "entity_action": {"type": "mxt:damage", "amount": "8 + level"},
  "target_selector": {"type": "mxt:ray", "length": 3},
  "costs": [{"type": "mxt:resource", "resource": "mxt:spirit_power", "amount": 10}]
}
```

```json
{
  "type": "mxt:interval",
  "interval": 40,
  "entity_action": {"type": "mxt:damage", "amount": "8 + level"}
}
```

技能行为由服务端处理，客户端轮盘只发送"选中了哪个技能"（`WheelActionC2SPayload`），授予、条件、消耗与冷却都在服务端判定。

技能一多，就按类别把文件分进子文件夹（`mxt/ability/sword/slash.json` → `example:sword/slash`）：目录会成为 ID 的一部分，分类要在写之前定好。完整规则见 `docs/数据包格式.md` 的「文件位置」。
