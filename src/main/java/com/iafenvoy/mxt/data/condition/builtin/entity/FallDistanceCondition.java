package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record FallDistanceCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<FallDistanceCondition> CODEC = Comparison.CODEC.xmap(FallDistanceCondition::new, FallDistanceCondition::comparison);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return this.comparison.compare(entity.fallDistance);
    }

    @Override
    public MapCodec<FallDistanceCondition> codec() {
        return CODEC;
    }
}
