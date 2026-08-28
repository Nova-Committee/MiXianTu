package com.iafenvoy.mxt.data.context.action;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;

public class EntityActionContext extends Context {
    private final Entity entity;
    private final FormulaContext formula;

    public EntityActionContext(Entity entity, FormulaContext formula) {
        this.entity = entity;
        this.formula = formula;
    }

    public Entity entity() {
        return this.entity;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
