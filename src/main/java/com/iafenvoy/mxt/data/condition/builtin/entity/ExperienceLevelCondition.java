package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record ExperienceLevelCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<ExperienceLevelCondition> CODEC = Comparison.CODEC.xmap(ExperienceLevelCondition::new, ExperienceLevelCondition::comparison);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity instanceof Player player && this.comparison.compare(player.experienceLevel);
    }

    @Override
    public MapCodec<ExperienceLevelCondition> codec() {
        return CODEC;
    }
}
