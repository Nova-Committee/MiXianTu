package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record TeamBiEntityCondition(boolean sameTeam) implements BiEntityCondition {
    public static final MapCodec<TeamBiEntityCondition> CODEC = Codec.BOOL.optionalFieldOf("same_team", true).xmap(TeamBiEntityCondition::new, TeamBiEntityCondition::sameTeam);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return actor.isAlliedTo(target) == this.sameTeam;
    }

    @Override
    public @NonNull MapCodec<TeamBiEntityCondition> codec() {
        return CODEC;
    }
}
