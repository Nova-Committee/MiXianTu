package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record NotEntityCondition(EntityCondition condition) implements EntityCondition {
    public static final MapCodec<NotEntityCondition> CODEC = EntityCondition.SINGLE_CODEC.fieldOf("condition").xmap(NotEntityCondition::new, NotEntityCondition::condition);

    @Override
    public boolean test(Entity entity) {
        return !this.condition.test(entity);
    }

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return !this.condition.test(entity, context);
    }

    @Override
    public MapCodec<NotEntityCondition> codec() {
        return CODEC;
    }
}
