package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.hud.AbstractHudEntry;
import com.iafenvoy.mxt.screen.hud.HudLayout;
import com.iafenvoy.mxt.screen.hud.HudManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The wheel's twelve sectors on the HUD, in the order the layout stores them: four columns over three rows,
 * each cell drawn like a sector of the wheel and the armed one in the editor's gold. An empty cell is an
 * empty frame, and no sector armed yet is a whole block of them - both are states the layout editor shows.
 */
public final class WheelSelectionEntry extends AbstractHudEntry {
    private static final String LAYOUT_KEY = "wheel.selection";
    /** The cell is the size a sector of the wheel draws an entry at, so the two read as the same thing. */
    private static final int CELL = 22;
    /**
     * The editor's own cell gap: the block and the configuration screen's slot rows are the same grid.
     */
    private static final int GAP = 2;
    private static final int STEP = CELL + GAP;
    private static final int COLUMNS = 4;
    /**
     * One cell per saved sector, so a change to the sector count cannot silently drop sectors.
     */
    private static final int CELLS = WheelGeometry.SECTORS;
    private static final int ROWS = (CELLS + COLUMNS - 1) / COLUMNS;
    private static final int WIDTH = COLUMNS * STEP - GAP;
    private static final int HEIGHT = ROWS * STEP - GAP;
    /**
     * Room left between the block and the left edge of the window while it sits at its default position.
     */
    private static final int EDGE_MARGIN = 4;
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22.png");
    private static final Identifier SELECTED_SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22_selected.png");
    /** The wheel's own cooldown colour. */
    private static final int UNUSABLE_WASH = 0x903A2A20;

    private WheelSelectionEntry() {
        super(LAYOUT_KEY, WIDTH, HEIGHT);
    }

    /** Called from client setup so the layout editor can show it from the main menu, not only in a world. */
    public static void register() {
        HudManager.register(new WheelSelectionEntry());
    }

    @Override
    public String displayName() {
        return Component.translatable("hud.mxt.wheel_selection").getString();
    }

    @Override
    public int layoutWidth() {
        return WIDTH;
    }

    @Override
    public int layoutHeight() {
        return HEIGHT;
    }

    @Override
    public int defaultX() {
        return EDGE_MARGIN;
    }

    /** Halfway down the window, which the entry follows until the player moves it somewhere else. */
    @Override
    public int defaultY() {
        int[] window = HudLayout.window();
        return window == null ? 0 : (window[1] - HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        // Both read once: the list was resolved once this tick, and drawing must not resolve it again.
        List<@Nullable WheelMenuEntry> sectors = WheelSelectionState.sectors();
        int armed = WheelSelectionState.sector();
        int x = this.x();
        int y = this.y();
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = x + cell % COLUMNS * STEP;
            int cellY = y + cell / COLUMNS * STEP;
            WheelMenuEntry entry = cell < sectors.size() ? sectors.get(cell) : null;
            // Gold says "the use key would spend this one", so a sector that is armed but empty is not gold:
            // nothing would happen, and the wheel does not highlight an empty pointed sector either.
            boolean highlighted = cell == armed && entry != null;
            graphics.blit(RenderPipelines.GUI_TEXTURED, highlighted ? SELECTED_SLOT : SLOT,
                    cellX, cellY, 0.0F, 0.0F, CELL, CELL, CELL, CELL);
            if (entry == null) continue;
            IconRenderer.renderOrName(graphics, minecraft.font, entry.icon(), entry.title(), cellX, cellY, CELL);
            graphics.fill(cellX + 1, cellY + CELL - 3, cellX + CELL - 1, cellY + CELL - 1, entry.accentColor());
            if (!entry.usable(player))
                graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 3, UNUSABLE_WASH);
        }
    }
}
