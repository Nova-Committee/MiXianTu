package com.iafenvoy.mxt.screen.hud;

import net.minecraft.util.Mth;

/**
 * The half of a HUD entry that the framework owns: size, anchor and the stored placement. Nothing here asks a
 * subclass anything during construction - the defaults are read on the frames after it, by which point the subclass
 * is built and a window exists.
 */
public abstract class AbstractHudEntry implements HudEntry {
    private final String layoutKey;
    // null means "never placed", which is also the state resetToDefault() restores; replaced in the same breath as
    // the file write, so nothing can re-apply a position the player has just dragged to.
    private HudPlacement stored;
    // The placement in force, in pixels: the anchor point is the window anchor plus this offset. The anchor is state
    // rather than a class constant because the editor rebinds it while dragging.
    private HudAnchor anchor = HudAnchor.LEFT_TOP;
    private int offsetX;
    private int offsetY;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean dragging;

    protected AbstractHudEntry(String layoutKey, int defaultWidth, int defaultHeight) {
        if (layoutKey == null || layoutKey.isBlank())
            throw new IllegalArgumentException("A HUD entry needs a layout key");
        this.layoutKey = layoutKey;
        this.width = Math.max(0, defaultWidth);
        this.height = Math.max(0, defaultHeight);
        this.stored = HudLayout.read(layoutKey).orElse(null);
        if (this.stored != null) {
            this.anchor = this.stored.anchor();
            this.offsetX = this.stored.offsetX();
            this.offsetY = this.stored.offsetY();
            this.visible = this.stored.visible();
        }
    }

    @Override
    public final String layoutKey() {
        return this.layoutKey;
    }

    @Override
    public ScreenBounds bounds() {
        return new ScreenBounds(this.x(), this.y(), this.layoutWidth(), this.layoutHeight());
    }

    @Override
    public final HudAnchor anchor() {
        return this.anchor;
    }

    // Rebinds without moving the element: the offset is recomputed so the drawn rectangle stays where it is, which
    // is why dragging across an anchor only changes what the position is measured from. The write that follows the
    // drag stores both.
    @Override
    public final void setAnchor(HudAnchor anchor) {
        if (this.anchor == anchor) return;
        int left = this.x();
        int top = this.y();
        this.anchor = anchor;
        int[] window = HudLayout.window();
        if (window == null) return;
        this.offsetX = left + anchor.toLeft(this.layoutWidth()) - anchor.windowX(window[0]);
        this.offsetY = top + anchor.toTop(this.layoutHeight()) - anchor.windowY(window[1]);
    }

    @Override
    public final int x() {
        return this.clampX(this.anchor.leftOf(this.anchorX(), this.layoutWidth()));
    }

    @Override
    public final int y() {
        return this.clampY(this.anchor.topOf(this.anchorY(), this.layoutHeight()));
    }

    // The point of the element the anchor names, in screen pixels: the window point the anchor stands for, moved by
    // the offset. Without a window yet there is nothing to measure against, so the offset stands alone.
    private int anchorX() {
        int[] window = HudLayout.window();
        return (window == null ? 0 : this.anchor.windowX(window[0])) + this.offsetX;
    }

    private int anchorY() {
        int[] window = HudLayout.window();
        return (window == null ? 0 : this.anchor.windowY(window[1])) + this.offsetY;
    }

    @Override
    public void setPosition(int x, int y) {
        int[] window = HudLayout.window();
        if (window != null) {
            // The player aims with the drawn rectangle, so only the top-left corner is clamped; it is then turned
            // back into the anchor point and the offset the element actually keeps.
            int left = Mth.clamp(x, 0, Math.max(0, window[0] - this.layoutWidth()));
            int top = Mth.clamp(y, 0, Math.max(0, window[1] - this.layoutHeight()));
            this.offsetX = left + this.anchor.toLeft(this.layoutWidth()) - this.anchor.windowX(window[0]);
            this.offsetY = top + this.anchor.toTop(this.layoutHeight()) - this.anchor.windowY(window[1]);
        }
        this.store();
    }

    @Override
    public void setSize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        // The anchor point is deliberately not touched: a column of bars that grows upward keeps standing on the
        // same bottom edge, because the edge - not the rectangle - is what the anchor pins.
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
        this.stored = null;
        HudLayout.clear(this.layoutKey);
        this.refreshPlacement();
    }

    // Applies the class-suggested placement without touching the stored layout, for an entry that computes its own
    // position and has nothing to store. A movable entry reaches the same branch through refreshPlacement().
    protected final void placeAtDefault() {
        this.anchor = this.defaultAnchor();
        this.offsetX = this.defaultOffsetX();
        this.offsetY = this.defaultOffsetY();
    }

    // Never writes the file, so it is safe every frame. A stored placement is pixels already and needs no
    // re-deriving at all; the class default is re-read every frame, which is what lets a default that depends on
    // the window - or on how wide the content currently is - follow it.
    @Override
    public void refreshPlacement() {
        if (this.stored == null) this.placeAtDefault();
    }

    // Clamping happens on the drawn rectangle rather than on what is stored, so shrinking the window pins an
    // element to the edge and growing it back returns the element to where the player put it.
    private int clampX(int left) {
        int[] window = HudLayout.window();
        return window == null ? left : Mth.clamp(left, 0, Math.max(0, window[0] - this.layoutWidth()));
    }

    private int clampY(int top) {
        int[] window = HudLayout.window();
        return window == null ? top : Mth.clamp(top, 0, Math.max(0, window[1] - this.layoutHeight()));
    }

    private void store() {
        // Storing before a window exists would pin an element the player never touched, and the defaults that would
        // be pinned are not even resolvable yet.
        if (HudLayout.window() == null) return;
        // The in-memory copy is replaced in the same breath as the file: leaving the old one here would undo the
        // move on the very next frame, because this placement is all the frame hook re-applies.
        this.stored = new HudPlacement(this.anchor, this.offsetX, this.offsetY, this.visible);
        HudLayout.write(this.layoutKey, this.stored);
        HudManager.layoutChanged();
    }
}
