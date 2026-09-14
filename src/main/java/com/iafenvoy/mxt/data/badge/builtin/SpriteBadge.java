package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.badge.Badge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A stand-alone icon badge.
 */
public record SpriteBadge(IconReference icon) implements Badge {
    public static final MapCodec<SpriteBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IconReference.CODEC.fieldOf("icon").forGetter(SpriteBadge::icon)
    ).apply(i, SpriteBadge::new));

    @Override
    public MapCodec<SpriteBadge> codec() {
        return CODEC;
    }
}
