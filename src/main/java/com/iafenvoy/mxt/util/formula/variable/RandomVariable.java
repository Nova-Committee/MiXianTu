package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;

import java.util.Set;

public record RandomVariable() implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("random");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        return suffix.isEmpty() ? context.random().nextDouble() : Double.NaN;
    }
}
