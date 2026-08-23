---
title: 特殊公开接口
---

### `SpiritAccess`

展示架、容器等方块实体实现的灵气访问接口。`add` 和 `extract` 一次只处理一种灵气，并返回操作后剩余量；`simulate=true` 只模拟，不修改状态。

### `SpiritItemAccess`

可充能物品实现的接口。除灵气类型、增加/抽取和模拟参数外，通过 `getCapacity(ItemStack)` 从物品动态计算容量，不能在 Java 中写死容量。

### `TooltipAppender`

物品模块通过 NeoForge `TooltipAppender` 注册 Tooltip。每个模块使用独立 Appender，资源、货币、品质和灵气存储显示互不耦合。

### `Cost`

技能、阵法和其他行为的消耗抽象，提供面向 `Player` 的检查和实际消耗方法。新增 Cost 类型应使用固有注册表分派，而不是在 JSON 中写 Java 类名。

### `HotbarEntry`

纯客户端条目接口，提供名称、可选图标、强调色和 `onPress`、`onTick(Player, boolean)`、`onRelease` 回调。
