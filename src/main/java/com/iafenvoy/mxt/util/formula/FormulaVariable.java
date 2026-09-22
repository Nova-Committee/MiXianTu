package com.iafenvoy.mxt.util.formula;

import java.util.Set;

/**
 * One entry of the intrinsic {@code mxt:formula_variable} registry. A variable stores no value: it decomposes a
 * named number out of the objects a {@link FormulaContext} carries, on demand, so an expression only pays for the
 * names it reads. Being code rather than content, a data pack cannot add entries.
 */
public interface FormulaVariable {
    // A name may only be claimed by a single variable.
    Set<String> names();

    // Each prefix already includes its separator. Overlapping prefixes are allowed, asked in registration order.
    default Set<String> prefixes() {
        return Set.of();
    }

    // NaN when the variable does not actually provide that name, e.g. a caster_ name read with no caster.
    double value(String key, String suffix, FormulaContext context);
}
