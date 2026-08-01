package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record DistanceBiEntityCondition(NumberProvider maximum) implements BiEntityCondition {
    public static final MapCodec<DistanceBiEntityCondition> CODEC = NumberProvider.CODEC.fieldOf("maximum").xmap(DistanceBiEntityCondition::new, DistanceBiEntityCondition::maximum);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        double maximum = this.maximum.evaluate(context);
        return Double.isFinite(maximum) && maximum >= 0.0D && actor.distanceToSqr(target) <= maximum * maximum;
    }

    @Override
    public MapCodec<DistanceBiEntityCondition> codec() {
        return CODEC;
    }
}
