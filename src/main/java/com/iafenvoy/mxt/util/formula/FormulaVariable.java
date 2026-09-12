package com.iafenvoy.mxt.util.formula;

import java.util.Set;

/**
 * One entry of the intrinsic {@code mxt:formula_variable} registry.
 *
 * <p>A variable does not store a value: it decomposes a named number out of the objects the
 * {@link FormulaContext} carries — the caster, the target, the resource subject or the random
 * source. Nothing is computed until a formula actually reads the name, so an expression only
 * pays for the variables it mentions. Values that are not a property of any object (damage,
 * a block position, a trigger payload) stay in the context's explicit value map instead.</p>
 *
 * <p>A variable claims names in two ways. {@link #names()} lists exact names, and
 * {@link #prefixes()} lists families such as {@code caster_}: a requested name is split at the
 * prefix and only the remainder is handed to the variable. Several variables may claim the same
 * requested name through different prefixes, in which case they are asked in registration order
 * until one of them can provide a value.</p>
 *
 * <p>Because a variable is code rather than content, a data pack cannot add entries.</p>
 */
public interface FormulaVariable {
    /**
     * Exact names this variable provides. A name may only be claimed by a single variable, so a
     * duplicate is reported.
     */
    Set<String> names();

    /**
     * Name prefixes this variable provides, each already including its separator. A name that
     * starts with the prefix is passed on to this variable without that prefix; overlapping
     * prefixes are allowed and are asked in registration order.
     */
    default Set<String> prefixes() {
        return Set.of();
    }

    /**
     * Resolves a name this variable claimed.
     *
     * @param key    the exact name or prefix that matched, so a variable with several names knows
     *               which one was asked for
     * @param suffix what follows the key; empty when the key is the whole name
     * @return the value, or {@link Double#NaN} when this variable does not actually provide the
     * name, for example a {@code caster_} name read in a context that has no caster. The resolver
     * reports a missing value once and continues with {@code 0}, so a variable never logs itself.
     */
    double value(String key, String suffix, FormulaContext context);
}
