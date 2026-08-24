package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * Uses the vanilla crafting-table background and slot geometry.
 */
public final class SpiritCraftingScreen extends AbstractContainerScreen<SpiritCraftingMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int PANEL_WIDTH = 108;
    private static final int PANEL_HEIGHT = 122;
    private static final int BAR_WIDTH = 94;
    private static final int BAR_HEIGHT = 7;

    public SpiritCraftingScreen(SpiritCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176 + PANEL_WIDTH, 166);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, 176, 166, 256, 256);
        this.extractProgress(graphics);
    }

    private void extractProgress(GuiGraphicsExtractor graphics) {
        int panelX = this.leftPos + 176;
        int panelY = this.topPos + 8;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE0101010);
        graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF303030);
        int row = 0;
        for (int index = 0; index < 8; index++) {
            Holder<Resource> resource = this.menu.progressResource(index);
            if (resource == null) continue;
            int required = this.menu.progressRequirement(index);
            int amount = Math.min(this.menu.progressAmount(index), required);
            int color = 0xFF000000 | resource.value().particleColor();
            int y = panelY + 7 + row * 26;
            Component name = DefinitionText.name(resource, "resource");
            graphics.text(this.font, name, panelX + 7, y, color, false);
            graphics.fill(panelX + 7, y + 11, panelX + 7 + BAR_WIDTH, y + 11 + BAR_HEIGHT, 0xFF303030);
            int filled = required <= 0 ? 0 : Math.round(BAR_WIDTH * amount / (float) required);
            if (filled > 0) graphics.fill(panelX + 7, y + 11, panelX + 7 + filled, y + 11 + BAR_HEIGHT, color);
            graphics.outline(panelX + 7, y + 11, BAR_WIDTH, BAR_HEIGHT, 0xFF000000);
            graphics.text(this.font, Component.literal(amount + " / " + required), panelX + 7, y + 20, 0xFFAAAAAA, false);
            row++;
        }
    }
}
