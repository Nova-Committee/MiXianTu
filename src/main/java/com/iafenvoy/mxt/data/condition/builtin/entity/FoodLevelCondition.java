package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

public record FoodLevelCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<FoodLevelCondition> CODEC = Comparison.CODEC.xmap(FoodLevelCondition::new, FoodLevelCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity() instanceof Player player && this.comparison.compare(player.getFoodData().getFoodLevel());
    }

    @Override
    public @NonNull MapCodec<FoodLevelCondition> codec() {
        return CODEC;
    }
}
