package com.iafenvoy.mxt.screen.hud;

/**
 * Which point of a HUD entry's rectangle its default position is measured against and a resize keeps fixed;
 * the live position is always kept as a top-left corner. Names are horizontal-then-vertical, so
 * {@link #CENTER_BOTTOM} is the midpoint of the bottom edge.
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

    public boolean centered() {
        return this.centeredHorizontally;
    }

    public boolean bottom() {
        return this.anchoredToBottom;
    }

    public int toLeft(int anchorX, int width) {
        return this.centeredHorizontally ? anchorX - width / 2 : anchorX;
    }

    public int toTop(int anchorY, int height) {
        return this.anchoredToBottom ? anchorY - height : anchorY;
    }
}
