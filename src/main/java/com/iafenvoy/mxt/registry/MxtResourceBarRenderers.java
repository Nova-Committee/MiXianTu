package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.MissingRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.RadialRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.SegmentedRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TextOnlyRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TexturedRenderData;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceBarRenderers {
    public static final DeferredRegister<MapCodec<? extends ResourceBarRenderData>> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_BAR_RENDER_DATA_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<TexturedRenderData>> TEXTURED = REGISTRY.register("textured_bar", () -> TexturedRenderData.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<SegmentedRenderData>> SEGMENTED = REGISTRY.register("segmented_bar", () -> SegmentedRenderData.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<RadialRenderData>> RADIAL = REGISTRY.register("radial_bar", () -> RadialRenderData.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<TextOnlyRenderData>> TEXT_ONLY = REGISTRY.register("text_only", () -> TextOnlyRenderData.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<OriginsRenderData>> ORIGINS = REGISTRY.register("boss_bar", () -> OriginsRenderData.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarRenderData>, MapCodec<MissingRenderData>> MISSING = REGISTRY.register("missing", () -> MissingRenderData.CODEC);

    private MxtResourceBarRenderers() {
    }
}
