package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.iafenvoy.mxt.data.aura.builtin.FixedAuraMaximum;
import com.iafenvoy.mxt.data.aura.builtin.InitialMultiplierAuraMaximum;
import com.iafenvoy.mxt.data.aura.builtin.UnlimitedAuraMaximum;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAuraMaximums {
    public static final DeferredRegister<MapCodec<? extends AuraMaximum>> REGISTRY = DeferredRegister.create(MxtRegistries.AURA_MAXIMUM_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<FixedAuraMaximum>> FIXED = REGISTRY.register("fixed", () -> FixedAuraMaximum.CODEC);
    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<InitialMultiplierAuraMaximum>> INITIAL_MULTIPLIER = REGISTRY.register("initial_multiplier", () -> InitialMultiplierAuraMaximum.CODEC);
    public static final DeferredHolder<MapCodec<? extends AuraMaximum>, MapCodec<UnlimitedAuraMaximum>> UNLIMITED = REGISTRY.register("unlimited", () -> UnlimitedAuraMaximum.CODEC);
}
