package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import com.iafenvoy.mxt.util.formula.variable.EntityVariable;
import com.iafenvoy.mxt.util.formula.variable.LifespanVariable;
import com.iafenvoy.mxt.util.formula.variable.PillToxicityVariable;
import com.iafenvoy.mxt.util.formula.variable.RandomVariable;
import com.iafenvoy.mxt.util.formula.variable.RealmVariable;
import com.iafenvoy.mxt.util.formula.variable.SecretRealmVariable;
import com.iafenvoy.mxt.util.formula.variable.ZeroVariable;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in formula variables: every entry decomposes a number out of the objects a {@link FormulaContext}
 * carries, while values that belong to no object (damage, a block position, an event payload) stay in the
 * context's explicit value map. A code registry, so data packs cannot add variables.
 */
@SuppressWarnings("unused")
public final class MxtFormulaVariables {
    public static final DeferredRegister<FormulaVariable> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_VARIABLE, MiXianTu.MOD_ID);

    public static final DeferredHolder<FormulaVariable, FormulaVariable> ZERO = REGISTRY.register("zero", ZeroVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> RANDOM = REGISTRY.register("random", RandomVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> CASTER = REGISTRY.register("caster", () -> new EntityVariable("caster_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> TARGET = REGISTRY.register("target", () -> new EntityVariable("target_"));
    public static final DeferredHolder<FormulaVariable, FormulaVariable> REALM = REGISTRY.register("realm", RealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> SECRET_REALM = REGISTRY.register("secret_realm", SecretRealmVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> LIFESPAN = REGISTRY.register("lifespan", LifespanVariable::new);
    public static final DeferredHolder<FormulaVariable, FormulaVariable> PILL_TOXICITY = REGISTRY.register("pill_toxicity", PillToxicityVariable::new);
}
