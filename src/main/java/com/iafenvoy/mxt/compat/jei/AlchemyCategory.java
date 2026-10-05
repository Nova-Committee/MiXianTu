package com.iafenvoy.mxt.compat.jei;

import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI view for {@code mxt:alchemy}. The input slots are the pack's own {@code guide}, which is an example
 * composition and never a match condition, so a recipe without one is still listed and can still be found through
 * its outputs; what the furnace actually asks for is the threshold panel below.
 *
 * <p>There is no recipe transfer button: a batch is judged on summed medicinal properties, not on which item sits in
 * which slot, and the guide is not required to satisfy the recipe, so filling the furnace from it would be a guess.
 */
final class AlchemyCategory extends AbstractRecipeCategory<RecipeHolder<AlchemyRecipe>> {
    /**
     * JEI fits a second recipe onto the page only when the category is short enough, so the height is the number
     * that matters. The vertical budget: labels 4..13, the sample row 16..34, the requirement panel starting at 40
     * and ending at 119 (eight lines), and the failure row ending at 104.
     */
    private static final int WIDTH = 172;
    private static final int HEIGHT = 122;
    private static final int LABEL_Y = 4;
    private static final int GUIDE_X = 6;
    private static final int GUIDE_Y = 16;
    private static final int MAX_GUIDE = 5;
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 16;
    private static final int FAILURE_LABEL_Y = 56;
    private static final int FAILURE_Y = 68;
    private static final int TEXT_X = 6;
    private static final int TEXT_Y = 40;
    private static final int TEXT_WIDTH = 116;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = 8;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int TEXT_COLOR = 0xFF666666;

    private final IDrawable arrow;

    AlchemyCategory(IGuiHelper gui) {
        super(MxtJeiPlugin.ALCHEMY, Component.translatable("jei.mxt.alchemy"),
                gui.createDrawableItemStack(MxtBlocks.ALCHEMY_FURNACE.toStack()), WIDTH, HEIGHT);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NonNull IRecipeLayoutBuilder builder, RecipeHolder<AlchemyRecipe> holder, @NonNull IFocusGroup focuses) {
        AlchemyRecipe recipe = holder.value();
        List<ItemStackTemplate> guide = new ArrayList<>();
        recipe.guide().ifPresent(sample -> {
            guide.addAll(sample.main());
            guide.addAll(sample.auxiliary());
            guide.addAll(sample.catalyst());
        });
        for (int index = 0; index < guide.size() && index < MAX_GUIDE; index++)
            builder.addInputSlot(GUIDE_X + index * 18, GUIDE_Y).setStandardSlotBackground().add(guide.get(index));
        addOutputs(builder, recipe.successOutputs(), OUTPUT_Y);
        addOutputs(builder, recipe.failureOutputs(), FAILURE_Y);
    }

    // Two columns: four outputs fit in the band beside the sample composition, and a background wide enough for a
    // vanilla output slot would overlap its neighbour at this spacing.
    private static void addOutputs(IRecipeLayoutBuilder builder, List<ItemStackTemplate> outputs, int y) {
        for (int index = 0; index < outputs.size(); index++)
            builder.addOutputSlot(OUTPUT_X + index % 2 * 18, y + index / 2 * 18)
                    .setStandardSlotBackground().add(outputs.get(index));
    }

    @Override
    public void draw(RecipeHolder<AlchemyRecipe> holder, @NonNull IRecipeSlotsView slots, @NonNull GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        AlchemyRecipe recipe = holder.value();
        this.arrow.draw(graphics, 100, 20);
        graphics.text(font, Component.translatable(recipe.guide().isPresent()
                ? "jei.mxt.alchemy.guide" : "jei.mxt.alchemy.no_guide"), GUIDE_X, LABEL_Y, LABEL_COLOR, false);
        graphics.text(font, Component.translatable("jei.mxt.alchemy.outputs"), OUTPUT_X, LABEL_Y, LABEL_COLOR, false);
        if (!recipe.failureOutputs().isEmpty())
            graphics.text(font, Component.translatable("jei.mxt.alchemy.failure_outputs"),
                    OUTPUT_X, FAILURE_LABEL_Y, LABEL_COLOR, false);
        int y = TEXT_Y;
        for (Component line : AlchemyJeiText.requirementLines(recipe, font, TEXT_WIDTH, MAX_LINES)) {
            graphics.text(font, line, TEXT_X, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }
}
