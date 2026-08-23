package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.FormationRecipe;
import com.iafenvoy.mxt.recipe.RefiningRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in serializers for the custom recipe types consumed by non-workbench stations.
 */
public final class MxtRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MiXianTu.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlchemyRecipe>> ALCHEMY = REGISTRY.register("alchemy", () -> new RecipeSerializer<>(AlchemyRecipe.CODEC, AlchemyRecipe.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FormationRecipe>> FORMATION = REGISTRY.register("formation", () -> new RecipeSerializer<>(FormationRecipe.CODEC, FormationRecipe.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefiningRecipe>> REFINING = REGISTRY.register("refining", () -> new RecipeSerializer<>(RefiningRecipe.CODEC, RefiningRecipe.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SpiritShapedRecipe>> SPIRIT_SHAPED = REGISTRY.register("spirit_shaped", () -> new RecipeSerializer<>(SpiritShapedRecipe.CODEC, SpiritShapedRecipe.PACKET_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SpiritShapelessRecipe>> SPIRIT_SHAPELESS = REGISTRY.register("spirit_shapeless", () -> new RecipeSerializer<>(SpiritShapelessRecipe.CODEC, SpiritShapelessRecipe.PACKET_CODEC));

    private MxtRecipeSerializers() {
    }
}
