package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * Uses the vanilla crafting-table background and slot geometry.
 */
public final class SpiritCraftingScreen extends AbstractContainerScreen<SpiritCraftingMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

    public SpiritCraftingScreen(SpiritCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }
}
