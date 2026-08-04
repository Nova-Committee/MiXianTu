package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record OnBlockCondition(BlockCondition condition) implements EntityCondition {
    public static final MapCodec<OnBlockCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(OnBlockCondition::new, OnBlockCondition::condition);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return this.condition.test(entity.level(), entity.blockPosition().below(), context);
    }

    @Override
    public boolean test(Entity entity) {
        return this.test(entity, FormulaContext.EMPTY);
    }

    @Override
    public MapCodec<OnBlockCondition> codec() {
        return CODEC;
    }
}
