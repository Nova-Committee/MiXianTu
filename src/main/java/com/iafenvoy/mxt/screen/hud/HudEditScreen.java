package com.iafenvoy.mxt.screen.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The screen the player drags HUD entries around on.
 *
 * <p>This is the port of AxolotlClient's {@code HudEditScreen} ("This implementation of Hud modules is based
 * on KronHUD", GPL-3.0) with the scaling half removed: KronHUD's version grabs a corner to resize an entry
 * and grows a snapping guide while moving, and neither exists here. What is left is the part that makes the
 * feature useful on its own - pick an entry up with the left button, drop it anywhere on screen, and the new
 * position is written to the client config as it moves.</p>
 *
 * <p>Entries keep drawing through the GUI layer while this screen is open, so what is being dragged is the
 * real element rather than a picture of one. This screen only adds the rectangle, the name label and the
 * button.</p>
 *
 * <p>Two departures from KronHUD, both because the guide that made them unnecessary is gone: a click on
 * empty space clears the selection, and the escape key drops the selection instead of closing the screen -
 * a misplaced drag is then one keystroke away from being abandoned.</p>
 */
public final class HudEditScreen extends Screen {
    /** Unselected entries: a dim wash, so the real element underneath stays readable. */
    private static final int IDLE_FILL = 0x33FFFFFF;
    private static final int IDLE_OUTLINE = 0x66FFFFFF;
    /** The entry in hand, which has to be obvious at a glance. */
    private static final int SELECTED_FILL = 0x44FFD24A;
    private static final int SELECTED_OUTLINE = 0xFFFFD24A;
    private static final int LABEL_COLOR = 0xFFFFD24A;

    private HudEntry selected;
    private int grabOffsetX;
    private int grabOffsetY;

    public HudEditScreen() {
        super(Component.translatable("screen.mxt.hud_layout"));
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        List<HudEntry> moveable = HudManager.moveableEntries();
        for (HudEntry entry : moveable) {
            ScreenBounds bounds = entry.bounds();
            boolean highlight = entry == this.selected;
            graphics.fill(bounds.x(), bounds.y(), bounds.xEnd(), bounds.yEnd(),
                    highlight ? SELECTED_FILL : IDLE_FILL);
            graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    highlight ? SELECTED_OUTLINE : IDLE_OUTLINE);
            // Named by what the entry calls itself, not by a translation key: an entry whose name has not
            // been written yet is exactly the one worth spotting here.
            graphics.text(this.font, entry.displayName(), bounds.x(), bounds.y() - 10, LABEL_COLOR, true);
        }
        if (moveable.isEmpty()) {
            Component empty = Component.translatable("screen.mxt.hud_layout.empty");
            graphics.text(this.font, empty, this.width / 2 - this.font.width(empty) / 2, this.height / 2,
                    0xAAAAAA, true);
        } else {
            // The controls are not discoverable on their own - there is no cursor change to hint at them,
            // because this port has no corner to grab - so they are written down.
            Component hint = Component.translatable("screen.mxt.hud_layout.hint");
            graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 44,
                    0xAAAAAA, true);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
    }

    /**
     * Places this screen in the same family as the vanilla container and book screens, and the reason is the
     * background: a screen that is not "in the game UI" gets the blurred menu background, and that blur is
     * applied to everything drawn before it - which is the whole HUD, because the HUD is extracted before any
     * screen is. The result was an editor whose elements were blurred along with the world, which is exactly
     * the thing the player is trying to look at. Reporting an in-game UI instead draws the plain translucent
     * gradient, so the elements being placed stay crisp.
     */
    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        boolean consumed = super.mouseClicked(event, doubleClick);
        if (consumed) return true;
        if (event.button() != 0) return false;
        HudEntry hit = HudManager.at(event.x(), event.y());
        this.setSelected(hit);
        if (hit == null) return false;
        this.grabOffsetX = (int) event.x() - hit.x();
        this.grabOffsetY = (int) event.y() - hit.y();
        return true;
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.selected != null) {
            this.selected.setPosition((int) event.x() - this.grabOffsetX, (int) event.y() - this.grabOffsetY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        this.setSelected(null);
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        // Arrow keys are the keyboard's version of dragging: one pixel at a time, or ten with a modifier.
        // That is also the only way to place an entry precisely, and the only way at all for a player who
        // cannot hold a mouse button down.
        if (this.selected != null) {
            int step = event.hasShiftDown() ? 10 : 1;
            int key = event.key();
            if (key == InputConstants.KEY_LEFT) return this.nudge(-step, 0);
            if (key == InputConstants.KEY_RIGHT) return this.nudge(step, 0);
            if (key == InputConstants.KEY_UP) return this.nudge(0, -step);
            if (key == InputConstants.KEY_DOWN) return this.nudge(0, step);
            if (key == InputConstants.KEY_ESCAPE) {
                this.setSelected(null);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        this.setSelected(null);
        super.removed();
    }

    private boolean nudge(int deltaX, int deltaY) {
        this.selected.setPosition(this.selected.x() + deltaX, this.selected.y() + deltaY);
        return true;
    }

    /**
     * Moves the edit screen's idea of "what is being dragged" and tells both entries, which is what lets an
     * entry draw itself differently while it is in hand.
     */
    private void setSelected(HudEntry entry) {
        if (this.selected == entry) return;
        boolean pickedUp = this.selected == null;
        if (this.selected != null) this.selected.setDragging(false);
        this.selected = entry;
        HudManager.setEditedEntry(entry);
        if (entry != null) entry.setDragging(true);
        // Picking something up is worth a sound: a misplaced click and a successful grab otherwise look the
        // same until the mouse moves, which reads as the drag having failed.
        if (pickedUp) Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
