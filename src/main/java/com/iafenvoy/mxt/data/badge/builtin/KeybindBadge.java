package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.badge.Badge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * A sprite annotated with a client key mapping identifier.
 */
public record KeybindBadge(Identifier sprite, String text, String key) implements Badge {
    public static final MapCodec<KeybindBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("sprite").forGetter(KeybindBadge::sprite),
            Codec.STRING.optionalFieldOf("text", "").forGetter(KeybindBadge::text),
            Codec.STRING.optionalFieldOf("key", "key.mxt.primary_active").forGetter(KeybindBadge::key)
    ).apply(i, KeybindBadge::new));

    @Override
    public MapCodec<KeybindBadge> codec() {
        return CODEC;
    }
}
