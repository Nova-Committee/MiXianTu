package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/** Moves an entity within its current level. Cross-dimension travel remains an explicit domain behavior. */
public record TeleportEntityAction(NumberProvider x, NumberProvider y, NumberProvider z) implements EntityAction {
    public static final MapCodec<TeleportEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("x").forGetter(TeleportEntityAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(TeleportEntityAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(TeleportEntityAction::z)
    ).apply(instance, TeleportEntityAction::new));

    @Override public void execute(Entity entity) { this.execute(entity, FormulaContext.EMPTY); }

    @Override public void execute(Entity entity, FormulaContext context) {
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) entity.teleportTo(x, y, z);
    }

    @Override public MapCodec<TeleportEntityAction> codec() { return CODEC; }
}
