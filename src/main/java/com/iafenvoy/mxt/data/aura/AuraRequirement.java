package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Formula-backed lower and upper bounds for one resource pool.
 */
public record AuraRequirement(NumberProvider min, NumberProvider max) {
    public static final Codec<AuraRequirement> CODEC = RecordCodecBuilder.create(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("min", new Constant(0.0D)).forGetter(AuraRequirement::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(AuraRequirement::max)
    ).apply(i, AuraRequirement::new));

    public boolean test(double value, FormulaContext context) {
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && value >= min && value <= max;
    }
}
