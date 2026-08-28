package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AndItemCondition(List<ItemCondition> conditions) implements ItemCondition {
    public static final MapCodec<AndItemCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndItemCondition::new, AndItemCondition::conditions);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().allMatch(condition -> condition.test(holder, stack, ctx));
    }

    @Override
    public @NonNull MapCodec<AndItemCondition> codec() {
        return CODEC;
    }
}
