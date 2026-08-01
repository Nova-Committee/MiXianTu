package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * Fixed server predicate referenced by a cultivate action's start/stop condition ID.
 */
@FunctionalInterface
public interface CultivationCondition {
    boolean test(LivingEntity entity, FormulaContext context);
}
