package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;

import java.util.Set;

// For formulas that must switch a term off without editing the expression.
public record ZeroVariable() implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("zero");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        return suffix.isEmpty() ? 0.0D : Double.NaN;
    }
}
