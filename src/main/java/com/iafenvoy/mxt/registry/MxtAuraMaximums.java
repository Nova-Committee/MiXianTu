package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraMaximum.InitialMultiplier;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Unlimited;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAuraMaximums {
    public static final DeferredRegister<MapCodec<? extends AuraMaximum>> REGISTRY = DeferredRegister.create(MxtRegistries.AURA_MAXIMUM_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<Fixed>> FIXED = REGISTRY.register("fixed", () -> Fixed.CODEC);
    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<InitialMultiplier>> INITIAL_MULTIPLIER = REGISTRY.register("initial_multiplier", () -> InitialMultiplier.CODEC);
    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<Unlimited>> UNLIMITED = REGISTRY.register("unlimited", () -> Unlimited.CODEC);
}
