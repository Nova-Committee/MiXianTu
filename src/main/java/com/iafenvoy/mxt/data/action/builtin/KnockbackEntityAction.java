package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Adds a bounded velocity vector; collision and fall handling remain vanilla-owned.
 */
public record KnockbackEntityAction(NumberProvider x, NumberProvider y, NumberProvider z) implements EntityAction {
    public static final MapCodec<KnockbackEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("x").forGetter(KnockbackEntityAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(KnockbackEntityAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(KnockbackEntityAction::z)
    ).apply(instance, KnockbackEntityAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) entity.push(x, y, z);
    }

    @Override
    public MapCodec<KnockbackEntityAction> codec() {
        return CODEC;
    }
}
