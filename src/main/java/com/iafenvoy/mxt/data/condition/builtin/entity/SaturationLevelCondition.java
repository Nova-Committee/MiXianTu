package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

public record SaturationLevelCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<SaturationLevelCondition> CODEC = Comparison.CODEC.xmap(SaturationLevelCondition::new, SaturationLevelCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof Player player && this.comparison.compare(player.getFoodData().getSaturationLevel());
    }

    @Override
    public @NonNull MapCodec<SaturationLevelCondition> codec() {
        return CODEC;
    }
}
