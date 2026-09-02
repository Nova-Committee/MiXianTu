package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.component.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAbilityComponents {
    public static final DeferredRegister<MapCodec<? extends AbilityComponent>> REGISTRY = DeferredRegister.create(MxtRegistries.ABILITY_COMPONENT_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<EmptyAbilityComponent>> EMPTY = REGISTRY.register("empty", () -> EmptyAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<CooldownAbilityComponent>> COOLDOWN = REGISTRY.register("cooldown", () -> CooldownAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ChargesAbilityComponent>> CHARGES = REGISTRY.register("charges", () -> ChargesAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ToggleAbilityComponent>> TOGGLE = REGISTRY.register("toggle", () -> ToggleAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<TimerAbilityComponent>> TIMER = REGISTRY.register("timer", () -> TimerAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ResourceAbilityComponent>> RESOURCE = REGISTRY.register("resource", () -> ResourceAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<TargetLockAbilityComponent>> TARGET_LOCK = REGISTRY.register("target_lock", () -> TargetLockAbilityComponent.CODEC);
}
