package com.iafenvoy.mxt.util.formula;

import java.util.Set;

/**
 * One entry of the intrinsic {@code mxt:formula_variable} registry. A variable stores no value: it
 * decomposes a named number out of the objects a {@link FormulaContext} carries, on demand, so an expression
 * only pays for the names it reads. Because it is code rather than content, a data pack cannot add entries.
 */
public interface FormulaVariable {
    /**
     * Exact names this variable provides. A name may only be claimed by a single variable.
     */
    Set<String> names();

    /**
     * Name prefixes this variable provides, each already including its separator. Overlapping prefixes are
     * allowed and are asked in registration order.
     */
    default Set<String> prefixes() {
        return Set.of();
    }

    /**
     * Resolves a name this variable claimed, and answers {@link Double#NaN} when it does not actually
     * provide that name — for example a {@code caster_} name read in a context with no caster.
     */
    double value(String key, String suffix, FormulaContext context);
}
