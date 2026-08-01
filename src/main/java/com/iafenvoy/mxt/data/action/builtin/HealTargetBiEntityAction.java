package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record HealTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<HealTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(HealTargetBiEntityAction::new, HealTargetBiEntityAction::amount);

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
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
