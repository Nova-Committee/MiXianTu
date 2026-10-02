package com.iafenvoy.mxt.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The spirit crafting table's 3x3 grid, plus the vanilla view of it. Vanilla recipes match against a
 * {@link CraftingInput}, which trims itself to the rectangle the items actually occupy, so one pattern means the
 * same thing wherever it is laid out in the grid; the view is built once here rather than per recipe tried.
 */
public final class SpiritCraftingInput implements RecipeInput {
    private static final int SIZE = 3;
    private final List<ItemStack> stacks;
    private final CraftingInput craftingInput;

    public SpiritCraftingInput(List<ItemStack> stacks) {
        if (stacks.size() != SIZE * SIZE)
            throw new IllegalArgumentException("Spirit crafting input must contain nine slots");
        this.stacks = stacks;
        this.craftingInput = CraftingInput.of(SIZE, SIZE, stacks);
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    public CraftingInput craftingInput() {
        return this.craftingInput;
    }
}
