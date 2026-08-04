package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record ExperiencePointsCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<ExperiencePointsCondition> CODEC = Comparison.CODEC.xmap(ExperiencePointsCondition::new, ExperiencePointsCondition::comparison);

    @Override
    public boolean test(Entity entity) {
        return entity instanceof Player player && this.comparison.compare(player.totalExperience);
    }

    @Override
    public MapCodec<ExperiencePointsCondition> codec() {
        return CODEC;
    }
}
