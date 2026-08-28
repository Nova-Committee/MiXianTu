package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.iafenvoy.mxt.screen.gui.SpiritCraftingScreen;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.registry.MxtMenus;
import org.jspecify.annotations.NonNull;

/**
 * JEI integration for the datapack-driven spirit crafting recipes.
 */
@JeiPlugin
public final class MxtJeiPlugin implements IModPlugin {
    public static final IRecipeHolderType<SpiritShapedRecipe> SHAPED = IRecipeHolderType.create(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shaped"));
    public static final IRecipeHolderType<SpiritShapelessRecipe> SHAPELESS = IRecipeHolderType.create(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_shapeless"));

    @Override
    public @NonNull Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SpiritShapedCategory(gui), new SpiritShapelessCategory(gui));
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
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(SpiritCraftingScreen.class, 90, 30, 20, 20, SHAPED, SHAPELESS);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(SpiritCraftingMenu.class, MxtMenus.SPIRIT_CRAFTING_TABLE.get(), SHAPED, 1, 9, 10, 36);
        registration.addRecipeTransferHandler(SpiritCraftingMenu.class, MxtMenus.SPIRIT_CRAFTING_TABLE.get(), SHAPELESS, 1, 9, 10, 36);
    }
}
