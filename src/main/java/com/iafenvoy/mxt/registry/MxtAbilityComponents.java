package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Charges;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Cooldown;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Empty;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Resource;
import com.iafenvoy.mxt.data.ability.AbilityComponent.TargetLock;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Timer;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Toggle;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Persistent ability state component schemas.
 */
public final class MxtAbilityComponents {
    public static final DeferredRegister<MapCodec<? extends AbilityComponent>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ABILITY_COMPONENT_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Cooldown>> COOLDOWN = REGISTRY.register("cooldown", () -> Cooldown.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Charges>> CHARGES = REGISTRY.register("charges", () -> Charges.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Toggle>> TOGGLE = REGISTRY.register("toggle", () -> Toggle.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Timer>> TIMER = REGISTRY.register("timer", () -> Timer.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Resource>> RESOURCE = REGISTRY.register("resource", () -> Resource.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<TargetLock>> TARGET_LOCK = REGISTRY.register("target_lock", () -> TargetLock.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<Empty>> EMPTY = REGISTRY.register("empty", () -> Empty.CODEC);

    private MxtAbilityComponents() {
    }
}
