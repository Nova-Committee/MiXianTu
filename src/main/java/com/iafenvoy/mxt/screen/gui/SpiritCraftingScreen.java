package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.screen.AuiContainerScreen;
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.AuiStyles;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.util.DefinitionText;
import com.sighs.apricityui.init.Document;
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
    private static final int PANEL_WIDTH = 308;
    private static final int PANEL_HEIGHT = 166;
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
    protected boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        List<Element> names = this.byIdPrefix(document, "aura_name-", ROWS);
        if (names == null) return this.fail("aura_name-*");
        List<Element> amounts = this.byIdPrefix(document, "aura_amount-", ROWS);
        if (amounts == null) return this.fail("aura_amount-*");
        List<Element> fills = this.byIdPrefix(document, "aura_fill-", ROWS);
        if (fills == null) return this.fail("aura_fill-*");
        Element inventoryLabel = document.getElementById("inventory_label");
        if (inventoryLabel == null) return this.fail("inventory_label");
        this.panel = panel;
        // Menu slot order: result, the nine grid cells, then the player inventory.
        if (!this.bindCells(document, "result", 0)) return false;
        if (!this.bindCells(document, "crafting", 1)) return false;
        if (!this.bindInventoryCells(document, "inventory")) return false;
        for (int index = 0; index < ROWS; index++) {
            this.rows.add(new Row(names.get(index), amounts.get(index), fills.get(index)));
        }
        this.text(title, this.getTitle());
        this.text(inventoryLabel, Component.translatable("container.inventory"));
        return true;
    }

    @Override
    protected void onBindingsCleared() {
        this.rows.clear();
    }

    @Override
    protected void onPageBound() {
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
                this.name.setTextContent(name);
                this.shownName = name;
            }
            if (!color.equalsIgnoreCase(this.shownColor)) {
                this.set(this.name, "color", color);
                this.set(this.fill, "background-color", color);
                this.shownColor = color;
            }
            if (!amount.equals(this.shownAmount)) {
                this.amount.setTextContent(amount);
                this.shownAmount = amount;
            }
            // The old screen filled from 2px in and stopped 2px short of the right edge.
            int width = Math.max(0, filled - BAR_INSET * 2);
            if (width == this.shownFill) return;
            this.set(this.fill, "width", width + "px");
            this.shownFill = width;
        }

        private void clear() {
            if (this.shownName == null && this.shownAmount == null && this.shownFill == 0) return;
            if (this.shownName != null) {
                this.name.setTextContent("");
                this.shownName = null;
            }
            if (this.shownAmount != null) {
                this.amount.setTextContent("");
                this.shownAmount = null;
            }
            if (this.shownFill != 0) {
                this.set(this.fill, "width", "0px");
                this.shownFill = 0;
            }
        }

        private void set(Element element, String property, String value) {
            if (value.equals(element.getInlineStylePropertyValue(property))) return;
            element.setInlineStyleProperty(property, value);
        }
    }
}
