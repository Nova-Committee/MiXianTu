package com.iafenvoy.mxt.data.resourcebar;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Pure visibility policy. It cannot mutate resources or access arbitrary client code.
 */
public interface ResourceBarVisibility {
    Codec<ResourceBarVisibility> CODEC = MxtRegistries.RESOURCE_BAR_VISIBILITY_TYPE.byNameCodec().dispatch("type", ResourceBarVisibility::codec, Function.identity());

    boolean visible(ResourceBarView view);

    MapCodec<? extends ResourceBarVisibility> codec();
}
