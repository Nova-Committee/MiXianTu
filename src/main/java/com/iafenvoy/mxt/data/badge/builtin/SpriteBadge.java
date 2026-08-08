package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.badge.Badge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * A stand-alone sprite badge.
 */
public record SpriteBadge(Identifier sprite) implements Badge {
    public static final MapCodec<SpriteBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("sprite").forGetter(SpriteBadge::sprite)
    ).apply(i, SpriteBadge::new));

    @Override
    public MapCodec<SpriteBadge> codec() {
        return CODEC;
    }
}
