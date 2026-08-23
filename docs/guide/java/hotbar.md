---
title: 客户端 Hotbar 扩展
---

Hotbar 由 `HotbarController` 统一管理，技能和灵气条目都实现 `HotbarEntry`。新增条目类型时直接创建 `HotbarEntry` 实现类，避免静态工厂和匿名行为堆叠。

```java
public final class ExampleEntry implements HotbarEntry {
    @Override public Component name() { return Component.literal("Example"); }
    @Override public void onPress(Player player) { }
    @Override public void onTick(Player player, boolean pressed) { }
    @Override public void onRelease(Player player) { }
}
```
