package com.iafenvoy.mxt.data.ability.render;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * How a mount is drawn, chosen by the {@code render} field of {@code mxt:mount}.
 *
 * <p>Data only: this type never draws anything and never resolves a renderer, which is a client-side product the
 * server cannot even load. Both sides decode it, only the client acts on it.
 */
public interface MountRender {
    Codec<MountRender> CODEC = MxtRegistries.MOUNT_RENDER_TYPE.byNameCodec().dispatch("type", MountRender::codec, Function.identity());

    MapCodec<? extends MountRender> codec();
}
