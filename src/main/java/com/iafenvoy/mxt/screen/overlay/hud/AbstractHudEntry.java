package com.iafenvoy.mxt.screen.overlay.hud;

import net.minecraft.util.Mth;

/**
 * The half of a HUD entry that the framework owns: where it is, how big it says it is, and what happens to
 * that position when the window changes size.
 *
 * <p>Every subclass has to do three things - describe what it shows, answer {@link #layoutWidth()} and
 * {@link #layoutHeight()}, and suggest {@link #defaultX()} / {@link #defaultY()}. Everything else about
 * being placed lives here, which is what keeps a new HUD entry from re-implementing drag handling.</p>
 *
 * <h2>Coordinates</h2>
 * <p>Screen coordinates throughout, with the origin at the top-left corner of the window: x grows right, y
 * grows down, and the position of an entry is the top-left corner of its rectangle. That is what the drag
 * editor, the hit test and the drawing all agree on. {@link #anchor()} then says where the entry
 * <em>wants</em> that rectangle to hang from, which is what its default position is measured against and
 * what stays fixed when the entry resizes.</p>
 *
 * <h2>Storing</h2>
 * <p>A position is kept in screen pixels and clamped into the window, but <em>stored</em> as a ratio of the
 * window (see {@link HudLayout}). The clamped pixel value is what {@link #bounds()} answers; the stored
 * ratio is written only when the player moves the entry, never as a side effect of resizing or of the
 * window changing shape. So shrinking the window cannot silently rewrite a layout the player set on a
 * bigger one - the entry is drawn inside the smaller window, and the moment the window grows back it
 * returns to where it was.</p>
 *
 * <h2>One rule for subclasses</h2>
 * <p><strong>Nothing here asks a subclass anything while the entry is being constructed.</strong> That is not
 * a style preference: a subclass field - the width of a column of bars, say - is still null or zero during
 * {@code super(...)}, so a {@link #defaultX()} that read one would either see garbage or throw. The
 * placement is therefore applied lazily, from {@link #refreshPlacement()} on the first frame the framework
 * draws, by which point the subclass is fully built and the window exists. Everything that depends on
 * subclass state answers from {@link #layoutWidth()} / {@link #layoutHeight()}, which the framework asks
 * only once it is about to draw or hit test.</p>
 */
public abstract class AbstractHudEntry implements HudEntry {
    private final String layoutKey;
    /**
     * What the file said about this entry, or {@code null} when it has never been placed - which is also the
     * state {@link #resetToDefault()} puts it back into. Read in the constructor because that is a plain file
     * lookup, but not turned into pixels until there is a window.
     *
     * <p>{@code null} or not is the <em>only</em> thing that decides whether the entry follows the stored
     * ratio or the default its class suggests, and that is on purpose: an earlier version kept a second
     * boolean for the same fact, which nothing set on startup, so a placement read from the file was skipped
     * and every restart looked like a layout that had never been saved. It is replaced whenever the entry is
     * moved, in the same breath as the file is written, so {@link #refreshPlacement()} can never re-apply a
     * stale position over one the player has just dragged to.</p>
     */
    private HudLayout.Placement storedPlacement;
    /**
     * The entry's anchor point in screen pixels - the point {@link #anchor()} names - which is the thing a
     * drag moves and a resize keeps still. The top-left corner is derived from it and the current size.
     */
    private int anchorX;
    private int anchorY;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean dragging;
    /**
     * Whether a placement has been turned into pixels yet. It happens on the first frame rather than in the
     * constructor, because it needs both a window and a finished subclass.
     */
    private boolean placed;
    /**
     * The window size the anchor point was computed against, or {@code null} before there has been one. A
     * stored position is a ratio of the window, so it is only re-derived when the window is a different size
     * than the one it was derived from - never on a frame that merely came after a drag.
     */
    private int[] placedWindow;

    protected AbstractHudEntry(String layoutKey, int defaultWidth, int defaultHeight) {
        if (layoutKey == null || layoutKey.isBlank())
            throw new IllegalArgumentException("A HUD entry needs a layout key");
        this.layoutKey = layoutKey;
        this.width = Math.max(0, defaultWidth);
        this.height = Math.max(0, defaultHeight);
        this.storedPlacement = HudLayout.read(layoutKey).orElse(null);
        if (this.storedPlacement != null) this.visible = this.storedPlacement.visible();
    }

    @Override
    public final String layoutKey() {
        return this.layoutKey;
    }

    @Override
    public ScreenBounds bounds() {
        return new ScreenBounds(this.x(), this.y(), this.layoutWidth(), this.layoutHeight());
    }

    /**
     * Whether this entry is drawn while the edit screen is open. Called by the entry's own render method so
     * that editing shows the real element rather than a stand-in rectangle.
     */
    public final boolean editMode() {
        return HudManager.editMode();
    }

    /**
     * The left edge of the rectangle for the current anchor point and size.
     */
    @Override
    public final int x() {
        return this.anchor().toLeft(this.anchorX, this.layoutWidth());
    }

    @Override
    public final int y() {
        return this.anchor().toTop(this.anchorY, this.layoutHeight());
    }

    @Override
    public void setPosition(int x, int y) {
        int[] window = HudLayout.window();
        if (window == null) {
            this.anchorX = x;
            this.anchorY = y;
        } else {
            // The player aims with the rectangle, which is drawn from its top-left corner; only then is that
            // turned back into the anchor point the entry keeps.
            int left = Mth.clamp(x, 0, Math.max(0, window[0] - this.layoutWidth()));
            int top = Mth.clamp(y, 0, Math.max(0, window[1] - this.layoutHeight()));
            this.anchorX = left + this.anchorOfLeft(this.layoutWidth());
            this.anchorY = top + this.anchorOfTop(this.layoutHeight());
        }
        this.placed = true;
        // Storing is also what makes the move stick: the frame hook re-derives a stored position only when
        // the window changes size, so the anchor point just set is the one the next frame keeps. The file and
        // the in-memory placement are written together, so there is no second copy of "where this entry is"
        // for a frame to disagree with.
        this.store();
    }

    @Override
    public void setSize(int width, int height) {
        int newWidth = Math.max(0, width);
        int newHeight = Math.max(0, height);
        if (newWidth == this.width && newHeight == this.height) return;
        this.width = newWidth;
        this.height = newHeight;
        // The anchor point is deliberately not touched: a column of bars that grows upward keeps standing on
        // the same bottom edge, one that grows to the right keeps its left edge. Clamping comes last, so a
        // rectangle that has grown past the edge of the window is pulled back in rather than left outside.
        if (this.placed) this.clamp();
    }

    @Override
    public boolean visible() {
        return this.visible;
    }

    @Override
    public void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        // Visibility is part of what is stored, so it is written back even though no position changed.
        this.store();
    }

    public boolean dragging() {
        return this.dragging;
    }

    @Override
    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    /**
     * Whether a point is on this entry. Small entries get a couple of pixels of slack, because a health bar
     * four pixels tall would otherwise be a test of precision rather than of intent.
     */
    public boolean isMouseOver(double pointX, double pointY) {
        ScreenBounds bounds = this.bounds();
        int slack = Math.min(2, Math.max(0, Math.min(bounds.width(), bounds.height()) / 2));
        return pointX >= bounds.x() - slack && pointX < bounds.xEnd() + slack
                && pointY >= bounds.y() - slack && pointY < bounds.yEnd() + slack;
    }

    /**
     * Puts the entry back where its class suggests and forgets the stored placement, in memory and in the
     * file. Erasing the value is what makes a reset mean anything across a restart: leaving it behind would
     * bring the old position back on the next launch, which reads exactly like a layout that was never saved.
     * With no stored placement the entry follows the window again, which is what its default position is
     * defined against.
     */
    @Override
    public void resetToDefault() {
        this.storedPlacement = null;
        this.placed = false;
        HudLayout.clear(this.layoutKey);
        this.refreshPlacement();
    }

    /**
     * Applies the position this entry's class suggests, without touching the stored layout, and re-clamps it.
     * For an entry that computes its own position and has nothing to store - the two rows about the entity
     * under the crosshair. A movable entry never needs it: this is the branch {@link #refreshPlacement()}
     * takes on its own while there is no stored placement.
     */
    protected final void placeAtDefault() {
        int[] window = HudLayout.window();
        if (window == null) return;
        this.anchorX = this.defaultX();
        this.anchorY = this.defaultY();
        this.placed = true;
        this.placedWindow = window;
        this.clamp();
    }

    /**
     * The "the window may have changed" hook, called once per frame immediately before drawing. It never
     * writes the config, so it is safe to call every frame and it can never fight a drag or overwrite a
     * layout.
     *
     * <p>Until a window exists there is nothing to compute, so the early frames simply wait. After that the
     * position comes from exactly one of two places, and which one is decided by a single question - is there
     * a stored placement for this entry. If there is, it is derived from the ratio when the window is a
     * different size than the one it was derived from, and otherwise left alone. If there is not, the position
     * the class suggests is re-read on every frame, which is what lets a default that depends on the window
     * (a column centred on the screen) follow it.</p>
     */
    @Override
    public void refreshPlacement() {
        int[] window = HudLayout.window();
        if (window == null) return;

        // A stored position is a ratio of the window, so it is re-derived when the window changes size - and
        // only then. Re-deriving it every frame would make the anchor point a function of the file rather
        // than of where the entry is, and the first frame after any write would move the entry back.
        boolean resized = this.placedWindow == null
                || this.placedWindow[0] != window[0] || this.placedWindow[1] != window[1];
        if (this.storedPlacement == null) {
            this.anchorX = this.defaultX();
            this.anchorY = this.defaultY();
        } else if (resized) {
            this.anchorX = Mth.floor(this.storedPlacement.xRatio() * window[0]) + this.anchorOfLeft(this.layoutWidth());
            this.anchorY = Mth.floor(this.storedPlacement.yRatio() * window[1]) + this.anchorOfTop(this.layoutHeight());
        }
        this.placed = true;
        this.placedWindow = window;
        this.clamp();
    }

    private void clamp() {
        int[] window = HudLayout.window();
        if (window == null) return;
        // Clamping happens on the top-left corner, because that is the part that must stay inside the window.
        int left = Mth.clamp(this.x(), 0, Math.max(0, window[0] - this.layoutWidth()));
        int top = Mth.clamp(this.y(), 0, Math.max(0, window[1] - this.layoutHeight()));
        this.anchorX = left + this.anchorOfLeft(this.layoutWidth());
        this.anchorY = top + this.anchorOfTop(this.layoutHeight());
    }

    /**
     * How far the anchor point sits from the left edge of a rectangle of this width.
     */
    private int anchorOfLeft(int width) {
        return this.anchor().centered() ? width / 2 : 0;
    }

    /**
     * How far the anchor point sits below the top edge of a rectangle of this height.
     */
    private int anchorOfTop(int height) {
        return this.anchor().bottom() ? height : 0;
    }

    private void store() {
        int[] window = HudLayout.window();
        if (window == null) return;
        // What is stored is the top-left corner, unchanged from the first version of this framework, so an
        // existing layout keeps meaning what it meant even though entries may now be anchored differently.
        double xRatio = (double) this.x() / window[0];
        double yRatio = (double) this.y() / window[1];
        // The in-memory copy is replaced in the same breath as the file, and this is what keeps a drag: the
        // only thing the frame hook re-applies is this placement, so leaving the old one here would undo the
        // move on the very next frame.
        this.storedPlacement = new HudLayout.Placement(xRatio, yRatio, this.visible);
        HudLayout.write(this.layoutKey, this.storedPlacement);
        HudManager.layoutChanged();
    }
}
