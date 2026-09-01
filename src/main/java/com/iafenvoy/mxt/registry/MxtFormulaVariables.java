package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);

    public static final DeferredHolder<FormulaVariable, FormulaVariable> ZERO = REGISTRY.register("zero", () -> _ -> 0);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> RANDOM = REGISTRY.register("random", () -> context -> context.random().nextDouble());
}
