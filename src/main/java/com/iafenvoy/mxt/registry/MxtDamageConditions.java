package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.builtin.damage.ElementDamageCondition;
import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtDamageConditions {
    public static final DeferredRegister<MapCodec<? extends DamageCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.DAMAGE_CONDITION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends DamageCondition>, MapCodec<ElementDamageCondition>> ELEMENT = REGISTRY.register("element", () -> ElementDamageCondition.CODEC);
}
