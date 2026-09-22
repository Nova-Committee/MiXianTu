package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction;
import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a formation's boundary for the players close enough to be looking at it. The vanilla broadcast helper
 * is not used because its own cutoff is a flat 32 blocks, which would clip the outline of any larger formation.
 */
public final class FormationRangeDisplay {
    /**
     * How far past its own radius a formation still draws for.
     */
    public static final double VISIBLE_MARGIN = 32.0D;

    private FormationRangeDisplay() {
    }

    public static void draw(ServerLevel level, RangeDisplayFormationAction module, BlockPos controller, double radius) {
        if (!due(module, level.getGameTime(), FormationWorldTicker.PERIOD)) return;
        Vec3 center = controller.getCenter();
        List<Vec3> points = points(module.shape(), center, radius, module.points());
        for (ServerPlayer player : level.players()) {
            if (!visible(center, radius, player.position())) continue;
            for (Vec3 point : points) {
                // One particle per point, no spread: the point is the message, and the broadcast limiter
                // would use a different cutoff from the one that chose this player.
                level.sendParticles(player, module.particle(), true, false,
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

    // Counted in the array's periods, so an interval of one means every period: the ticker only reaches here on
    // a period boundary.
    public static boolean due(RangeDisplayFormationAction module, long gameTime, long period) {
        return Math.floorDiv(gameTime, period) % module.intervalPeriods() == 0L;
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
}
