package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Applies generic damage; source-specific damage belongs in a dedicated code-owned action type.
 */
public record DamageEntityAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<DamageEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageEntityAction::new, DamageEntityAction::amount);

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double amount = this.amount.evaluate(context);
        if (Double.isFinite(amount) && amount > 0.0D) entity.hurt(entity.damageSources().generic(), (float) amount);
    }

    @Override
    public MapCodec<DamageEntityAction> codec() {
        return CODEC;
    }
}
