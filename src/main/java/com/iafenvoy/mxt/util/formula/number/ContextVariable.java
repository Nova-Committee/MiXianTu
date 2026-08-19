package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Resolves a declared variable directly, with a finite fallback for absent context values.
 */
public record ContextVariable(String variable, double fallback) implements NumberProvider {
    public static final MapCodec<ContextVariable> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("variable").forGetter(ContextVariable::variable),
            FINITE_DOUBLE_CODEC.optionalFieldOf("fallback", 0.0D).forGetter(ContextVariable::fallback)
    ).apply(i, ContextVariable::new));

    public ContextVariable {
        if (variable.isBlank() || !Double.isFinite(fallback))
            throw new IllegalArgumentException("Invalid context variable provider");
    }

    @Override
    public double evaluate(FormulaContext context) {
        double value = context.contains(this.variable) ? context.value(this.variable) : this.fallback;
        return this.assertFinite(value) ? value : 0.0D;
    }

    @Override
    public MapCodec<ContextVariable> codec() {
        return MAP_CODEC;
    }
}
