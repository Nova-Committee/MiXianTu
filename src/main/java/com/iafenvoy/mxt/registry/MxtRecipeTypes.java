package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.FormationRecipe;
import com.iafenvoy.mxt.recipe.RefiningRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AlchemyRecipe>> ALCHEMY = register("alchemy");
    public static final DeferredHolder<RecipeType<?>, RecipeType<FormationRecipe>> FORMATION = register("formation");
    public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipe>> REFINING = register("refining");
    public static final DeferredHolder<RecipeType<?>, RecipeType<SpiritShapedRecipe>> SPIRIT_SHAPED = register("spirit_shaped");
    public static final DeferredHolder<RecipeType<?>, RecipeType<SpiritShapelessRecipe>> SPIRIT_SHAPELESS = register("spirit_shapeless");

    private static <T extends Recipe<?>> DeferredHolder<RecipeType<?>, RecipeType<T>> register(String id) {
        return REGISTRY.register(id, () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id)));
    }
}
