package com.iafenvoy.mxt.screen.overlay.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * One HUD element the framework knows how to place, draw and let the player drag.
 *
 * <p>This is the port of AxolotlClient's {@code HudEntry} ("This implementation of Hud modules is based on
 * KronHUD", GPL-3.0). Two things are deliberately missing from that interface. First, scaling: every entry
 * here is drawn at 1:1, so there is no {@code scale} and no {@code supportsScaling}, and the corner grab
 * handles the edit screen draws in KronHUD have no counterpart. Second, the bounds dependency graph
 * ("entry A's left edge follows entry B's right edge") is gone as well - it only exists to keep entries
 * attached to each other while scaling and snapping, and without either of those it would be dead weight.</p>
 *
 * <p>An entry owns its drawing; the framework owns only where that drawing goes. That split is why
 * {@link #layoutWidth()} and {@link #layoutHeight()} are asked for rather than measured: a width that the
 * entry itself computes from the world (a bar that grows, a list that gains a row) is the entry's business,
 * and it tells the framework about a change by calling {@link #setSize(int, int)}.</p>
 *
 * <p>One guarantee, because it is easy to get wrong: <strong>none of these methods is called while an entry
 * is being constructed.</strong> A subclass may therefore answer {@link #defaultX()} from its own fields
 * even though those fields are only assigned after {@code super(...)} returns. See
 * {@link AbstractHudEntry}.</p>
 */
public interface HudEntry {
    /**
     * This entry's identity: at once the config key its position is stored under, part of the translation
     * key for its name, and what a diagnostic should print. Two entries sharing one key are a programming
     * mistake, not a configuration, and registering the second one throws.
     */
    String layoutKey();

    /**
     * The name shown to the player while editing.
     */
    String displayName();

    /**
     * The horizontal extent of this entry, in screen pixels, at the moment it is asked. It is asked for
     * while drawing, hit testing and clamping, so a value that depends on world state is fine - but it has
     * to be stable for as long as the answer is used, or the rectangle drawn and the rectangle clicked
     * disagree.
     */
    int layoutWidth();

    /**
     * The vertical extent of this entry, in screen pixels, at the moment it is asked. See
     * {@link #layoutWidth()}.
     */
    int layoutHeight();

    /**
     * What this entry wants drawn, this frame.
     *
     * <p>An entry built out of a stack of things - a column of resource bars, a list of rows - answers with
     * the blocks it currently has and lets {@link HudRenderer} place them. An entry that draws itself with
     * graphics calls it cannot express as blocks answers with an empty list and does the drawing in
     * {@link #render} instead.</p>
     */
    default List<RenderBlock> renderBlocks() {
        return RenderBlock.none();
    }

    /**
     * Draws this entry at its current position, for an entry that is not built out of {@link RenderBlock}s.
     * Called every frame outside the edit screen, and while editing only for the entry the placeholders draw
     * on top of. An entry that answered {@link #renderBlocks()} does nothing here.
     */
    void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);

    /**
     * Where this entry's {@link #anchor()} point wants to be before the player has ever moved it: the x of
     * the anchor point, which is a left edge for a left-anchored entry and a centre for a centred one.
     * Only a suggestion: as soon as there is a stored placement, that wins.
     */
    int defaultX();

    /**
     * The y of this entry's {@link #anchor()} point: a top edge for an entry anchored to the top, a bottom
     * edge for one anchored to the bottom.
     */
    int defaultY();

    /**
     * Which point of this entry's rectangle {@link #defaultX()} / {@link #defaultY()} describe, and the
     * point that stays fixed when the entry's own size changes. The default is the ordinary top-left corner.
     */
    default HudAnchor anchor() {
        return HudAnchor.LEFT_TOP;
    }

    default int x() {
        return this.bounds().x();
    }

    default int y() {
        return this.bounds().y();
    }

    /**
     * The area this entry occupies right now, in screen pixels. What a click, a hover and the placeholder
     * outline are all tested against, so an entry that draws outside this rectangle is an entry the player
     * cannot grab there.
     */
    ScreenBounds bounds();

    /**
     * Moves (or, while editing, drags) this entry to a screen pixel position and stores it. Values outside
     * the window are clamped, so the stored position is what the player can actually see.
     */
    void setPosition(int x, int y);

    /**
     * Whether this entry is drawn at all. An entry that is not visible is not drawn and cannot be dragged;
     * turning this back on is the entry's own business for now (see {@code research/26}).
     */
    boolean visible();

    void setVisible(boolean visible);

    /**
     * Whether the player may drag this entry. A centred bar that computes its own position is not movable,
     * and an entry that answers {@code false} here is neither drawn as a placeholder nor hit tested.
     */
    default boolean moveable() {
        return true;
    }

    /**
     * Tells this entry that a drag has begun or ended, for entries whose looks change while they are being
     * moved.
     */
    default void setDragging(boolean dragging) {
    }

    /**
     * The "the window may have changed" hook, called once per frame immediately before this entry draws and
     * after {@link #resetToDefault()}. It must not write the stored layout; it reads it only to derive a
     * position once the window is a different size than the one that position was derived from. An entry with
     * a stored placement keeps it, one that has never been placed (or that has just been reset) re-applies
     * its own default position, and both then re-clamp. Implementations that override it should do their own
     * adjustment and then call {@code super.refreshPlacement()}.
     */
    void refreshPlacement();

    /**
     * Called by the entry itself when it has changed its own size, so the framework can recompute where the
     * still-clamped rectangle lies.
     */
    void setSize(int width, int height);

    /**
     * Puts this entry back where its class suggests and makes it follow the window again, which includes
     * forgetting the stored placement - a reset that only lasted until the next launch would look like a
     * layout that had not been saved.
     */
    void resetToDefault();
}
