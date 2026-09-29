package com.iafenvoy.mxt.data.ability.render;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * The seven poses a mount can be in, in two independent groups. Each group is total, so every frame answers exactly
 * one value per group and an unwritten animation entry simply asks for the animation named after the pose.
 */
public enum MountPose implements StringRepresentable {
    IDLE(Axis.MOTION), MOVING(Axis.MOTION), ASCENDING(Axis.MOTION), DESCENDING(Axis.MOTION),
    EMPTY(Axis.CREW), RIDDEN(Axis.CREW), CARRYING(Axis.CREW);
    public static final Codec<MountPose> CODEC = StringRepresentable.fromEnum(MountPose::values);
    // Position, not rotation: a driver looking around must not read as moving.
    private static final double MOVED = 1.0E-4D;

    private final Axis axis;

    MountPose(Axis axis) {
        this.axis = axis;
    }

    /**
     * The movement group, from one tick's change in position: vertical wins, so climbing is never also "moving".
     */
    public static MountPose motion(double horizontalDelta, double verticalDelta) {
        if (verticalDelta > MOVED) return ASCENDING;
        if (verticalDelta < -MOVED) return DESCENDING;
        return horizontalDelta > MOVED ? MOVING : IDLE;
    }

    // The crew group: the driver alone, carrying passengers, or (briefly, at take-off) nobody at all.
    public static MountPose crew(int riders) {
        return riders <= 0 ? EMPTY : riders == 1 ? RIDDEN : CARRYING;
    }

    public Axis axis() {
        return this.axis;
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public enum Axis {
        MOTION, CREW
    }
}
