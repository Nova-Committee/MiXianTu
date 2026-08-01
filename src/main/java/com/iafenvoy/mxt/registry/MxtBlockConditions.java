package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtBlockConditions {
    public static final DeferredRegister<MapCodec<? extends BlockCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.BLOCK_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AlwaysTrueBlockCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AndBlockCondition>> AND = REGISTRY.register("and", () -> AndBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BlockIdCondition>> BLOCK_ID = REGISTRY.register("block_id", () -> BlockIdCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AuraRangeBlockCondition>> AURA_RANGE = REGISTRY.register("aura_range", () -> AuraRangeBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BlockTagCondition>> BLOCK_TAG = REGISTRY.register("block_tag", () -> BlockTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BiomeTagBlockCondition>> BIOME_TAG = REGISTRY.register("biome_tag", () -> BiomeTagBlockCondition.CODEC);

    private MxtBlockConditions() {
    }
}
