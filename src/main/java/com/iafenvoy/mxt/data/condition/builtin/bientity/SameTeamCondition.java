package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Strictly requires a non-null common scoreboard team.
 */
public enum SameTeamCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<SameTeamCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return actor.getTeam() != null && actor.isAlliedTo(target);
    }

    @Override
    public @NonNull MapCodec<SameTeamCondition> codec() {
        return CODEC;
    }
}
