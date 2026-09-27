package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AndBiEntityCondition(List<BiEntityCondition> conditions) implements BiEntityCondition {
    public static final MapCodec<AndBiEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBiEntityCondition::new, AndBiEntityCondition::conditions);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.actor(), ctx.target(), ctx));
    }

    @Override
    public @NonNull MapCodec<AndBiEntityCondition> codec() {
        return CODEC;
    }
}
