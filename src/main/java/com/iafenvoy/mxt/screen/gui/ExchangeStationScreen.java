package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.runtime.economy.CurrencyValueService.ExchangeOffer;
import com.iafenvoy.mxt.screen.menu.ExchangeStationMenu;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * Stonecutter-style client selector for currency exchange offers.
 */
public final class ExchangeStationScreen extends AbstractContainerScreen<ExchangeStationMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
    private static final Identifier RECIPE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private float scrollOffset;
    private boolean scrolling;
    private int startIndex;
    private boolean displayOffers;

    public ExchangeStationScreen(ExchangeStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        menu.registerUpdateListener(this::containerChanged);
        this.titleLabelY--;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        int scrollerY = (int) (41.0F * this.scrollOffset);
        int scrollerX = this.leftPos + 119;
        int scrollerTop = this.topPos + 15;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.isScrollBarActive() ? SCROLLER : SCROLLER_DISABLED, scrollerX, scrollerTop + scrollerY, 12, 15);
        if (mouseX >= scrollerX && mouseY >= scrollerTop && mouseX < scrollerX + 12 && mouseY < scrollerTop + 54) {
            graphics.requestCursor(this.isScrollBarActive() ? (this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND) : CursorTypes.NOT_ALLOWED);
        }
        this.extractOfferButtons(graphics, mouseX, mouseY);
        this.extractOffers(graphics);
    }

    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!this.displayOffers) return;
        for (int index = this.startIndex; index < this.startIndex + 12 && index < this.menu.getNumberOfVisibleOffers(); index++) {
            int offset = index - this.startIndex;
            int x = this.leftPos + 52 + offset % 4 * 16;
            int y = this.topPos + 16 + offset / 4 * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 18) {
                ExchangeOffer offer = this.menu.getVisibleOffers().get(index);
                graphics.setTooltipForNextFrame(this.font, offer.output(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (this.displayOffers) {
            for (int index = this.startIndex; index < this.startIndex + 12; index++) {
                int offset = index - this.startIndex;
                double x = event.x() - (this.leftPos + 52 + offset % 4 * 16);
                double y = event.y() - (this.topPos + 14 + offset / 4 * 18);
                if (x >= 0.0D && y >= 0.0D && x < 16.0D && y < 18.0D && this.menu.clickMenuButton(this.minecraft.player, index)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
                    return true;
                }
            }
            int x = this.leftPos + 119;
            int y = this.topPos + 9;
            if (event.x() >= x && event.x() < x + 12 && event.y() >= y && event.y() < y + 54) this.scrolling = true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.scrolling || !this.isScrollBarActive()) return super.mouseDragged(event, deltaX, deltaY);
        int top = this.topPos + 14;
        this.scrollOffset = Mth.clamp(((float) event.y() - top - 7.5F) / 39.0F, 0.0F, 1.0F);
        this.startIndex = (int) (this.scrollOffset * this.getOffscreenRows() + 0.5F) * 4;
        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (this.isScrollBarActive()) {
            int rows = this.getOffscreenRows();
            this.scrollOffset = Mth.clamp(this.scrollOffset - (float) scrollY / rows, 0.0F, 1.0F);
            this.startIndex = (int) (this.scrollOffset * rows + 0.5F) * 4;
        }
        return true;
    }

    private void extractOfferButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int index = this.startIndex; index < this.startIndex + 12 && index < this.menu.getNumberOfVisibleOffers(); index++) {
            int offset = index - this.startIndex;
            int x = this.leftPos + 52 + offset % 4 * 16;
            int y = this.topPos + 14 + offset / 4 * 18;
            boolean inRange = mouseX >= x && mouseY >= y && mouseX < x + 16 && mouseY < y + 18;
            Identifier sprite = index == this.menu.getSelectedExchange() ? SELECTED : inRange ? HIGHLIGHTED : RECIPE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 18);
            if (inRange) graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private void extractOffers(GuiGraphicsExtractor graphics) {
        for (int index = this.startIndex; index < this.startIndex + 12 && index < this.menu.getNumberOfVisibleOffers(); index++) {
            int offset = index - this.startIndex;
            graphics.item(this.menu.getVisibleOffers().get(index).output(), this.leftPos + 52 + offset % 4 * 16, this.topPos + 16 + offset / 4 * 18);
        }
    }

    private boolean isScrollBarActive() {
        return this.displayOffers && this.menu.getNumberOfVisibleOffers() > 12;
    }

    private int getOffscreenRows() {
        return (this.menu.getNumberOfVisibleOffers() + 3) / 4 - 3;
    }

    private void containerChanged() {
        this.displayOffers = this.menu.hasInputItem();
        this.scrollOffset = 0.0F;
        this.startIndex = 0;
    }
}
