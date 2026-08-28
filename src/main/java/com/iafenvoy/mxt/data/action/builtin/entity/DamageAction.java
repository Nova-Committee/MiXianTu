package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Applies generic damage; source-specific damage belongs in a dedicated code-owned action type.
 */
public record DamageAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<DamageAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageAction::new, DamageAction::amount);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        if (Double.isFinite(amount) && amount > 0.0D) entity.hurt(entity.damageSources().generic(), (float) amount);
    }

    @Override
    public MapCodec<DamageAction> codec() {
        return CODEC;
    }
}
