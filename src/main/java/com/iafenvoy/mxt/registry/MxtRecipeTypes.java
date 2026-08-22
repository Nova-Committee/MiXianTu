package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipeAdapter;
import com.iafenvoy.mxt.recipe.FormationRecipeAdapter;
import com.iafenvoy.mxt.recipe.RefiningRecipeAdapter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtRecipeTypes {
    /** Recipe types must be entries in the vanilla RECIPE_TYPE registry; a standalone simple instance is not enough. */
    public static final DeferredRegister<RecipeType<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlchemyRecipeAdapter>> ALCHEMY =
            REGISTRY.register("alchemy", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<FormationRecipeAdapter>> FORMATION =
            REGISTRY.register("formation", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "formation")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipeAdapter>> REFINING =
            REGISTRY.register("refining", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "refining")));

    private MxtRecipeTypes() {
    }
}
