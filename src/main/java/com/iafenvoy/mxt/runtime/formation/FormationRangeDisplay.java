package com.iafenvoy.mxt.runtime.formation;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The display a formation used before its figure was the ring-and-star shapes: a ring of points at the centre's own
 * height, or the same points spread over a sphere. Kept whole - its own margin, its own shape enum, its own reach rule
 * - so it can be put back by pointing a caller at it; nothing calls it now.
 */
public final class FormationRangeDisplay {
    /**
     * How far past its own radius a formation still draws for.
     */
    public static final double VISIBLE_MARGIN = 32.0D;

    private FormationRangeDisplay() {
    }

    public static void draw(ServerLevel level, ParticleOptions particle, Shape shape, int count, BlockPos controller,
                            double radius) {
        Vec3 center = controller.getCenter();
        List<Vec3> points = points(shape, center, radius, count);
        for (ServerPlayer player : level.players()) {
            if (!visible(center, radius, player.position())) continue;
            for (Vec3 point : points) {
                // One particle per point, no spread: the point is the message, and the broadcast limiter
                // would use a different cutoff from the one that chose this player.
                level.sendParticles(player, particle, true, false,
                        point.x(), point.y(), point.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // Reach is the radius plus a fixed margin, because standing at the edge of an array is exactly when a player
    // wants to see where it ends.
    public static boolean visible(Vec3 center, double radius, Vec3 position) {
        double reach = radius + VISIBLE_MARGIN;
        return position.distanceToSqr(center) <= reach * reach;
    }

    // RING is a circle at the centre's own height; SPHERE spreads the same number of points over the whole
    // surface with the golden angle, evenly rather than in a latitude grid that would clump them at the poles.
    public static List<Vec3> points(Shape shape, Vec3 center, double radius, int count) {
        List<Vec3> points = new ArrayList<>(count);
        if (shape == Shape.RING) {
            for (int index = 0; index < count; index++) {
                double angle = 2.0D * Math.PI * index / count;
                points.add(new Vec3(center.x + radius * Math.cos(angle), center.y, center.z + radius * Math.sin(angle)));
            }
            return points;
        }
        double goldenAngle = Math.PI * (1.0D + Math.sqrt(5.0D));
        for (int index = 0; index < count; index++) {
            double y = 1.0D - 2.0D * (index + 0.5D) / count;
            double ring = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double angle = goldenAngle * index;
            points.add(new Vec3(center.x + radius * ring * Math.cos(angle),
                    center.y + radius * y,
                    center.z + radius * ring * Math.sin(angle)));
        }
        return points;
    }

    public enum Shape implements StringRepresentable {
        RING,
        SPHERE;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
