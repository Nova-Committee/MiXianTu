package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.builtin.block.AuraRangeBlockCondition;
import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtBlockConditions {
    public static final DeferredRegister<MapCodec<? extends BlockCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.BLOCK_CONDITION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends BlockCondition>, MapCodec<AuraRangeBlockCondition>> AURA_RANGE = REGISTRY.register("aura_range", () -> AuraRangeBlockCondition.CODEC);
}
