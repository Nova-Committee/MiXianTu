package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.FormationRecipe;
import com.iafenvoy.mxt.recipe.RefiningRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtRecipeTypes {
    /**
     * Recipe types must be entries in the vanilla RECIPE_TYPE registry; a standalone simple instance is not enough.
     */
    public static final DeferredRegister<RecipeType<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlchemyRecipe>> ALCHEMY =
            REGISTRY.register("alchemy", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<FormationRecipe>> FORMATION =
            REGISTRY.register("formation", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "formation")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipe>> REFINING =
            REGISTRY.register("refining", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "refining")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<SpiritShapedRecipe>> SPIRIT_SHAPED =
            REGISTRY.register("spirit_shaped", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shaped")));
    public static final DeferredHolder<RecipeType<?>, RecipeType<SpiritShapelessRecipe>> SPIRIT_SHAPELESS =
            REGISTRY.register("spirit_shapeless", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shapeless")));

    private MxtRecipeTypes() {
    }
}
