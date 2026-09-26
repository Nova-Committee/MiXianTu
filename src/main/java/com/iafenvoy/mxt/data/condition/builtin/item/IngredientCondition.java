package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.NonNull;

public record IngredientCondition(Ingredient ingredient) implements ItemCondition {
    public static final MapCodec<IngredientCondition> CODEC = Ingredient.CODEC.fieldOf("ingredient").xmap(IngredientCondition::new, IngredientCondition::ingredient);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return this.ingredient.test(ctx.stack());
    }

    @Override
    public @NonNull MapCodec<IngredientCondition> codec() {
        return CODEC;
    }
}
