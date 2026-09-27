package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public record HealTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<HealTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(HealTargetBiEntityAction::new, HealTargetBiEntityAction::amount);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        if (ctx.target() instanceof LivingEntity living) {
            double amount = this.amount.evaluate(ctx.formula());
            if (Double.isFinite(amount) && amount > 0.0D) living.heal((float) amount);
        }
    }

    @Override
    public @NonNull MapCodec<HealTargetBiEntityAction> codec() {
        return CODEC;
    }
}
