package com.iafenvoy.mxt.data.ability.target;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * How a capped selector picks its share of the candidates: by distance from the activation's own place, or without
 * regard to it.
 */
public enum TargetOrder {
    NEAREST,
    FARTHEST,
    RANDOM;

    public static final Codec<TargetOrder> CODEC = Codec.STRING.xmap(
            value -> valueOf(value.toUpperCase(Locale.ROOT)),
            value -> value.name().toLowerCase(Locale.ROOT)
    );
}
