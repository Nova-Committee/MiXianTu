package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipeAdapter;
import com.iafenvoy.mxt.recipe.FormationRecipeAdapter;
import com.iafenvoy.mxt.recipe.RefiningRecipeAdapter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in serializers for recipe-manager references into dynamic definitions.
 */
public final class MxtRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MiXianTu.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlchemyRecipeAdapter>> ALCHEMY = REGISTRY.register("alchemy", () -> new RecipeSerializer<>(AlchemyRecipeAdapter.CODEC, AlchemyRecipeAdapter.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FormationRecipeAdapter>> FORMATION = REGISTRY.register("formation", () -> new RecipeSerializer<>(FormationRecipeAdapter.CODEC, FormationRecipeAdapter.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefiningRecipeAdapter>> REFINING = REGISTRY.register("refining", () -> new RecipeSerializer<>(RefiningRecipeAdapter.CODEC, RefiningRecipeAdapter.PACKET_CODEC));

    private MxtRecipeSerializers() {
    }
}
