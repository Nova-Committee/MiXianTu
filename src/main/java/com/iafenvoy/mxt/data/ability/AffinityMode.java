package com.iafenvoy.mxt.data.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * How an ability's element affinity is read when more than one active spirit root matches: the best root's own
 * multiplier, or the average over the matching roots. Content's own rule now that Saturn keeps affinity out of the
 * ability definition.
 */
public enum AffinityMode {
    AVERAGE,
    MAX;

    public static final Codec<AffinityMode> CODEC = Codec.STRING.comapFlatMap(
            name -> {
                for (AffinityMode mode : values())
                    if (mode.name().toLowerCase(Locale.ROOT).equals(name)) return DataResult.success(mode);
                return DataResult.error(() -> "Unknown element affinity mode: " + name);
            },
            mode -> mode.name().toLowerCase(Locale.ROOT));
}
