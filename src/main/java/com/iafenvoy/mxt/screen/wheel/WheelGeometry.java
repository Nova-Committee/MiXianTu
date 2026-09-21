package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Where the wheel's sectors are: the one place that turns a pointer direction into a sector. Angles are screen
 * space degrees, 0 right and increasing downwards, with sector 0 straight up; only the direction matters, so
 * the middle of the wheel stays a free target.
 */
public final class WheelGeometry {
    /** One sector per saved layout slot, so geometry and content cannot disagree. */
    public static final int SECTORS = WheelLayout.SLOTS;
    public static final double SECTOR_DEGREES = 360.0 / SECTORS;
    /** How far the pointed-at sector is pushed out at full size, in GUI-scaled pixels. */
    public static final double SELECTED_GROW = 3.0;
    /** The ring's radii at full size, in GUI-scaled pixels; the hole in the middle is where the text goes. */
    private static final double INNER_RADIUS = 56.0;
    private static final double OUTER_RADIUS = 96.0;
    /** How much room the ring leaves at the window's edge before it shrinks to fit. */
    private static final double MARGIN = 8.0;
    /** Fraction of its full size the ring starts at; the caller supplies the opening progress. */
    private static final double OPEN_SCALE = 0.72;
    private static final double FIRST_SECTOR_CENTRE = -90.0;

    private WheelGeometry() {
    }

    /** Pointer direction in degrees, from the raw mouse position - an angle is scale invariant. */
    public static double pointerAngle() {
        Minecraft minecraft = Minecraft.getInstance();
        double centreX = minecraft.getWindow().getScreenWidth() * 0.5D;
        double centreY = minecraft.getWindow().getScreenHeight() * 0.5D;
        return Math.toDegrees(Math.atan2(minecraft.mouseHandler.ypos() - centreY,
                minecraft.mouseHandler.xpos() - centreX));
    }

    /** The sector an angle falls in; an angle exactly on a boundary belongs to the sector it leads. */
    public static int sectorAt(double angleDegrees) {
        double offset = normalize(angleDegrees - sectorStart(0));
        return (int) Math.floor(offset / SECTOR_DEGREES) % SECTORS;
    }

    public static double sectorStart(int slot) {
        return FIRST_SECTOR_CENTRE - SECTOR_DEGREES * 0.5D + SECTOR_DEGREES * slot;
    }

    public static double sectorCentre(int slot) {
        return FIRST_SECTOR_CENTRE + SECTOR_DEGREES * slot;
    }

    /** The ring for a window at the opening progress: {@code 0} just opened, {@code 1} fully grown. */
    public static Ring ring(int width, int height, double growth) {
        // A small window must not push the ring off screen, and the pop must shrink with it, hence the scale.
        double fit = Math.min(1.0D, (Math.min(width, height) * 0.5D - MARGIN) / OUTER_RADIUS);
        double opening = Mth.clamp(growth, 0.0D, 1.0D);
        double scale = fit * (OPEN_SCALE + (1.0D - OPEN_SCALE) * opening);
        return new Ring(width / 2, height / 2, INNER_RADIUS * scale, OUTER_RADIUS * scale, scale);
    }

    private static double normalize(double angleDegrees) {
        return ((angleDegrees % 360.0D) + 360.0D) % 360.0D;
    }

    /**
     * The ring's position and size at one moment of the animation, plus the trigonometry for points on it.
     * {@code scale} rides along because the constants above are full-size numbers.
     */
    public record Ring(int centreX, int centreY, double innerRadius, double outerRadius, double scale) {
        public double x(double angleDegrees, double radius) {
            return this.centreX + radius * Math.cos(Math.toRadians(angleDegrees));
        }

        public double y(double angleDegrees, double radius) {
            return this.centreY + radius * Math.sin(Math.toRadians(angleDegrees));
        }

        public double iconRadius() {
            return (this.innerRadius + this.outerRadius) * 0.5D;
        }

        public double grow() {
            return SELECTED_GROW * this.scale;
        }
    }
}
