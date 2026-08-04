package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record TeamBiEntityCondition(boolean sameTeam) implements BiEntityCondition {
    public static final MapCodec<TeamBiEntityCondition> CODEC = Codec.BOOL.optionalFieldOf("same_team", true).xmap(TeamBiEntityCondition::new, TeamBiEntityCondition::sameTeam);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return actor.isAlliedTo(target) == this.sameTeam;
    }

    @Override
    public MapCodec<TeamBiEntityCondition> codec() {
        return CODEC;
    }
}
