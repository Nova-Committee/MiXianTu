package com.iafenvoy.mxt.data.aura.builtin;

import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record InitialMultiplierAuraMaximum(double multiplier) implements AuraMaximum {
    public static final InitialMultiplierAuraMaximum ONE = new InitialMultiplierAuraMaximum(1.0D);
    public static final MapCodec<InitialMultiplierAuraMaximum> CODEC = Codec.doubleRange(0.0D, Double.MAX_VALUE)
            .optionalFieldOf("multiplier", 1.0D).xmap(InitialMultiplierAuraMaximum::new, InitialMultiplierAuraMaximum::multiplier);

    @Override
    public double resolve(double initialAura) {
        return Math.max(0.0D, initialAura) * this.multiplier;
    }

    @Override
    public MapCodec<InitialMultiplierAuraMaximum> codec() {
        return CODEC;
    }
}
