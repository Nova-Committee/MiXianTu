package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.builtin.block.*;
import com.iafenvoy.mxt.data.condition.builtin.block.meta.*;
import com.iafenvoy.mxt.integration.kubejs.type.condition.JsBlockCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtBlockConditions {
    public static final DeferredRegister<MapCodec<? extends BlockCondition>> REGISTRY = DeferredRegister.create(MxtRegistries.BLOCK_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AlwaysTrueBlockCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<JsBlockCondition>> JS = REGISTRY.register("js", () -> JsBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AndBlockCondition>> AND = REGISTRY.register("and", () -> AndBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BlockIdCondition>> BLOCK_ID = REGISTRY.register("block_id", () -> BlockIdCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AuraRangeBlockCondition>> AURA_RANGE = REGISTRY.register("aura_range", () -> AuraRangeBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BlockTagCondition>> BLOCK_TAG = REGISTRY.register("block_tag", () -> BlockTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BiomeTagBlockCondition>> BIOME_TAG = REGISTRY.register("biome_tag", () -> BiomeTagBlockCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<HardnessCondition>> HARDNESS = REGISTRY.register("hardness", () -> HardnessCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<HeightCondition>> HEIGHT = REGISTRY.register("height", () -> HeightCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<LightLevelCondition>> LIGHT_LEVEL = REGISTRY.register("light_level", () -> LightLevelCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<SlipperinessCondition>> SLIPPERINESS = REGISTRY.register("slipperiness", () -> SlipperinessCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<BlastResistanceCondition>> BLAST_RESISTANCE = REGISTRY.register("blast_resistance", () -> BlastResistanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<MovementBlockingCondition>> MOVEMENT_BLOCKING = REGISTRY.register("movement_blocking", () -> MovementBlockingCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<ChanceCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<ConstantCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<NotCondition>> NOT = REGISTRY.register("not", () -> NotCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<OrCondition>> OR = REGISTRY.register("or", () -> OrCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AdjacentCondition>> ADJACENT = REGISTRY.register("adjacent", () -> AdjacentCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<OffsetCondition>> OFFSET = REGISTRY.register("offset", () -> OffsetCondition.CODEC);

    private MxtBlockConditions() {
    }
}
