package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Constant;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Current;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Maximum;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Missing;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider.Regen;
import com.iafenvoy.mxt.data.resource.JsResourceValueProvider;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceValueProviders {
    public static final DeferredRegister<MapCodec<? extends ResourceValueProvider>> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_VALUE_PROVIDER_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<Current>> CURRENT = REGISTRY.register("current", () -> Current.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<JsResourceValueProvider>> JS = REGISTRY.register("js", () -> JsResourceValueProvider.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<Maximum>> MAX = REGISTRY.register("max", () -> Maximum.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<Regen>> REGEN = REGISTRY.register("regen", () -> Regen.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<Missing>> MISSING = REGISTRY.register("missing", () -> Missing.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceValueProvider>, MapCodec<Constant>> CONSTANT = REGISTRY.register("constant", () -> Constant.CODEC);

    private MxtResourceValueProviders() {
    }
}
