package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Strictly requires a non-null common scoreboard team.
 */
public enum SameTeamCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<SameTeamCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return ctx.actor().getTeam() != null && ctx.actor().isAlliedTo(ctx.target());
    }

    @Override
    public @NonNull MapCodec<SameTeamCondition> codec() {
        return CODEC;
    }
}
