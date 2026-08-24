package com.iafenvoy.mxt.data.resourcebar;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

public interface ResourceBarRenderData {
    Codec<ResourceBarRenderData> CODEC = MxtRegistries.RESOURCE_BAR_RENDER_DATA_TYPE.byNameCodec().dispatch("type", ResourceBarRenderData::codec, Function.identity());

    MapCodec<? extends ResourceBarRenderData> codec();

    default int width() {
        return 71;
    }

    default int height() {
        return 8;
    }
}
