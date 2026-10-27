package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in formula variables: every entry decomposes a number out of the objects a {@link FormulaContext}
 * carries, while values that belong to no object (damage, a block position, an event payload) stay in the
 * context's explicit value map. A code registry, so data packs cannot add variables.
 */
@SuppressWarnings("unused")
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(SaturnRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);

    public static final DeferredHolder<FormulaVariable, FormulaVariable> ELEMENT = REGISTRY.register("element", ElementVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> REALM = REGISTRY.register("realm", RealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> SECRET_REALM = REGISTRY.register("secret_realm", SecretRealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> LIFESPAN = REGISTRY.register("lifespan", LifespanVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> PILL_TOXICITY = REGISTRY.register("pill_toxicity", PillToxicityVariable::new);
}
