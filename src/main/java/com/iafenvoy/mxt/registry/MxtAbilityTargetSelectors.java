package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.target.AreaTargetSelector;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAbilityTargetSelectors {
    public static final DeferredRegister<MapCodec<? extends TargetSelector>> REGISTRY = DeferredRegister.create(MxtRegistries.ABILITY_TARGET_SELECTOR_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends TargetSelector>, MapCodec<SelfTargetSelector>> SELF = REGISTRY.register("self", () -> SelfTargetSelector.CODEC);
    public static final DeferredHolder<MapCodec<? extends TargetSelector>, MapCodec<AreaTargetSelector>> AREA = REGISTRY.register("area", () -> AreaTargetSelector.CODEC);
}
