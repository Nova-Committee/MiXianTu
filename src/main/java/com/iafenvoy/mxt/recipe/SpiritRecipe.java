package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface SpiritRecipe extends Recipe<SpiritCraftingInput> {
    /**
     * What one craft takes out of the table's own store, written like every other cost. The pre-Cost aura map
     * is still read as a list of {@code mxt:aura} entries; writing always emits the list form.
     */
    Codec<List<Cost>> AURA_CODEC = Codec.either(
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC), Cost.LIST_CODEC).xmap(
            either -> either.map(values -> values.entrySet().stream()
                    .map(entry -> (Cost) new AuraCost(entry.getKey(), entry.getValue())).toList(), value -> value),
            Either::right).validate(Costs::validateAuras);

    List<Cost> aura();

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
