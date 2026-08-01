package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Mob;

/**
 * Code-owned predicate used by a creature profile's parameterless spawn-condition ID.
 */
@FunctionalInterface
public interface CreatureSpawnCondition {
    boolean test(Mob creature, FormulaContext context);
}
