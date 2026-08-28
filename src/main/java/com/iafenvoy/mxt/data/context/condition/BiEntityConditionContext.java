package com.iafenvoy.mxt.data.context.condition;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;

public class BiEntityConditionContext extends Context {
    private final Entity actor;
    private final Entity target;
    private final FormulaContext formula;

    public BiEntityConditionContext(Entity actor, Entity target, FormulaContext formula) {
        this.actor = actor;
        this.target = target;
        this.formula = formula;
    }

    public Entity actor() {
        return this.actor;
    }

    public Entity target() {
        return this.target;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
