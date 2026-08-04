package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record NotCondition(ItemCondition condition) implements ItemCondition {
    public static final MapCodec<NotCondition> CODEC = ItemCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return !this.condition.test(holder, stack, context);
    }

    @Override
    public MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
