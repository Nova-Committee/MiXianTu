package com.iafenvoy.mxt.util.formula;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * An inclusive numeric window whose bounds are independently optional, so one field covers "at least", "at most"
 * and an exact window. An absent bound is no bound at all; a bound that evaluates to something non-finite fails
 * the check rather than opening it, the way the other range conditions fail closed.
 */
public record NumberRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
    public static final Codec<NumberRange> CODEC = RecordCodecBuilder.create(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("min").forGetter(NumberRange::min),
            NumberProvider.CODEC.optionalFieldOf("max").forGetter(NumberRange::max)
    ).apply(i, NumberRange::new));

    public boolean test(double value, FormulaContext context) {
        if (Double.isNaN(value)) return false;
        if (this.min.isPresent()) {
            double min = this.min.get().evaluate(context);
            if (!Double.isFinite(min) || value < min) return false;
        }
        if (this.max.isPresent()) {
            double max = this.max.get().evaluate(context);
            return Double.isFinite(max) && !(value > max);
        }
        return true;
    }
}
