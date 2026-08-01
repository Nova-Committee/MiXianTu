package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.condition.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtItemConditions {
    public static final DeferredRegister<MapCodec<? extends ItemCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ITEM_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<AlwaysTrueItemCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<AndItemCondition>> AND = REGISTRY.register("and", () -> AndItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemIdCondition>> ITEM_ID = REGISTRY.register("item_id", () -> ItemIdCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<OwnedByItemCondition>> OWNED_BY = REGISTRY.register("owned_by", () -> OwnedByItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ArtifactEnergyRangeItemCondition>> ENERGY_RANGE = REGISTRY.register("energy_range", () -> ArtifactEnergyRangeItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemTagCondition>> ITEM_TAG = REGISTRY.register("item_tag", () -> ItemTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemMatcherCondition>> ITEM_MATCHER = REGISTRY.register("item_matcher", () -> ItemMatcherCondition.CODEC);

    private MxtItemConditions() {
    }
}
