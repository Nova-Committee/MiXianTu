package com.iafenvoy.mxt.screen.hud;

import net.minecraft.util.Mth;

/**
 * The half of a HUD entry that the framework owns: size, anchor point and the stored placement. Nothing here
 * asks a subclass anything during construction - placement is applied lazily from {@link #refreshPlacement()}
 * on the first frame, by which point the subclass is built and a window exists.
 */
public abstract class AbstractHudEntry implements HudEntry {
    private final String layoutKey;
    // null means "never placed", which is also the state resetToDefault() restores; replaced in the same breath
    // as the file write, so refreshPlacement() can never re-apply a position the player has just dragged to.
    private HudLayout.Placement storedPlacement;
    // The anchor point in screen pixels - what a drag moves and a resize keeps still. The top-left corner,
    // which is what the drag editor and the hit test use, is derived from it and the current size.
    private int anchorX;
    private int anchorY;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean dragging;
    // Turned into pixels on the first frame that has a window, not in the constructor.
    private boolean placed;
    // Window size the anchor point was derived against; a stored ratio is re-derived only when this differs.
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

    public final boolean editMode() {
        return HudManager.editMode();
    }

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
            // The player aims with the drawn rectangle, so only the top-left corner is clamped; it is then
            // turned back into the anchor point the entry actually keeps.
            int left = Mth.clamp(x, 0, Math.max(0, window[0] - this.layoutWidth()));
            int top = Mth.clamp(y, 0, Math.max(0, window[1] - this.layoutHeight()));
            this.anchorX = left + this.anchorOfLeft(this.layoutWidth());
            this.anchorY = top + this.anchorOfTop(this.layoutHeight());
        }
        this.placed = true;
        // Storing here is what makes the move stick: the frame hook re-derives a stored position only on a
        // window resize, and the file and the in-memory placement are written together.
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
        // the same bottom edge. Clamping comes last, so a rectangle that grew past the edge is pulled back in.
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

    // Small entries get a couple of pixels of slack, because a bar four pixels tall would otherwise be a test
    // of precision rather than of intent.
    public boolean isMouseOver(double pointX, double pointY) {
        ScreenBounds bounds = this.bounds();
        int slack = Math.min(2, Math.max(0, Math.min(bounds.width(), bounds.height()) / 2));
        return pointX >= bounds.x() - slack && pointX < bounds.xEnd() + slack
                && pointY >= bounds.y() - slack && pointY < bounds.yEnd() + slack;
    }

    // Erasing the stored value is what makes a reset mean anything across a restart: leaving it behind would
    // bring the old position back on the next launch, which reads exactly like a layout that was never saved.
    @Override
    public void resetToDefault() {
        this.storedPlacement = null;
        this.placed = false;
        HudLayout.clear(this.layoutKey);
        this.refreshPlacement();
    }

    // Applies the class-suggested position without touching the stored layout, for an entry that computes its
    // own position and has nothing to store. A movable entry reaches the same branch through refreshPlacement().
    protected final void placeAtDefault() {
        int[] window = HudLayout.window();
        if (window == null) return;
        this.anchorX = this.defaultX();
        this.anchorY = this.defaultY();
        this.placed = true;
        this.placedWindow = window;
        this.clamp();
    }

    // Never writes the config, so it is safe every frame. With a stored placement the position is re-derived
    // only when the window changed size; without one the class default is re-read every frame, which is what
    // lets a default that depends on the window follow it.
    @Override
    public void refreshPlacement() {
        int[] window = HudLayout.window();
        if (window == null) return;

        // Only on a resize: re-deriving every frame would make the anchor point a function of the file rather
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

    private int anchorOfLeft(int width) {
        return this.anchor().centered() ? width / 2 : 0;
    }

    private int anchorOfTop(int height) {
        return this.anchor().bottom() ? height : 0;
    }

    private void store() {
        int[] window = HudLayout.window();
        if (window == null) return;
        // What is stored is the ratio of the top-left corner, as in the first version of this framework, so a
        // layout written by it keeps meaning the same thing.
        double xRatio = (double) this.x() / window[0];
        double yRatio = (double) this.y() / window[1];
        // The in-memory copy is replaced in the same breath as the file: leaving the old one here would undo
        // the move on the very next frame, because this placement is all the frame hook re-applies.
        this.storedPlacement = new HudLayout.Placement(xRatio, yRatio, this.visible);
        HudLayout.write(this.layoutKey, this.storedPlacement);
        HudManager.layoutChanged();
    }
}
