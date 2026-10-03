package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
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
 * The vanilla crafting grid plus the aura progress panel, both drawn by one page. The progress rows are the menu's
 * own data slots. The page declares a single row and the screen copies it once per aura the recipe on the grid
 * needs, so an empty grid leaves the panel with no rows at all while a recipe that asks for many still fits.
 * <p>
 * The page's containers map straight onto the menu's slots - {@code result} cell 0, {@code crafting} cells 0..8,
 * {@code inventory} the vanilla player indices - so the screen binds no cells of its own.
 */
public final class SpiritCraftingScreen extends AuiContainerScreen<SpiritCraftingMenu> {
    /**
     * Rows the page may grow to; the menu publishes at most this many.
     */
    private static final int ROWS = 8;
    private static final int BAR_WIDTH = 105;
    /**
     * The interior the filled part is drawn in: 2px in from both sides, hence the 4px shortfall.
     */
    private static final int BAR_INSET = 2;
    /**
     * Aura panel metrics, mirroring {@code .subpanel.aura} / {@code .aura-row} in the page's stylesheet: rows are
     * packed from the top padding at the page's own 26px pitch and squeezed down to their own 18px height once a
     * recipe asks for more than five, so the last bar of a full panel still ends inside the box.
     */
    private static final int AURA_PANEL_HEIGHT = 155, AURA_PADDING = 10, ROW_HEIGHT = 18, ROW_PITCH = 26;

    private final List<Row> rows = new ArrayList<>(ROWS);
    @Nullable
    private Element auraPanel;
    /**
     * The page's own row; every further row is a copy of it.
     */
    @Nullable
    private Element auraRow;

    public SpiritCraftingScreen(SpiritCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.auraPanel = this.getOrThrow("aura_panel");
        Element prototype = this.getOrThrow("aura_row");
        this.auraRow = prototype;
        // The page's own row is the first one; every further row is a copy of it, made when a recipe asks for it.
        this.rows.add(this.row(prototype));
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }

    @Override
    public void onBindingsCleared() {
        this.rows.clear();
        this.auraPanel = null;
        this.auraRow = null;
    }

    @Override
    public void onPageBound() {
        this.refresh();
    }

    @Override
    protected void refresh() {
        Element panel = this.auraPanel;
        Element prototype = this.auraRow;
        if (panel == null || prototype == null) return;
        int count = 0;
        while (count < ROWS && this.menu.progressAura(count) != null) count++;
        this.fitRows(panel, prototype, count);
        int pitch = pitch(count);
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            Holder<Aura> aura = index < count ? this.menu.progressAura(index) : null;
            if (aura == null) {
                row.hide();
                continue;
            }
            int required = this.menu.progressRequirement(index);
            int amount = Math.min(this.menu.progressAmount(index), required);
            // The aura's own particle colour, lifted so it stays readable on the dark skin.
            String color = AuiStyles.hex(AuiStyles.readableOnDark(aura.value().resource().value().particleColor()));
            int filled = required <= 0 ? 0 : Math.round(BAR_WIDTH * amount / (float) required);
            row.show(AURA_PADDING + index * pitch, DefinitionText.name(aura, "aura").getString(),
                    amount + " / " + required, color, filled);
        }
    }

    /**
     * Distance between two row tops; a single row sits at the padding, which is the old screen's look, and only a
     * recipe needing more than five auras moves the rows closer together.
     */
    private static int pitch(int count) {
        if (count <= 1) return 0;
        return Math.clamp((AURA_PANEL_HEIGHT - AURA_PADDING * 2 - ROW_HEIGHT) / (count - 1), ROW_HEIGHT, ROW_PITCH);
    }

    /**
     * Grows the rows to what the recipe needs, the way ApricityUI expands a repeated slot: the first row is the
     * page's own and every further one is a copy of it. A copy carries the prototype's id for an instant, so the
     * bind resolves that id before any copy exists and never looks it up again.
     */
    private void fitRows(Element panel, Element prototype, int count) {
        while (this.rows.size() < count) this.rows.add(this.row(copyOf(panel, prototype)));
    }

    /**
     * One more row, attached to the panel. ApricityUI attaches through {@code Element.init}, which swaps in the
     * element class registered for the tag, so the attached element is the one the screen keeps.
     */
    private static Element copyOf(Element panel, Element prototype) {
        Element copy = prototype.cloneNode(true);
        copy.removeAttribute("id");
        return panel.appendChild(copy);
    }

    private Row row(Element root) {
        return new Row(root, this.part(root, ".name"), this.part(root, ".amount"), this.part(root, ".bar .fill"));
    }

    /**
     * One named element of a row. A copy carries the same children as the prototype, so this only faults on a page
     * whose row is not the row this screen expects.
     */
    private Element part(Element root, String selector) {
        Element found = root.querySelector(selector);
        if (found == null) throw this.missing("aura_row " + selector);
        return found;
    }

    /**
     * One aura row: a name, a right-aligned "have / need" and the filled part of the bar. Every write goes through
     * {@link AuiElements}, which compares first, because the rows are refreshed on every container tick.
     */
    private record Row(Element root, Element name, Element amount, Element fill) {
        private void show(int top, String aura, String progress, String color, int filled) {
            AuiElements.style(this.root, "top", top + "px");
            AuiElements.style(this.root, "display", "block");
            AuiElements.setText(this.name, aura);
            AuiElements.style(this.name, "color", color);
            AuiElements.setText(this.amount, progress);
            AuiElements.style(this.fill, "background-color", color);
            // The old screen filled from 2px in and stopped 2px short of the right edge.
            AuiElements.style(this.fill, "width", Math.max(0, filled - BAR_INSET * 2) + "px");
        }

        /**
         * Hides a row the recipe on the grid does not need. An unused row is kept for the next recipe rather than
         * removed: the page is rebuilt wholesale by a hot reload, so nothing outlives the bind this way.
         */
        private void hide() {
            AuiElements.style(this.root, "display", "none");
        }
    }
}
