package com.iafenvoy.mxt.data.storage.runtime;

import com.iafenvoy.mxt.data.storage.RuntimeStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * The tick the current cast of a host finishes at; the tick the value was written is when that cast started, which
 * is what a cast bar draws against.
 */
public record CastDeadline(double endsAt) implements RuntimeStorage {
    public static final MapCodec<CastDeadline> CODEC = Codec.DOUBLE.fieldOf("ends_at").xmap(CastDeadline::new, CastDeadline::endsAt);

    @Override
    public MapCodec<CastDeadline> codec() {
        return CODEC;
    }
}