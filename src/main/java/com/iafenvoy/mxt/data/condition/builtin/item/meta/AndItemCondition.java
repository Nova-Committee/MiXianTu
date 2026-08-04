package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record AndItemCondition(List<ItemCondition> conditions) implements ItemCondition {
    public static final MapCodec<AndItemCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndItemCondition::new, AndItemCondition::conditions);

    public AndItemCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.conditions.stream().allMatch(condition -> condition.test(holder, stack, context));
    }

    @Override
    public MapCodec<AndItemCondition> codec() {
        return CODEC;
    }
}
