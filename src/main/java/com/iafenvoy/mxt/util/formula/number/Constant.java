package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

public record Constant(double value) implements NumberProvider {
    public static final MapCodec<Constant> MAP_CODEC = FINITE_DOUBLE_CODEC.fieldOf("value").xmap(Constant::new, Constant::value);

    public Constant {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Constant value must be finite");
    }

    @Override
    public double evaluate(FormulaContext context) {
        return this.value;
    }

    @Override
    public MapCodec<Constant> codec() {
        return MAP_CODEC;
    }
}
