package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record HealTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<HealTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(HealTargetBiEntityAction::new, HealTargetBiEntityAction::amount);

    @Override
    public void execute(BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        if (target instanceof LivingEntity living) {
            double amount = this.amount.evaluate(context);
            if (Double.isFinite(amount) && amount > 0.0D) living.heal((float) amount);
        }
    }

    @Override
    public MapCodec<HealTargetBiEntityAction> codec() {
        return CODEC;
    }
}
