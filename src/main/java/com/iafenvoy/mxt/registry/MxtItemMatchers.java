package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.alchemy.HerbTagEntry;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueEntry;
import com.iafenvoy.mxt.runtime.resource.ResourceContainerEntry;
import com.iafenvoy.mxt.runtime.spirit.SpiritStorageEntry;
import com.iafenvoy.mxt.util.matcher.QualityEntry;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtItemMatchers {
    public static final DeferredRegister<MapCodec<? extends Entry>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_MATCHER_ENTRY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<SpiritStorageEntry>> SPIRIT_STORAGE = REGISTRY.register("spirit_storage", () -> SpiritStorageEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<ResourceContainerEntry>> RESOURCE_CONTAINER = REGISTRY.register("resource_container", () -> ResourceContainerEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<HerbTagEntry>> HERB_TAG = REGISTRY.register("herb_tag", () -> HerbTagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<TechniqueEntry>> TECHNIQUE = REGISTRY.register("technique", () -> TechniqueEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<QualityEntry>> QUALITY = REGISTRY.register("quality", () -> QualityEntry.CODEC);
}
