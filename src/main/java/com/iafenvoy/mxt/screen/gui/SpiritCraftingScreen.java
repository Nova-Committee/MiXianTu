package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * Uses the vanilla crafting-table background and slot geometry.
 */
public final class SpiritCraftingScreen extends AbstractContainerScreen<SpiritCraftingMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final Identifier PROGRESS_BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/spirit_crafting_progress.png");
    private static final Identifier PROGRESS_BAR = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/progress_bar.png");
    private static final int PANEL_WIDTH = 132;
    private static final int PANEL_HEIGHT = 122;
    private static final int PANEL_PADDING = 10;
    private static final int BAR_WIDTH = PANEL_WIDTH - PANEL_PADDING * 2;
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, PROGRESS_BACKGROUND, panelX, panelY, 0.0F, 0.0F,
                PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        int row = 0;
        for (int index = 0; index < 8; index++) {
            Holder<Resource> resource = this.menu.progressResource(index);
            if (resource == null) continue;
            int required = this.menu.progressRequirement(index);
            int amount = Math.min(this.menu.progressAmount(index), required);
            int color = 0xFF000000 | resource.value().particleColor();
            int y = panelY + PANEL_PADDING + row * 26;
            Component name = DefinitionText.name(resource, "resource");
            Component progress = Component.literal(amount + " / " + required);
            int progressX = panelX + PANEL_WIDTH - PANEL_PADDING - this.font.width(progress);
            graphics.text(this.font, name, panelX + PANEL_PADDING, y, color, true);
            graphics.text(this.font, progress, progressX, y, 0xFF404040, false);
            graphics.blit(RenderPipelines.GUI_TEXTURED, PROGRESS_BAR, panelX + PANEL_PADDING, y + 11, 0.0F, 0.0F,
                    BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
            int filled = required <= 0 ? 0 : Math.round(BAR_WIDTH * amount / (float) required);
            if (filled > 0) graphics.fill(panelX + PANEL_PADDING + 2, y + 13,
                    panelX + PANEL_PADDING + 2 + Math.max(0, filled - 4), y + 16, color);
            row++;
        }
    }
}
