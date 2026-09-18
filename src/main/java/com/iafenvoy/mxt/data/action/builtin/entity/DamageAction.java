package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Applies generic damage; source-specific damage belongs in a dedicated code-owned action type.
 */
public record DamageAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<DamageAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageAction::new, DamageAction::amount);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        // Damage is a server decision: {@code Entity#hurt} still routes to the server and is deprecated,
        // and an action running on a client level must not pretend it dealt damage.
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (Double.isFinite(amount) && amount > 0.0D)
            entity.hurtServer(level, entity.damageSources().generic(), (float) amount);
    }

    @Override
    public @NonNull MapCodec<DamageAction> codec() {
        return CODEC;
    }
}
