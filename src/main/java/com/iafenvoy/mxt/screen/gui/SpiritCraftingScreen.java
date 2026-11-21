package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.screen.AuraDisplay;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.sighs.apricityui.init.Element;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The vanilla crafting grid plus the aura progress panel, both drawn by one page. The progress rows are the menu's
 * own snapshots. The page declares a single row and the screen copies it once per aura the recipe on the grid
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
    /**
     * Aura panel metrics, mirroring {@code .subpanel.aura} / {@code .aura-row} in the page's stylesheet: rows are
     * packed from the top padding at the page's own 26px pitch and squeezed down to their own 18px height once a
     * recipe asks for more than five, so the last bar of a full panel still ends inside the box.
     */
    private static final int AURA_PANEL_HEIGHT = 155, AURA_PADDING = 10, ROW_HEIGHT = 18, ROW_PITCH = 26;

    private final List<SpiritCraftingAuraRow> rows = new ArrayList<>(ROWS);
    @Nullable
    private Element auraPanel;
    /**
     * The page's own row; every further row is a copy of it.
     */
    @Nullable
    private Element auraRow;
    @Nullable
    private Element craftingArray;

    public SpiritCraftingScreen(SpiritCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.auraPanel = this.getOrThrow("aura_panel");
        this.craftingArray = this.getOrThrow("crafting_array");

        Element prototype = this.getOrThrow("aura_row");
        this.auraRow = prototype;
        // The page's own row is the first one; every further row is a copy of it, made when a recipe asks for it.
        this.rows.add(this.row(prototype));
        this.text(this.getOrThrow("title"), this.getTitle());
    }

    @Override
    public void onBindingsCleared() {
        this.rows.clear();
        this.auraPanel = null;
        this.auraRow = null;
        this.craftingArray = null;
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
        AuiElements.setClass(this.craftingArray, "active", count > 0);
        this.fitRows(panel, prototype, count);
        int pitch = pitch(count);
        for (int index = 0; index < this.rows.size(); index++) {
            SpiritCraftingAuraRow row = this.rows.get(index);
            Holder<Aura> aura = index < count ? this.menu.progressAura(index) : null;
            if (aura == null) {
                row.hide();
                continue;
            }
            double required = this.menu.progressRequirement(index);
            row.show(AURA_PADDING + index * pitch, AuraDisplay.of(aura), this.menu.progressAmount(index), required);
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

    private SpiritCraftingAuraRow row(Element root) {
        SpiritCraftingAuraRow row = new SpiritCraftingAuraRow(root);
        this.tooltip(root, row::tooltip);
        return row;
    }
}
