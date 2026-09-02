package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Moves an entity within its current level. Cross-dimension travel remains an explicit extension point.
 */
public record TeleportAction(NumberProvider x, NumberProvider y, NumberProvider z) implements EntityAction {
    public static final MapCodec<TeleportAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("x").forGetter(TeleportAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(TeleportAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(TeleportAction::z)
    ).apply(i, TeleportAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) entity.teleportTo(x, y, z);
    }

    @Override
    public @NonNull MapCodec<TeleportAction> codec() {
        return CODEC;
    }
}
