package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Uniform(NumberProvider min, NumberProvider max) implements NumberProvider {
    public static final MapCodec<Uniform> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CODEC.fieldOf("min").forGetter(Uniform::min),
            CODEC.fieldOf("max").forGetter(Uniform::max)
    ).apply(i, Uniform::new));

    @Override
    public double evaluate(FormulaContext context) {
        double lower = this.min.evaluate(context);
        double upper = this.max.evaluate(context);
        if (lower > upper) {
            LOGGER.warn("Number provider Uniform received invalid range {}..{}; using 0", lower, upper);
            return 0.0D;
        }
        double value = lower == upper ? lower : lower + context.random().nextDouble() * (upper - lower);
        return this.assertFinite(value) ? value : 0.0D;
    }

    @Override
    public MapCodec<Uniform> codec() {
        return MAP_CODEC;
    }
}
