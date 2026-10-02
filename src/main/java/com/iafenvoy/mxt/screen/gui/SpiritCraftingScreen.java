package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.util.DefinitionText;
import com.sighs.apricityui.init.Element;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla crafting grid plus the aura progress panel, both drawn by one page. The progress rows are
 * the menu's own data slots; a row with no aura is cleared rather than hidden, so the page keeps the old
 * fixed 8-row layout.
 */
public final class SpiritCraftingScreen extends AuiContainerScreen<SpiritCraftingMenu> {
    private static final int PANEL_WIDTH = 308, PANEL_HEIGHT = 166;
    /**
     * Rows the page provides; the menu publishes at most this many.
     */
    private static final int ROWS = 8;
    private static final int BAR_WIDTH = 112;
    /**
     * The interior the filled part is drawn in: 2px in from both sides, hence the 4px shortfall.
     */
    private static final int BAR_INSET = 2;

    private final List<Row> rows = new ArrayList<>(ROWS);

    public SpiritCraftingScreen(SpiritCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.page(AuiPages.SPIRIT_CRAFTING, "spirit_crafting");
    }

    @Override
    protected String pageName() {
        return "spirit_crafting";
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");

        List<Element> names = this.byIdPrefix("aura_name-", ROWS);
        List<Element> amounts = this.byIdPrefix("aura_amount-", ROWS);
        List<Element> fills = this.byIdPrefix("aura_fill-", ROWS);
        // Menu slot order: result, the nine grid cells, then the player inventory.
        this.bindCells("result", 0);
        this.bindCells("crafting", 1);
        this.bindInventoryCells("inventory");
        for (int index = 0; index < ROWS; index++) {
            this.rows.add(new Row(names.get(index), amounts.get(index), fills.get(index)));
        }
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }

    @Override
    public void onBindingsCleared() {
        this.rows.clear();
    }

    @Override
    public void onPageBound() {
        this.refresh();
    }

    @Override
    protected void refresh() {
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            Holder<Aura> aura = this.menu.progressAura(index);
            if (aura == null) {
                row.clear();
                continue;
            }
            int required = this.menu.progressRequirement(index);
            int amount = Math.min(this.menu.progressAmount(index), required);
            // The aura's own particle colour, lifted so it stays readable on the dark skin.
            String color = String.format("#%06X",
                    AuiStyles.readableOnDark(aura.value().resource().value().particleColor()));
            row.show(DefinitionText.name(aura, "aura").getString(), amount + " / " + required, color,
                    required <= 0 ? 0 : Math.round(BAR_WIDTH * amount / (float) required));
        }
    }

    /**
     * One aura row: a name, a right-aligned "have / need" and the filled part of the bar. Every field is
     * cached because writing to the DOM re-runs its style pass.
     */
    private static final class Row {
        private final Element name;
        private final Element amount;
        private final Element fill;
        @Nullable
        private String shownName;
        @Nullable
        private String shownAmount;
        @Nullable
        private String shownColor;
        private int shownFill = -1;

        private Row(Element name, Element amount, Element fill) {
            this.name = name;
            this.amount = amount;
            this.fill = fill;
        }

        private void show(String name, String amount, String color, int filled) {
            if (!name.equals(this.shownName)) {
                AuiElements.setText(this.name, name);
                this.shownName = name;
            }
            if (!color.equalsIgnoreCase(this.shownColor)) {
                AuiElements.style(this.name, "color", color);
                AuiElements.style(this.fill, "background-color", color);
                this.shownColor = color;
            }
            if (!amount.equals(this.shownAmount)) {
                AuiElements.setText(this.amount, amount);
                this.shownAmount = amount;
            }
            // The old screen filled from 2px in and stopped 2px short of the right edge.
            int width = Math.max(0, filled - BAR_INSET * 2);
            if (width == this.shownFill) return;
            AuiElements.style(this.fill, "width", width + "px");
            this.shownFill = width;
        }

        private void clear() {
            if (this.shownName == null && this.shownAmount == null && this.shownFill == 0) return;
            if (this.shownName != null) {
                AuiElements.setText(this.name, "");
                this.shownName = null;
            }
            if (this.shownAmount != null) {
                AuiElements.setText(this.amount, "");
                this.shownAmount = null;
            }
            if (this.shownFill != 0) {
                AuiElements.style(this.fill, "width", "0px");
                this.shownFill = 0;
            }
        }
    }
}
