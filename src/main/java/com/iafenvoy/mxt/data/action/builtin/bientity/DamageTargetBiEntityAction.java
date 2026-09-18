package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record DamageTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<DamageTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageTargetBiEntityAction::new, DamageTargetBiEntityAction::amount);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        // Damage is a server decision; the deprecated {@code Entity#hurt} only ever applied on a server
        // anyway, so asking for the server level first is the same behaviour stated outright.
        if (!(target.level() instanceof ServerLevel level)) return;
        if (Double.isFinite(amount) && amount > 0.0D)
            target.hurtServer(level, target.damageSources().generic(), (float) amount);
    }

    @Override
    public @NonNull MapCodec<DamageTargetBiEntityAction> codec() {
        return CODEC;
    }
}
