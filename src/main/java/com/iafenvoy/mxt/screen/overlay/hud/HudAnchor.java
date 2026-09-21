package com.iafenvoy.mxt.screen.overlay.hud;

/**
 * Which point of a HUD entry's rectangle its stored position refers to.
 *
 * <p>The position an entry is placed at is always kept as a top-left corner internally, because that is what
 * a rectangle, a hit test and the drawing all want. The anchor is about the entry's <em>default</em>
 * position and about what happens when the entry resizes: a column of bars that grows upward should keep
 * standing on the same bottom edge, not push its own bottom edge further down the screen.</p>
 *
 * <p>Naming is horizontal-then-vertical: {@link #LEFT_TOP} is the top-left corner - the ordinary case, and
 * the default - while {@link #CENTER_BOTTOM} is the midpoint of the bottom edge, which is what a column
 * sitting above the hotbar wants.</p>
 */
public enum HudAnchor {
    LEFT_TOP(false, false),
    CENTER_TOP(true, false),
    RIGHT_TOP(false, false),
    LEFT_BOTTOM(false, true),
    CENTER_BOTTOM(true, true),
    RIGHT_BOTTOM(false, true);

    private final boolean centeredHorizontally;
    private final boolean anchoredToBottom;

    HudAnchor(boolean centeredHorizontally, boolean anchoredToBottom) {
        this.centeredHorizontally = centeredHorizontally;
        this.anchoredToBottom = anchoredToBottom;
    }

    /**
     * Whether the anchor point is the middle of the rectangle horizontally rather than its left edge.
     */
    public boolean centered() {
        return this.centeredHorizontally;
    }

    /**
     * Whether the anchor point is the bottom edge of the rectangle rather than its top edge.
     */
    public boolean bottom() {
        return this.anchoredToBottom;
    }

    /**
     * The left edge of a rectangle of this width whose anchor point sits at {@code anchorX}.
     */
    public int toLeft(int anchorX, int width) {
        return this.centeredHorizontally ? anchorX - width / 2 : anchorX;
    }

    /**
     * The top edge of a rectangle of this height whose anchor point sits at {@code anchorY}.
     */
    public int toTop(int anchorY, int height) {
        return this.anchoredToBottom ? anchorY - height : anchorY;
    }
}
