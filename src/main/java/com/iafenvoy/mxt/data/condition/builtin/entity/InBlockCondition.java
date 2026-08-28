package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record InBlockCondition(BlockCondition blockCondition) implements EntityCondition {
    public static final MapCodec<InBlockCondition> CODEC = BlockCondition.CODEC.fieldOf("block_condition").xmap(InBlockCondition::new, InBlockCondition::blockCondition);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.blockCondition.test(entity.level(), entity.blockPosition(), ctx);
    }

    @Override
    public @NonNull MapCodec<InBlockCondition> codec() {
        return CODEC;
    }
}
