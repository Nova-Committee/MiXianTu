package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A matcher entry that asks a whole ingredient, so everything an ingredient can say - a tag, a list, or a custom
 * type such as {@code mxt:quality} - can be written where a matcher entry goes.
 */
public record IngredientEntry(Ingredient ingredient) implements Entry {
    public static final MapCodec<IngredientEntry> CODEC = Ingredient.CODEC.fieldOf("ingredient")
            .xmap(IngredientEntry::new, IngredientEntry::ingredient);

    @Override
    public boolean matches(ItemStack stack) {
        return this.ingredient.test(stack);
    }

    // A simple ingredient is decided by the item alone; a custom one is free to read the stack, which is what the
    // per-item index may not cache.
    @Override
    public boolean itemLevel() {
        return this.ingredient.isSimple();
    }

    @Override
    public MapCodec<IngredientEntry> codec() {
        return CODEC;
    }
}
