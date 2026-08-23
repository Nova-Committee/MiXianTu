package com.iafenvoy.mxt.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Fixed nine-slot input used by the spirit crafting table.
 */
public record SpiritCraftingInput(List<ItemStack> stacks) implements RecipeInput {
    public SpiritCraftingInput {
        if (stacks.size() != 9) throw new IllegalArgumentException("Spirit crafting input must contain nine slots");
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public int size() {
        return 9;
    }
}
