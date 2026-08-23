package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.recipe.SpiritShapedRecipe;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.NonNull;

/**
 * JEI view matching the spirit crafting table's 3x3 shaped grid.
 */
final class SpiritShapedCategory extends AbstractRecipeCategory<RecipeHolder<SpiritShapedRecipe>> {
    private final IDrawable arrow;

    SpiritShapedCategory(IGuiHelper gui) {
        super(MxtJeiPlugin.SHAPED, Component.translatable("jei.mxt.spirit_shaped"),
                gui.createDrawableItemStack(new ItemStack(MxtBlocks.SPIRIT_CRAFTING_TABLE.get().asItem())), 140, 88);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NonNull IRecipeLayoutBuilder builder, RecipeHolder<SpiritShapedRecipe> holder, @NonNull IFocusGroup focuses) {
        SpiritShapedRecipe recipe = holder.value();
        IRecipeSlotBuilder[] grid = new IRecipeSlotBuilder[9];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                grid[y * 3 + x] = builder.addInputSlot(10 + x * 18, 8 + y * 18).setStandardSlotBackground();
            }
        }
        int height = recipe.pattern().size();
        int width = recipe.pattern().stream().mapToInt(String::length).max().orElse(0);
        int offsetX = (3 - width) / 2;
        int offsetY = (3 - height) / 2;
        for (int y = 0; y < height; y++) {
            String row = recipe.pattern().get(y);
            for (int x = 0; x < row.length(); x++) {
                Ingredient ingredient = recipe.key().get(String.valueOf(row.charAt(x)));
                if (ingredient != null) grid[(y + offsetY) * 3 + x + offsetX].add(ingredient);
            }
        }
        builder.addOutputSlot(106, 26).setOutputSlotBackground().add(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<SpiritShapedRecipe> holder, @NonNull IRecipeSlotsView slots, @NonNull GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        this.arrow.draw(graphics, 76, 26);
        int y = 62;
        for (Component line : SpiritJeiText.auraLines(holder.value().aura(), font, 124)) {
            graphics.text(font, line, 8, y, 0xFF666666, false);
            y += 10;
        }
    }
}
