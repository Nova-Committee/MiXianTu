package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtCosts {
    public static final DeferredRegister<MapCodec<? extends Cost>> REGISTRY = DeferredRegister.create(SaturnRegistries.COST_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Cost>, MapCodec<AuraCost>> AURA = REGISTRY.register("aura", () -> AuraCost.CODEC);
}
