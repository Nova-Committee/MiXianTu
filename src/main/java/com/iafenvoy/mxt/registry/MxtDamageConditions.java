package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueDamageCondition;
import com.iafenvoy.mxt.data.condition.builtin.DamageAmountRangeCondition;
import com.iafenvoy.mxt.data.condition.builtin.DirectDamageCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtDamageConditions {
    public static final DeferredRegister<MapCodec<? extends DamageCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.DAMAGE_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<AlwaysTrueDamageCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueDamageCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DamageAmountRangeCondition>> AMOUNT_RANGE = REGISTRY.register("amount_range", () -> DamageAmountRangeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<DirectDamageCondition>> DIRECTNESS = REGISTRY.register("directness", () -> DirectDamageCondition.CODEC);

    private MxtDamageConditions() {
    }
}
