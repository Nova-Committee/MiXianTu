package com.iafenvoy.mxt.screen.hud;

/**
 * A rectangle of the screen in scaled pixels, as the HUD framework talks about positions. There is no scaling
 * of its own - every entry is drawn at 1:1, so a rectangle is both what is drawn and what is clicked - and
 * {@code xEnd} / {@code yEnd} are exclusive, matching {@code GuiGraphicsExtractor#fill}.
 */
public record ScreenBounds(int x, int y, int width, int height) {
    public int xEnd() {
        return this.x + this.width;
    }

    public int yEnd() {
        return this.y + this.height;
    }

    // The end edges are exclusive, so two entries that share an edge never both claim the pixel on it.
    public boolean contains(double pointX, double pointY) {
        return pointX >= this.x && pointX < this.xEnd() && pointY >= this.y && pointY < this.yEnd();
    }
}
