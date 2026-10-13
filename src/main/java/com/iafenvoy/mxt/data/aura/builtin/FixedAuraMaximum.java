package com.iafenvoy.mxt.data.aura.builtin;

import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record FixedAuraMaximum(double value) implements AuraMaximum {
    public static final MapCodec<FixedAuraMaximum> CODEC = Codec.doubleRange(0.0D, Double.MAX_VALUE)
            .fieldOf("value").xmap(FixedAuraMaximum::new, FixedAuraMaximum::value);

    @Override
    public double resolve(double initialAura) {
        return this.value;
    }

    @Override
    public MapCodec<FixedAuraMaximum> codec() {
        return CODEC;
    }
}
