package com.iafenvoy.mxt.data.resourcebar;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Renderer schema selected by datapacks; actual drawing is a client integration responsibility.
 */
public interface ResourceBarRenderer {
    Codec<ResourceBarRenderer> CODEC = MxtTypeRegistries.RESOURCE_BAR_RENDERER_TYPE.byNameCodec().dispatch("type", ResourceBarRenderer::codec, Function.identity());

    MapCodec<? extends ResourceBarRenderer> codec();
}
