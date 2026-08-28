package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public record TeamCondition(Optional<String> team) implements EntityCondition {
    public static final MapCodec<TeamCondition> CODEC = Codec.STRING.optionalFieldOf("team").xmap(TeamCondition::new, TeamCondition::team);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        PlayerTeam current = entity.getTeam();
        return current != null && this.team.map(current.getName()::equals).orElse(true);
    }

    @Override
    public @NonNull MapCodec<TeamCondition> codec() {
        return CODEC;
    }
}
