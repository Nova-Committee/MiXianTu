package com.iafenvoy.mxt.data.context.condition;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.damagesource.DamageSource;

public class DamageConditionContext extends Context {
    private final DamageSource source;
    private final float amount;
    private final FormulaContext formula;

    public DamageConditionContext(DamageSource source, float amount, FormulaContext formula) {
        this.source = source;
        this.amount = amount;
        this.formula = formula;
    }

    public DamageSource source() {
        return this.source;
    }

    public float amount() {
        return this.amount;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
