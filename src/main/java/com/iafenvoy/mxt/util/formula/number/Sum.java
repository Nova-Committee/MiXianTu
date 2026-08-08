package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

import java.util.List;

public record Sum(List<NumberProvider> summands) implements NumberProvider {
    public static final MapCodec<Sum> MAP_CODEC = CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("summands").xmap(Sum::new, Sum::summands);

    @Override
    public double evaluate(FormulaContext context) {
        double result = 0.0D;
        for (NumberProvider summand : this.summands) result += summand.evaluate(context);
        return this.assertFinite(result) ? result : 0.0D;
    }

    @Override
    public MapCodec<Sum> codec() {
        return MAP_CODEC;
    }
}
