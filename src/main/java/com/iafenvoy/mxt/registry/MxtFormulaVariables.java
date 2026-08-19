package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in context variables. Explicit FormulaContext values take precedence over these resolvers.
 */
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> ZERO = REGISTRY.register("zero", () -> context -> 0.0D);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> RANDOM = REGISTRY.register("random", () -> context -> context.random().nextDouble());

    private MxtFormulaVariables() {
    }
}
