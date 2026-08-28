package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Adds a bounded velocity vector; collision and fall handling remain vanilla-owned.
 */
public record KnockbackAction(NumberProvider x, NumberProvider y, NumberProvider z) implements EntityAction {
    public static final MapCodec<KnockbackAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("x").forGetter(KnockbackAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(KnockbackAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(KnockbackAction::z)
    ).apply(i, KnockbackAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) entity.push(x, y, z);
    }

    @Override
    public @NonNull MapCodec<KnockbackAction> codec() {
        return CODEC;
    }
}
