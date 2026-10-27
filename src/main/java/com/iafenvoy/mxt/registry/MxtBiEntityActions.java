package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.builtin.bientity.DamageTargetBiEntityAction;
import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


@SuppressWarnings("unused")
public final class MxtBiEntityActions {
    public static final DeferredRegister<MapCodec<? extends BiEntityAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.BI_ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<DamageTargetBiEntityAction>> DAMAGE_TARGET = REGISTRY.register("damage_target", () -> DamageTargetBiEntityAction.CODEC);
}
