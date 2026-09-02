package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbility;
import org.jspecify.annotations.NonNull;

public record ToolAbilityCondition(ItemAbility ability) implements ItemCondition {
    public static final MapCodec<ToolAbilityCondition> CODEC = ItemAbility.CODEC.fieldOf("ability").xmap(ToolAbilityCondition::new, ToolAbilityCondition::ability);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return stack.canPerformAction(this.ability);
    }

    @Override
    public @NonNull MapCodec<ToolAbilityCondition> codec() {
        return CODEC;
    }
}
