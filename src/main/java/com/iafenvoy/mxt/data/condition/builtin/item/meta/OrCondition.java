package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record OrCondition(List<ItemCondition> conditions) implements ItemCondition {
    public static final MapCodec<OrCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrCondition::new, OrCondition::conditions);

    public OrCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.conditions.stream().anyMatch(condition -> condition.test(holder, stack, context));
    }

    @Override
    public MapCodec<OrCondition> codec() {
        return CODEC;
    }
}
