package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.badge.Badge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An icon annotated with a client key mapping identifier.
 */
public record KeybindBadge(IconReference icon, String text, String key) implements Badge {
    public static final MapCodec<KeybindBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IconReference.CODEC.fieldOf("icon").forGetter(KeybindBadge::icon),
            Codec.STRING.optionalFieldOf("text", "").forGetter(KeybindBadge::text),
            Codec.STRING.optionalFieldOf("key", "key.mxt.primary_active").forGetter(KeybindBadge::key)
    ).apply(i, KeybindBadge::new));

    @Override
    public MapCodec<KeybindBadge> codec() {
        return CODEC;
    }
}
