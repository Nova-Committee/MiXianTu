package com.iafenvoy.mxt.screen.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * One HUD element the framework knows how to place, draw and let the player drag: position only, no scaling
 * and no bounds dependency graph (research/26). None of these methods runs while an entry is being
 * constructed, so {@link #defaultX()} may read fields assigned after {@code super(...)} returns.
 */
public interface HudEntry {
    // The identity of this entry: the config key its position is stored under, the suffix of its translation
    // key, and what a diagnostic prints. Two entries may not share one - registering a duplicate throws.
    String layoutKey();

    String displayName();

    // Asked while drawing, hit testing and clamping: it may follow world state, but it must stay stable for
    // as long as the answer is used, or the rectangle drawn and the rectangle clicked disagree.
    int layoutWidth();

    int layoutHeight();

    // Either this or render(): an entry that hands over blocks lets the framework place them, one that draws
    // itself answers empty here and draws in render().
    default List<RenderBlock> renderBlocks() {
        return RenderBlock.none();
    }

    // Every frame outside the edit screen; while editing, only for the entry the placeholders draw over.
    void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);

    // Only a suggestion: as soon as there is a stored placement, that wins.
    int defaultX();

    int defaultY();

    // Which point defaultX() / defaultY() describe, and the point that stays fixed when the entry resizes.
    default HudAnchor anchor() {
        return HudAnchor.LEFT_TOP;
    }

    default int x() {
        return this.bounds().x();
    }

    default int y() {
        return this.bounds().y();
    }

    // Click, hover and the placeholder outline all test against this, so drawing outside it is not grabbable.
    ScreenBounds bounds();

    // Values outside the window are clamped, so the stored position is one the player can actually see.
    void setPosition(int x, int y);

    // A hidden entry is neither drawn nor draggable; turning one back on is the entry's own business.
    boolean visible();

    void setVisible(boolean visible);

    // A non-moveable entry is neither drawn as a placeholder nor hit tested.
    default boolean moveable() {
        return true;
    }

    default void setDragging(boolean dragging) {
    }

    // Must never write the stored layout: it only derives a position when the window changed size. Overriders
    // adjust their own way and then call super.
    void refreshPlacement();

    void setSize(int width, int height);

    // Also forgets the stored placement - a reset that lasted only until the next launch would look unsaved.
    void resetToDefault();
}
