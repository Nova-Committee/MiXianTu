package com.iafenvoy.mxt.util.formula;

/**
 * Resolves a named value supplied to an exp4j formula when the value was not
 * explicitly written into the current {@link FormulaContext}.
 */
@FunctionalInterface
public interface FormulaVariable {
    double resolve(FormulaContext context);
}
