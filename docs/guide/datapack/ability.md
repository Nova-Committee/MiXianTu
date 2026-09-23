---
title: Ability、Cost 与 Condition
---

Ability 的数值字段均可使用 NumberProvider/表达式。技能类型写在**顶层** `type` 上（如 `{"type": "mxt:active", ...}`，不是嵌套的 `ability` 对象），行为写在 `entity_action` / `bi_entity_action` 中，并通过 `costs` 声明资源或物品消耗。技能**只能在 `data/<命名空间>/mxt/ability/` 里定义一处**（2026-09-23 起取消了"写在法器定义里"的内联写法），别处要用它就在自己的字段里写它的 id 或 `#标签`——法器 `abilities` 就是这么一个字段。

```json
{
  "type": "mxt:active",
  "costs": [{"type": "mxt:resource", "resource": "mxt:spirit_power", "amount": 10}],
  "entity_action": {"type": "mxt:damage", "amount": "8 + level"}
}
```

技能行为由服务端处理，客户端轮盘只发送"选中了哪个技能"（`WheelActionC2SPayload`），授予、条件、消耗与冷却都在服务端判定。

技能一多，就按类别把文件分进子文件夹（`mxt/ability/sword/slash.json` → `example:sword/slash`）：目录会成为 ID 的一部分，分类要在写之前定好。完整规则见 `docs/数据包格式.md` 的「文件位置」。
