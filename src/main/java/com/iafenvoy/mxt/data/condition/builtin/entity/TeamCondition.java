package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Optional;

public record TeamCondition(Optional<String> team) implements EntityCondition {
    public static final MapCodec<TeamCondition> CODEC = Codec.STRING.optionalFieldOf("team").xmap(TeamCondition::new, TeamCondition::team);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        PlayerTeam current = entity.getTeam();
        return current != null && this.team.map(current.getName()::equals).orElse(true);
    }

    @Override
    public MapCodec<TeamCondition> codec() {
        return CODEC;
    }
}
