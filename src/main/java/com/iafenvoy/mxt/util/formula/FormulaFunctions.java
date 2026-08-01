package com.iafenvoy.mxt.util.formula;

import net.objecthunter.exp4j.function.Function;

import java.util.List;

/**
 * Deterministic extra functions allowed by the formula language.
 */
final class FormulaFunctions {
    private static final List<Function> FUNCTIONS = List.of(
            new Function("round", 1) {
                @Override
                public double apply(double... arguments) {
                    return Math.round(arguments[0]);
                }
            },
            new Function("clamp", 3) {
                @Override
                public double apply(double... arguments) {
                    return Math.max(arguments[1], Math.min(arguments[0], arguments[2]));
                }
            },
            new Function("min", 2) {
                @Override
                public double apply(double... arguments) {
                    return Math.min(arguments[0], arguments[1]);
                }
            },
            new Function("max", 2) {
                @Override
                public double apply(double... arguments) {
                    return Math.max(arguments[0], arguments[1]);
                }
            }
    );

    private FormulaFunctions() {
    }

    static List<Function> all() {
        return FUNCTIONS;
    }
}
