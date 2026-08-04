package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Moves an entity within its current level. Cross-dimension travel remains an explicit domain behavior.
 */
public record TeleportAction(NumberProvider x, NumberProvider y, NumberProvider z) implements EntityAction {
    public static final MapCodec<TeleportAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("x").forGetter(TeleportAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(TeleportAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(TeleportAction::z)
    ).apply(instance, TeleportAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) entity.teleportTo(x, y, z);
    }

    @Override
    public MapCodec<TeleportAction> codec() {
        return CODEC;
    }
}
