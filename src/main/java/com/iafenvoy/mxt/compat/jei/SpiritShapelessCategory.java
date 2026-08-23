package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.recipe.SpiritShapelessRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.NonNull;

/**
 * JEI view matching the spirit crafting table's shapeless grid.
 */
final class SpiritShapelessCategory extends AbstractRecipeCategory<RecipeHolder<SpiritShapelessRecipe>> {
    private final IDrawable arrow;

    SpiritShapelessCategory(IGuiHelper gui) {
        super(MxtJeiPlugin.SHAPELESS, Component.translatable("jei.mxt.spirit_shapeless"),
                gui.createDrawableItemStack(new ItemStack(MxtBlocks.SPIRIT_CRAFTING_TABLE.get().asItem())), 140, 88);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NonNull IRecipeLayoutBuilder builder, RecipeHolder<SpiritShapelessRecipe> holder, @NonNull IFocusGroup focuses) {
        SpiritShapelessRecipe recipe = holder.value();
        IRecipeSlotBuilder[] grid = new IRecipeSlotBuilder[9];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                grid[y * 3 + x] = builder.addInputSlot(10 + x * 18, 8 + y * 18).setStandardSlotBackground();
            }
        }
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            if (index < grid.length) grid[index].add(recipe.ingredients().get(index));
        }
        builder.addOutputSlot(106, 26).setOutputSlotBackground().add(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<SpiritShapelessRecipe> holder, @NonNull IRecipeSlotsView slots, @NonNull GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        this.arrow.draw(graphics, 76, 26);
        int y = 62;
        for (Component line : SpiritJeiText.auraLines(holder.value().aura(), font, 124)) {
            graphics.text(font, line, 8, y, 0xFF666666, false);
            y += 10;
        }
    }
}
