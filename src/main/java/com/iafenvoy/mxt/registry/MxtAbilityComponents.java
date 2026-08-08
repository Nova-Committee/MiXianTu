package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.component.ChargesAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.CooldownAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.EmptyAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.ResourceAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.TargetLockAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.TimerAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.ToggleAbilityComponent;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Persistent ability state component schemas.
 */
public final class MxtAbilityComponents {
    public static final DeferredRegister<MapCodec<? extends AbilityComponent>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ABILITY_COMPONENT_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<CooldownAbilityComponent>> COOLDOWN = REGISTRY.register("cooldown", () -> CooldownAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ChargesAbilityComponent>> CHARGES = REGISTRY.register("charges", () -> ChargesAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ToggleAbilityComponent>> TOGGLE = REGISTRY.register("toggle", () -> ToggleAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<TimerAbilityComponent>> TIMER = REGISTRY.register("timer", () -> TimerAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<ResourceAbilityComponent>> RESOURCE = REGISTRY.register("resource", () -> ResourceAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<TargetLockAbilityComponent>> TARGET_LOCK = REGISTRY.register("target_lock", () -> TargetLockAbilityComponent.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityComponent>, MapCodec<EmptyAbilityComponent>> EMPTY = REGISTRY.register("empty", () -> EmptyAbilityComponent.CODEC);

    private MxtAbilityComponents() {
    }
}
