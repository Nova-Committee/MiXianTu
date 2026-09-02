package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record OnBlockCondition(BlockCondition condition) implements EntityCondition {
    public static final MapCodec<OnBlockCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(OnBlockCondition::new, OnBlockCondition::condition);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.condition.test(entity.level(), entity.blockPosition().below(), ctx);
    }

    @Override
    public @NonNull MapCodec<OnBlockCondition> codec() {
        return CODEC;
    }
}
