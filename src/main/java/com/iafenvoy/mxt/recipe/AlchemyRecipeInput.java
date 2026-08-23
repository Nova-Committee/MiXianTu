package com.iafenvoy.mxt.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe input used by alchemy stations, which may contain multiple stacks.
 */
public record AlchemyRecipeInput(List<ItemStack> stacks) implements RecipeInput {
    public AlchemyRecipeInput {
        stacks = stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    public boolean matches(List<Identifier> expected) {
        if (expected.size() != this.stacks.size()) return false;
        List<Identifier> actual = new ArrayList<>(this.stacks.size());
        for (ItemStack stack : this.stacks) actual.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        actual.sort(Identifier::compareTo);
        List<Identifier> required = new ArrayList<>(expected);
        required.sort(Identifier::compareTo);
        return actual.equals(required);
    }
}
