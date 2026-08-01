package com.iafenvoy.mxt.util.formula;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Immutable, server-authoritative variables supplied to a number formula.
 */
public record FormulaContext(@NotNull Map<String, Double> variables) {
    public static final FormulaContext EMPTY = new FormulaContext(Map.of());

    public FormulaContext {
        variables = Map.copyOf(variables);
    }

    public double value(String name) {
        return this.variables.getOrDefault(name, 0.0D);
    }

    public boolean contains(String name) {
        return this.variables.containsKey(name);
    }
}
