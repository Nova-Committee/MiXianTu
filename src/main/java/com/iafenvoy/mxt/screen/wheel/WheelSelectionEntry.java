package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelMenuEntry;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Every cell of the wheel on the HUD: four columns wide and as many rows as the cells need, growing downward as
 * pages are added. Only the configured page keeps its empty cells - the other pages hold exactly what their
 * source contributes, so a page boundary is not necessarily a row boundary - and the armed cell is drawn in gold.
 */
public final class WheelSelectionEntry extends AbstractHudEntry {
    private static final String LAYOUT_KEY = "wheel.selection";
    // The cell is the size a sector of the wheel draws an entry at, so the two read as the same thing.
    private static final int CELL = 22;
    // The icon box inside a cell: what the cooldown sheet covers, the way vanilla covers an item's own 16x16.
    private static final int ICON_INSET = (CELL - IconRenderer.ICON_SIZE) / 2;
    // The editor's own gap: this block and the configuration screen's slot rows are the same grid.
    private static final int GAP = 2;
    private static final int STEP = CELL + GAP;
    private static final int COLUMNS = 4;
    // The block before a world is joined, or with no player; the configured page always exists after.
    private static final int MIN_ROWS = 1;
    // Room left between the block and the left edge of the window while it sits at its default position.
    private static final int EDGE_MARGIN = 4;
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22.png");
    private static final Identifier SELECTED_SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22_selected.png");
    // The wheel's own cooldown colour.
    private static final int UNUSABLE_WASH = 0x903A2A20;
    // Vanilla's item cooldown sheet: white at 50% alpha, the same constant GuiGraphicsExtractor passes.
    private static final int COOLDOWN_SHEET = Integer.MAX_VALUE;

    private WheelSelectionEntry() {
        super(LAYOUT_KEY, 0, 0);
    }

    // Called from client setup so the layout editor can show it from the main menu, not only in a world.
    public static void register() {
        HudManager.register(new WheelSelectionEntry());
    }

    @Override
    public String displayName() {
        return Component.translatable("hud.mxt.wheel_selection").getString();
    }

    private static int rows() {
        int cells = WheelMenuContent.shown(WheelSelectionState.pages());
        return Math.max(MIN_ROWS, (cells + COLUMNS - 1) / COLUMNS);
    }

    @Override
    public int layoutWidth() {
        return COLUMNS * STEP - GAP;
    }

    @Override
    public int layoutHeight() {
        return rows() * STEP - GAP;
    }

    @Override
    public int defaultX() {
        return EDGE_MARGIN;
    }

    @Override
    public int defaultY() {
        int[] window = HudLayout.window();
        return window == null ? 0 : (window[1] - this.layoutHeight()) / 2;
    }

    @Override
    public void refreshPlacement() {
        // A page gained or lost changes the block's height, and the framework is told rather than asked: it
        // re-clamps from there, so a block that grew past the bottom edge is pulled back in.
        this.setSize(this.layoutWidth(), this.layoutHeight());
        super.refreshPlacement();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        // Both read once: the pages were resolved once this tick, and drawing must not resolve them again.
        List<WheelPage> pages = WheelSelectionState.pages();
        int armed = WheelSelectionState.effective();
        int x = this.x();
        int y = this.y();
        int index = 0;
        for (int page = 0; page < pages.size(); page++) {
            WheelPage current = pages.get(page);
            for (int cell = 0; cell < current.shown(); cell++) {
                int cellX = x + index % COLUMNS * STEP;
                int cellY = y + index / COLUMNS * STEP;
                index++;
                WheelMenuEntry entry = current.sectors().get(cell);
                // Gold says "the use key would spend this one", so a cell that stands for nothing is not gold:
                // nothing would happen, and the wheel does not highlight an empty pointed sector either.
                boolean highlighted = page * WheelMenuContent.SECTORS + cell == armed && entry != null;
                graphics.blit(RenderPipelines.GUI_TEXTURED, highlighted ? SELECTED_SLOT : SLOT,
                        cellX, cellY, 0.0F, 0.0F, CELL, CELL, CELL, CELL);
                if (entry == null) continue;
                IconRenderer.renderOrName(graphics, minecraft.font, entry.icon(), entry.title(), cellX, cellY, CELL);
                graphics.fill(cellX + 1, cellY + CELL - 3, cellX + CELL - 1, cellY + CELL - 1, entry.accentColor());
                float cooldown = cooldownFraction(entry, player, deltaTracker);
                // A cooling cell carries the sheet, so the flat wash is left for every other reason a cell
                // cannot be used - the two would otherwise stack on the same cell.
                if (cooldown > 0.0F) renderCooldown(graphics, cellX, cellY, cooldown);
                else if (!entry.usable(player))
                    graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 3, UNUSABLE_WASH);
            }
        }
    }

    // Vanilla's own sheet, over the icon rather than the whole cell: the part of the remaining fraction that is
    // still to wait, growing downward from the top, so it drains the way an item's cooldown does.
    private static void renderCooldown(GuiGraphicsExtractor graphics, int cellX, int cellY, float fraction) {
        int left = cellX + ICON_INSET;
        int top = cellY + ICON_INSET + Mth.floor(IconRenderer.ICON_SIZE * (1.0F - fraction));
        int bottom = top + Mth.ceil(IconRenderer.ICON_SIZE * fraction);
        graphics.fill(left, top, left + IconRenderer.ICON_SIZE, bottom, COOLDOWN_SHEET);
    }

    // What the sheet is drawn against: the fraction of the cooldown still to wait, 0 when the cell is ready. The
    // partial tick is what vanilla adds, so the sheet moves every frame instead of stepping once per game tick.
    private static float cooldownFraction(WheelMenuEntry entry, @Nullable Player player, DeltaTracker deltaTracker) {
        if (player == null) return 0.0F;
        long remaining = entry.cooldownTicks(player);
        if (remaining <= 0L) return 0.0F;
        long length = entry.cooldownLength(player);
        // An entry that does not know its length still gets to say that a cooldown is running.
        return length <= 0L ? 1.0F
                : Mth.clamp((remaining - deltaTracker.getGameTimeDeltaPartialTick(true)) / length, 0.0F, 1.0F);
    }
}
