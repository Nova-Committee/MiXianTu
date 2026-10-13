package com.iafenvoy.mxt.data.aura.builtin;

import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.mojang.serialization.MapCodec;

public enum UnlimitedAuraMaximum implements AuraMaximum {
    INSTANCE;
    public static final MapCodec<UnlimitedAuraMaximum> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double resolve(double initialAura) {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public MapCodec<UnlimitedAuraMaximum> codec() {
        return CODEC;
    }
}
