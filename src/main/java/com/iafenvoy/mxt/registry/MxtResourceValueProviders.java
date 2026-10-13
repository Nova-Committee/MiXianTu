package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resource.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtResourceValueProviders {
    public static final DeferredRegister<MapCodec<? extends ResourceValueProvider>> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_VALUE_PROVIDER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<CurrentResourceValueProvider>> CURRENT = REGISTRY.register("current", () -> CurrentResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<JsResourceValueProvider>> JS = REGISTRY.register("js", () -> JsResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<MaximumResourceValueProvider>> MAX = REGISTRY.register("max", () -> MaximumResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<RegenResourceValueProvider>> REGEN = REGISTRY.register("regen", () -> RegenResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<MissingResourceValueProvider>> MISSING = REGISTRY.register("missing", () -> MissingResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<EnvironmentConcentrationResourceValueProvider>> ENVIRONMENT_CONCENTRATION = REGISTRY.register("environment_concentration", () -> EnvironmentConcentrationResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<ActualConcentrationResourceValueProvider>> ACTUAL_CONCENTRATION = REGISTRY.register("actual_concentration", () -> ActualConcentrationResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<ConstantResourceValueProvider>> CONSTANT = REGISTRY.register("constant", () -> ConstantResourceValueProvider.CODEC);
}
