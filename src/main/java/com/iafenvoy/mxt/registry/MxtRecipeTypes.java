package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipeAdapter;
import com.iafenvoy.mxt.recipe.FormationRecipeAdapter;
import com.iafenvoy.mxt.recipe.RefiningRecipeAdapter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;

public final class MxtRecipeTypes {
    public static final RecipeType<AlchemyRecipeAdapter> ALCHEMY = RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy"));
    public static final RecipeType<FormationRecipeAdapter> FORMATION = RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "formation"));
    public static final RecipeType<RefiningRecipeAdapter> REFINING = RecipeType.simple(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "refining"));

    private MxtRecipeTypes() {
    }
}
