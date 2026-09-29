package com.iafenvoy.mxt.data.ability.render.builtin;

import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.mojang.serialization.MapCodec;

/**
 * The default: the mount is drawn as the item model it carries, which is what every definition did before the
 * {@code render} field existed.
 */
public enum ItemMountRender implements MountRender {
    INSTANCE;
    public static final MapCodec<ItemMountRender> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<ItemMountRender> codec() {
        return CODEC;
    }
}
