package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record DamageTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<DamageTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageTargetBiEntityAction::new, DamageTargetBiEntityAction::amount);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        if (Double.isFinite(amount) && amount > 0.0D) target.hurt(target.damageSources().generic(), (float) amount);
    }

    @Override
    public @NonNull MapCodec<DamageTargetBiEntityAction> codec() {
        return CODEC;
    }
}
