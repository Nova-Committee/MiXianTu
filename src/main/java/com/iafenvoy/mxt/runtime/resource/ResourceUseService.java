package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the optional aura use gate for entity-driven interactions.
 */
public final class ResourceUseService {
    private ResourceUseService() {
    }

    // The gate is a field of the aura, so it is asked of the aura rather than of the value the aura is counted
    // in. A value carrying no aura has no gate at all, and asking for one is the caller's decision.
    public static boolean canUse(@NotNull LivingEntity entity, @NotNull Holder<Aura> aura) {
        FormulaContext context = ResourceService.formulaContext(entity, aura.value().resource(), FormulaContext.of(entity));
        return aura.value().useCondition().test(entity, context);
    }
}
