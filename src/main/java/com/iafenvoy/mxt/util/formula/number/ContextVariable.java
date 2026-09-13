package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariables;
import com.iafenvoy.mxt.util.formula.FormulaVariables.Binding;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Resolves a declared variable directly, with a finite fallback for absent context values.
 *
 * <p>The name is bound to the variable that provides it once, because the split never changes;
 * only the value is read per evaluation.</p>
 */
public final class ContextVariable implements NumberProvider {
    private static final MapCodec<ContextVariable> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("variable").forGetter(ContextVariable::variable),
            FINITE_DOUBLE_CODEC.optionalFieldOf("fallback", 0.0D).forGetter(ContextVariable::fallback)
    ).apply(i, ContextVariable::new));

    public static final MapCodec<ContextVariable> MAP_CODEC = RAW_CODEC.validate(variable -> variable.variable().isBlank()
            ? DataResult.error(() -> "Context variable name must not be blank")
            : DataResult.success(variable));

    private final String variable;
    private final double fallback;
    private volatile Binding binding;

    public ContextVariable(String variable, double fallback) {
        this.variable = variable;
        this.fallback = fallback;
    }

    public String variable() {
        return this.variable;
    }

    public double fallback() {
        return this.fallback;
    }

    @Override
    public double evaluate(FormulaContext context) {
        double explicit = context.explicit(this.variable);
        if (!Double.isNaN(explicit)) return this.assertFinite(explicit) ? explicit : 0.0D;
        Binding resolved = this.binding;
        if (resolved == null) this.binding = resolved = FormulaVariables.bind(this.variable);
        // An unknown name is what the fallback exists for; a known name that this context cannot
        // provide keeps the usual reporting.
        return resolved == null ? this.fallback : FormulaVariables.resolve(this.variable, context, resolved);
    }

    @Override
    public MapCodec<ContextVariable> codec() {
        return MAP_CODEC;
    }
}
