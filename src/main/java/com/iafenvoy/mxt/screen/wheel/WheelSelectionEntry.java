package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.overlay.hud.AbstractHudEntry;
import com.iafenvoy.mxt.screen.overlay.hud.HudAnchor;
import com.iafenvoy.mxt.screen.overlay.hud.HudLayout;
import com.iafenvoy.mxt.screen.overlay.hud.HudManager;
import com.iafenvoy.mxt.screen.overlay.resourcebar.ResourceBarEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The wheel's current selection on the HUD, so the use key is not a blind cast while the wheel is closed. An
 * empty cell stays an empty frame: nothing selected is a real state the layout editor must still place.
 */
public final class WheelSelectionEntry extends AbstractHudEntry {
    private static final String LAYOUT_KEY = "wheel.selection";
    /** The cell is the size a sector of the wheel draws an entry at, so the two read as the same thing. */
    private static final int CELL = 22;
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_22.png");
    /** The wheel's own cooldown colour. */
    private static final int UNUSABLE_WASH = 0x903A2A20;

    private WheelSelectionEntry() {
        super(LAYOUT_KEY, CELL, CELL);
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
        return CELL;
    }

    @Override
    public int layoutHeight() {
        return CELL;
    }

    @Override
    public HudAnchor anchor() {
        return HudAnchor.CENTER_BOTTOM;
    }

    @Override
    public int defaultX() {
        int[] window = HudLayout.window();
        return window == null ? 0 : window[0] / 2;
    }

    @Override
    public int defaultY() {
        int[] window = HudLayout.window();
        return window == null ? 0 : window[1] - ResourceBarEntry.BOTTOM_MARGIN;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        int x = this.x();
        int y = this.y();
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 0.0F, 0.0F, CELL, CELL, CELL, CELL);
        // Resolved once a tick by the controller, not here: this runs every frame and walks the registry.
        WheelMenuEntry entry = WheelSelectionState.selected();
        if (entry == null) return;
        IconRenderer.renderOrName(graphics, minecraft.font, entry.icon(), entry.title(), x, y, CELL);
        graphics.fill(x + 1, y + CELL - 3, x + CELL - 1, y + CELL - 1, entry.accentColor());
        if (!entry.usable(player)) graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 3, UNUSABLE_WASH);
    }
}
