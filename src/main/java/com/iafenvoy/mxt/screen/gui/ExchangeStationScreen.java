package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.runtime.economy.CurrencyValueService.ExchangeOffer;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.menu.ExchangeStationMenu;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stonecutter-style client selector for currency exchange offers: the panel, the twelve offer cells and the
 * scroller are the bundled page's, while the slots, their items and their tooltips stay vanilla. The offer list
 * is derived on the client from whatever sits in the input slot (see {@link ExchangeStationMenu}), so a cell
 * click only has to turn into the same menu button press the old screen sent.
 */
public final class ExchangeStationScreen extends AuiContainerScreen<ExchangeStationMenu> {
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    private static final int INPUT_SLOT = 0, RESULT_SLOT = 1;
    /**
     * The visible window: four columns of three.
     */
    private static final int OFFER_CELLS = 12;
    /**
     * The old scroller: a 15px thumb travelling 41px inside a 54px track, dragged over a 39px span.
     */
    private static final float SCROLLER_TRAVEL = 41.0F, SCROLLER_DRAG_SPAN = 39.0F;
    private final List<OfferCell> offerCells = new ArrayList<>(OFFER_CELLS);
    @Nullable
    private Element scroller;
    private float scrollOffset;
    private boolean scrolling;
    private int startIndex;
    private boolean displayOffers;

    public ExchangeStationScreen(ExchangeStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        menu.registerUpdateListener(this::containerChanged);
    }

    @Override
    protected String pagePath() {
        return AuiPages.economyPage("exchange");
    }

    @Override
    protected String pageName() {
        return "exchange";
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.scroller = this.getOrThrow("scroller");

        this.offerCells.clear();
        List<Element> roots = this.byIdPrefix("offer-", OFFER_CELLS);
        for (int cell = 0; cell < OFFER_CELLS; cell++) {
            Item icon = this.getOrThrow("offer_item-" + cell, Item.class);
            this.offerCells.add(new OfferCell(roots.get(cell), icon));
            int offset = cell;
            // The window start is read when the click happens, not when the cell was bound.
            this.click(roots.get(cell), () -> this.choose(this.startIndex + offset));
        }
        this.bindCells("input", INPUT_SLOT);
        this.bindCells("result", RESULT_SLOT);
        this.bindInventoryCells("inventory");
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }

    @Override
    public void onBindingsCleared() {
        this.offerCells.clear();
        this.scroller = null;
    }

    @Override
    public void onPageBound() {
        // Pushes the first state straight away, so the twelve cells are not all painted empty for one tick.
        this.refresh();
    }

    @Override
    protected void refresh() {
        // Recomputed every tick rather than only from the menu's update callback: the offers are derived on the
        // client from the input stack, and the first refresh can land before that callback fires.
        this.displayOffers = this.menu.hasInputItem();
        List<ExchangeOffer> offers = this.menu.getVisibleOffers();
        for (int cell = 0; cell < this.offerCells.size(); cell++) {
            int index = this.startIndex + cell;
            boolean visible = this.displayOffers && index < offers.size();
            this.offerCells.get(cell).show(visible ? offers.get(index).output() : ItemStack.EMPTY,
                    visible && index == this.menu.getSelectedExchange(), visible);
        }
        if (this.scroller == null) return;
        AuiElements.setClass(this.scroller, "disabled", !this.isScrollBarActive());
        AuiElements.style(this.scroller, "top", Math.round(SCROLLER_TRAVEL * this.scrollOffset) + "px");
    }

    // ------------------------------------------------------------------ input: the scroll bar only

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - (this.leftPos + 119);
        double y = event.y() - (this.topPos + 9);
        if (this.displayOffers && x >= 0.0D && x < 12.0D && y >= 0.0D && y < 54.0D) this.scrolling = true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.scrolling || !this.isScrollBarActive()) return super.mouseDragged(event, deltaX, deltaY);
        this.scrollOffset = Mth.clamp((float) (event.y() - (this.topPos + 14) - 7.5D) / SCROLLER_DRAG_SPAN, 0.0F, 1.0F);
        this.startIndex = (int) (this.scrollOffset * this.getOffscreenRows() + 0.5F) * 4;
        this.refresh();
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
            this.refresh();
        }
        return true;
    }

    /**
     * One offer cell becoming the menu button press the server expects; the old screen also played a click.
     */
    private void choose(int index) {
        if (index < 0 || index >= this.menu.getNumberOfVisibleOffers()) return;
        if (this.minecraft.player == null || this.minecraft.gameMode == null) return;
        if (!this.menu.clickMenuButton(this.minecraft.player, index)) return;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
        this.refresh();
    }

    private boolean isScrollBarActive() {
        return this.displayOffers && this.menu.getNumberOfVisibleOffers() > OFFER_CELLS;
    }

    private int getOffscreenRows() {
        return (this.menu.getNumberOfVisibleOffers() + 3) / 4 - 3;
    }

    private void containerChanged() {
        this.scrollOffset = 0.0F;
        this.startIndex = 0;
        this.refresh();
    }

    /**
     * One offer cell: the icon element is fed the offer's output stack and the classes carry the states.
     */
    private static final class OfferCell {
        private final Element root;
        private final Item icon;
        private ItemStack shownStack = ItemStack.EMPTY;
        private boolean shownFilled;
        private boolean shownSelected;

        private OfferCell(Element root, Item icon) {
            this.root = root;
            this.icon = icon;
        }

        private void show(ItemStack stack, boolean selected, boolean filled) {
            if (!ItemStack.matches(stack, this.shownStack)) {
                if (stack.isEmpty()) this.icon.clearDrivenState(Item.Source.INGREDIENT);
                else this.icon.setIngredientStack(stack);
                this.shownStack = stack;
            }
            if (filled != this.shownFilled) {
                // "Holds an item" is the theme's icon toggle for the cell: `icon-item` is what `cell .icon` shows on.
                AuiElements.setClass(this.root, "icon-item", filled);
                AuiElements.setClass(this.root, "empty", !filled);
                this.shownFilled = filled;
            }
            if (selected == this.shownSelected) return;
            AuiElements.setClass(this.root, "selected", selected);
            this.shownSelected = selected;
        }
    }
}