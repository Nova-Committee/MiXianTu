package com.iafenvoy.mxt.util.formula;

/**
 * Raised for formula problems that a development environment treats as fatal.
 * Production reports the same problem as a single warning and substitutes {@code 0}.
 *
 * @see FormulaDiagnostics
 */
public final class FormulaException extends RuntimeException {
    public FormulaException(String message) {
        super(message);
    }
}
