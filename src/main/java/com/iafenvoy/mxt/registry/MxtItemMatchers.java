package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.builtin.RegexEntry;
import com.iafenvoy.mxt.util.matcher.builtin.TagEntry;
import com.iafenvoy.mxt.util.matcher.builtin.WildcardEntry;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtItemMatchers {
    public static final DeferredRegister<MapCodec<? extends Entry>> REGISTRY = DeferredRegister.create(MxtRegistries.ITEM_MATCHER_ENTRY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<ItemEntry>> ITEM = REGISTRY.register("item", () -> ItemEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<TagEntry>> TAG = REGISTRY.register("tag", () -> TagEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<WildcardEntry>> WILDCARD = REGISTRY.register("wildcard", () -> WildcardEntry.CODEC);
    public static final DeferredHolder<MapCodec<? extends Entry>, MapCodec<RegexEntry>> REGEX = REGISTRY.register("regex", () -> RegexEntry.CODEC);
}
