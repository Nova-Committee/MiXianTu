package com.iafenvoy.mxt.screen.hud;

/**
 * A rectangle of the screen in scaled pixels, as the HUD framework talks about positions.
 *
 * <p>Unlike the {@code Rectangle} KronHUD uses, this one carries no scaling of its own: this port draws
 * every entry at 1:1, so a rectangle here is both what is drawn and what is clicked. {@code xEnd} and
 * {@code yEnd} are exclusive, matching {@code GuiGraphicsExtractor#fill}.</p>
 */
public record ScreenBounds(int x, int y, int width, int height) {
    public int xEnd() {
        return this.x + this.width;
    }

    public int yEnd() {
        return this.y + this.height;
    }

    /**
     * Whether a point is inside this rectangle. The end edges are exclusive, so two entries that share an
     * edge never both claim the pixel on it.
     */
    public boolean contains(double pointX, double pointY) {
        return pointX >= this.x && pointX < this.xEnd() && pointY >= this.y && pointY < this.yEnd();
    }
}
