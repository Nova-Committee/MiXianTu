---
title: 注册表与 Codec
---

固有注册表使用 NeoForge `DeferredRegister<MapCodec<?>>`，数据包通过 `type` 分派到对应实现。动态数据表统一在 `MxtDatapackRegistries` 注册，并使用对应 Definition 的 `DIRECT_CODEC`。

```java
public static final DeferredRegister<MapCodec<? extends Cost>> REGISTRY =
        DeferredRegister.create(MxtRegistries.COST_TYPE, MiXianTu.MOD_ID);
```

Definition 的 Holder Codec 命名为 `CODEC`，直接对象 Codec 命名为 `DIRECT_CODEC`。避免在静态字段初始化顺序中通过反向引用造成循环依赖。
