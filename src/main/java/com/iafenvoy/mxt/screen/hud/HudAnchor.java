package com.iafenvoy.mxt.screen.hud;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * One of the eight points of the window an element can be bound to: the four corners and the four edge midpoints.
 * A bound element keeps its distance from that point in pixels, so the same layout survives a resize and a GUI scale
 * change, and the same point of the element's own rectangle is what the stored offset is measured to.
 */
public enum HudAnchor {
    LEFT_TOP(Horizontal.LEFT, Vertical.TOP),
    CENTER_TOP(Horizontal.CENTER, Vertical.TOP),
    RIGHT_TOP(Horizontal.RIGHT, Vertical.TOP),
    LEFT_CENTER(Horizontal.LEFT, Vertical.CENTER),
    RIGHT_CENTER(Horizontal.RIGHT, Vertical.CENTER),
    LEFT_BOTTOM(Horizontal.LEFT, Vertical.BOTTOM),
    CENTER_BOTTOM(Horizontal.CENTER, Vertical.BOTTOM),
    RIGHT_BOTTOM(Horizontal.RIGHT, Vertical.BOTTOM);

    // Held as an object rather than as one name, so a file says which of the two axes each half belongs to.
    private static final MapCodec<Axes> AXES_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Horizontal.CODEC.fieldOf("horizontal").forGetter(Axes::horizontal),
            Vertical.CODEC.fieldOf("vertical").forGetter(Axes::vertical)
    ).apply(i, Axes::new));

    public static final Codec<HudAnchor> CODEC = AXES_CODEC.codec().comapFlatMap(
            axes -> of(axes.horizontal(), axes.vertical())
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "The middle of the window is not an anchor: "
                            + axes.horizontal().getSerializedName() + "/" + axes.vertical().getSerializedName())),
            anchor -> new Axes(anchor.horizontal, anchor.vertical));

    private final Horizontal horizontal;
    private final Vertical vertical;

    HudAnchor(Horizontal horizontal, Vertical vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static Optional<HudAnchor> of(Horizontal horizontal, Vertical vertical) {
        for (HudAnchor anchor : values())
            if (anchor.horizontal == horizontal && anchor.vertical == vertical) return Optional.of(anchor);
        return Optional.empty();
    }

    // The window point this anchor stands for; the offset an element stores is measured from it.
    public int windowX(int windowWidth) {
        return switch (this.horizontal) {
            case LEFT -> 0;
            case CENTER -> windowWidth / 2;
            case RIGHT -> windowWidth;
        };
    }

    public int windowY(int windowHeight) {
        return switch (this.vertical) {
            case TOP -> 0;
            case CENTER -> windowHeight / 2;
            case BOTTOM -> windowHeight;
        };
    }

    // The point of the element's own rectangle this anchor stands for, and the way back from a top-left corner.
    public int leftOf(int anchorX, int width) {
        return anchorX - this.toLeft(width);
    }

    public int topOf(int anchorY, int height) {
        return anchorY - this.toTop(height);
    }

    public int toLeft(int width) {
        return switch (this.horizontal) {
            case LEFT -> 0;
            case CENTER -> width / 2;
            case RIGHT -> width;
        };
    }

    public int toTop(int height) {
        return switch (this.vertical) {
            case TOP -> 0;
            case CENTER -> height / 2;
            case BOTTOM -> height;
        };
    }

    // The square the editor draws and binds against, kept against the window edge rather than half outside it.
    public ScreenBounds marker(int windowWidth, int windowHeight, int size) {
        int x = switch (this.horizontal) {
            case LEFT -> 0;
            case CENTER -> windowWidth / 2 - size / 2;
            case RIGHT -> windowWidth - size;
        };
        int y = switch (this.vertical) {
            case TOP -> 0;
            case CENTER -> windowHeight / 2 - size / 2;
            case BOTTOM -> windowHeight - size;
        };
        return new ScreenBounds(x, y, size, size);
    }

    // Whether the anchor sits on the element's own bottom edge, which is the edge a stack of blocks grows away
    // from: a column of bars pinned to the bottom of the window is what keeps the hotbar clear.
    public boolean atBottom() {
        return this.vertical == Vertical.BOTTOM;
    }

    public String key() {
        return this.horizontal.getSerializedName() + "_" + this.vertical.getSerializedName();
    }

    private record Axes(Horizontal horizontal, Vertical vertical) {
    }

    public enum Horizontal implements StringRepresentable {
        LEFT,
        CENTER,
        RIGHT;

        public static final Codec<Horizontal> CODEC = StringRepresentable.fromEnum(Horizontal::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Vertical implements StringRepresentable {
        TOP,
        CENTER,
        BOTTOM;

        public static final Codec<Vertical> CODEC = StringRepresentable.fromEnum(Vertical::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
