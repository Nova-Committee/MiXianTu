package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;

import java.util.Optional;

public record ScoreboardCondition(Optional<String> name, String objective,
                                  Comparison comparison) implements EntityCondition {
    public static final MapCodec<ScoreboardCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("name").forGetter(ScoreboardCondition::name),
            Codec.STRING.fieldOf("objective").forGetter(ScoreboardCondition::objective),
            Comparison.CODEC.forGetter(ScoreboardCondition::comparison)
    ).apply(i, ScoreboardCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        ScoreHolder holder = ScoreHolder.forNameOnly(this.name.orElse(entity.getScoreboardName()));
        return Optional.ofNullable(entity.level().getScoreboard().getObjective(this.objective))
                .map(objective -> entity.level().getScoreboard().getPlayerScoreInfo(holder, objective))
                .map(ReadOnlyScoreInfo::value).map(this.comparison::compare).orElse(false);
    }

    @Override
    public MapCodec<ScoreboardCondition> codec() {
        return CODEC;
    }
}
