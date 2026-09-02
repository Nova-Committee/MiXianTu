package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the optional resource use gate for entity-driven interactions.
 */
public final class ResourceUseService {
    private ResourceUseService() {
    }

    public static boolean canUse(@NotNull LivingEntity entity, @NotNull Holder<Resource> resource) {
        FormulaContext context = ResourceService.formulaContext(entity, resource, FormulaContext.of(entity));
        return resource.value().useCondition().test(entity, context);
    }
}
