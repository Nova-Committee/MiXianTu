package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Common contract for recipes accepted by the spirit crafting table.
 */
public interface SpiritRecipe extends Recipe<SpiritCraftingInput> {
    Map<Holder<Resource>, NumberProvider> aura();

    ItemStackTemplate result();

    @Override
    default @NonNull ItemStack assemble(SpiritCraftingInput input) {
        return this.result().create();
    }

    @Override
    default boolean showNotification() {
        return true;
    }

    @Override
    default @NonNull String group() {
        return "mxt.spirit_crafting";
    }

    @Override
    default @NonNull PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default @NonNull RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    default boolean hasAuraCost() {
        return !this.aura().isEmpty();
    }
}
