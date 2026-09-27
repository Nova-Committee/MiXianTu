package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.recipe.AlchemyRecipe.Role;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed role slots. Empty slots stay so a missing auxiliary cell cannot be read as the catalyst.
 */
public record AlchemyRecipeInput(List<Slot> slots) implements RecipeInput {
    public AlchemyRecipeInput {
        List<Slot> copied = new ArrayList<>(slots.size());
        for (Slot slot : slots) copied.add(new Slot(slot.role(), slot.stack().copy()));
        slots = List.copyOf(copied);
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.slots.get(index).stack();
    }

    @Override
    public int size() {
        return this.slots.size();
    }

    public record Slot(Role role, ItemStack stack) {
    }
}
