package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Missing;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Radial;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Segmented;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.TextOnly;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarRenderers.Textured;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceBarRenderers {
    public static final DeferredRegister<MapCodec<? extends ResourceBarRenderer>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.RESOURCE_BAR_RENDERER_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderer>, MapCodec<Textured>> TEXTURED = REGISTRY.register("textured_bar", () -> Textured.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderer>, MapCodec<Segmented>> SEGMENTED = REGISTRY.register("segmented_bar", () -> Segmented.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderer>, MapCodec<Radial>> RADIAL = REGISTRY.register("radial_bar", () -> Radial.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderer>, MapCodec<TextOnly>> TEXT_ONLY = REGISTRY.register("text_only", () -> TextOnly.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderer>, MapCodec<Missing>> MISSING = REGISTRY.register("missing", () -> Missing.CODEC);

    private MxtResourceBarRenderers() {
    }
}
