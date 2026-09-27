package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AndBlockCondition(List<BlockCondition> conditions) implements BlockCondition {
    public static final MapCodec<AndBlockCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBlockCondition::new, AndBlockCondition::conditions);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.level(), ctx.pos(), ctx));
    }

    @Override
    public @NonNull MapCodec<AndBlockCondition> codec() {
        return CODEC;
    }
}
