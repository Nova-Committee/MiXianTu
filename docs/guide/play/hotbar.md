---
title: Hotbar、资源条与灵气 HUD
---

技能和灵气释放共用一个客户端 Hotbar。打开其中一个时另一个自动关闭；数字键 1～9 选择条目，多个数字键可以同时按住。默认拦截原版 1～9 快捷栏，鼠标滚轮仍可切换物品栏；客户端配置「技能快捷栏 → 允许数字键」可以放行原版数字键。

每个 `HotbarEntry` 支持：

```java
void onPress(Player player);
void onPressTick(Player player);
void onRelease(Player player);
```

资源条和灵气浓度条由服务端同步状态驱动，客户端只负责绘制。服务端按服务端配置里的「灵气 → 同步周期」（默认每 5 tick）同步当前位置灵气，雾效和粒子不会反向决定灵气数值。
