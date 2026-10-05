package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.screen.gui.SpiritCraftingScreen;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.NonNull;

/**
 * JEI integration for the datapack-driven spirit crafting and alchemy recipes.
 */
@JeiPlugin
public final class MxtJeiPlugin implements IModPlugin {
    public static final IRecipeHolderType<SpiritShapedRecipe> SHAPED = IRecipeHolderType.create(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shaped"));
    public static final IRecipeHolderType<SpiritShapelessRecipe> SHAPELESS = IRecipeHolderType.create(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shapeless"));
    public static final IRecipeHolderType<AlchemyRecipe> ALCHEMY = IRecipeHolderType.create(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy"));

    @Override
    public @NonNull Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SpiritShapedCategory(gui), new SpiritShapelessCategory(gui),
                new AlchemyCategory(gui));
    }

    @Override
    public void registerRecipes(@NonNull IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        RecipeManager recipes = minecraft.getSingleplayerServer() == null
                ? null : minecraft.getSingleplayerServer().getRecipeManager();
        if (recipes == null) return;
        registration.addRecipes(SHAPED, recipes.getRecipes().stream()
                .filter(holder -> holder.value() instanceof SpiritShapedRecipe)
                .map(MxtJeiPlugin::<SpiritShapedRecipe>castHolder).toList());
        registration.addRecipes(SHAPELESS, recipes.getRecipes().stream()
                .filter(holder -> holder.value() instanceof SpiritShapelessRecipe)
                .map(MxtJeiPlugin::<SpiritShapelessRecipe>castHolder).toList());
        registration.addRecipes(ALCHEMY, recipes.getRecipes().stream()
                .filter(holder -> holder.value() instanceof AlchemyRecipe)
                .map(MxtJeiPlugin::<AlchemyRecipe>castHolder).toList());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> castHolder(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        Item item = MxtBlocks.SPIRIT_CRAFTING_TABLE.get().asItem();
        registration.addCraftingStation(SHAPED, item);
        registration.addCraftingStation(SHAPELESS, item);
        // Every furnace specification shares this one block item, so it is the only station the category can name.
        registration.addCraftingStation(ALCHEMY, MxtBlocks.ALCHEMY_FURNACE.get().asItem());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(SpiritCraftingScreen.class, 90, 30, 20, 20, SHAPED, SHAPELESS);
    }

    // No click area and no transfer handler for ALCHEMY. The furnace panels are ApricityUI pages centred in the
    // window, so a click area would need fixed coordinates it cannot have; and a batch is judged on summed
    // medicinal properties rather than on which item sits in which slot, so filling from the guide would be a guess.
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(SpiritCraftingMenu.class, MxtMenus.SPIRIT_CRAFTING_TABLE.get(), SHAPED, 1, 9, 10, 36);
        registration.addRecipeTransferHandler(SpiritCraftingMenu.class, MxtMenus.SPIRIT_CRAFTING_TABLE.get(), SHAPELESS, 1, 9, 10, 36);
    }
}
