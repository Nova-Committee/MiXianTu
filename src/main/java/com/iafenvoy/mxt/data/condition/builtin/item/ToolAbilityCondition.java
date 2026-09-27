package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.ItemAbility;
import org.jspecify.annotations.NonNull;

public record ToolAbilityCondition(ItemAbility ability) implements ItemCondition {
    public static final MapCodec<ToolAbilityCondition> CODEC = ItemAbility.CODEC.fieldOf("ability").xmap(ToolAbilityCondition::new, ToolAbilityCondition::ability);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ctx.stack().canPerformAction(this.ability);
    }

    @Override
    public @NonNull MapCodec<ToolAbilityCondition> codec() {
        return CODEC;
    }
}
