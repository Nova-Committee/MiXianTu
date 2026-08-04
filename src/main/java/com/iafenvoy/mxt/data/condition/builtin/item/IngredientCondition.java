package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record IngredientCondition(Ingredient ingredient) implements ItemCondition {
    public static final MapCodec<IngredientCondition> CODEC = Ingredient.CODEC.fieldOf("ingredient").xmap(IngredientCondition::new, IngredientCondition::ingredient);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.ingredient.test(stack);
    }

    @Override
    public MapCodec<IngredientCondition> codec() {
        return CODEC;
    }
}
