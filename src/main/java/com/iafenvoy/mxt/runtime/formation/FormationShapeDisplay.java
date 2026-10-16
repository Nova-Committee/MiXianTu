package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.particle.FormationParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Sends one frame of a formation's figure to the players who are close enough to be looking at it. The vanilla
 * broadcast helper is not used because its own cutoff is a flat 32 blocks, which would clip the outline of any larger
 * formation. The frame is sent as one order per player: the layout and the rest of the beat's growth happen on the
 * client, so a dense figure costs no more traffic than a sparse one.
 */
public final class FormationShapeDisplay {
    /**
     * How far past its own radius a formation still draws for.
     */
    public static final double VISIBLE_MARGIN = 32.0D;
    /**
     * How far above the bottom of the controller block the figure is drawn. It is written on the ground, and at the
     * block's own centre a large ring reads as floating above it.
     */
    public static final double DRAW_HEIGHT = 0.1D;

    private FormationShapeDisplay() {
    }

    // A frame carries points around this centre, so how high a figure sits is decided only here.
    public static Vec3 center(BlockPos controller) {
        return new Vec3(controller.getX() + 0.5D, controller.getY() + DRAW_HEIGHT, controller.getZ() + 0.5D);
    }

    public static void draw(ServerLevel level, BlockPos controller, FormationParticleOptions frame) {
        Vec3 center = center(controller);
        for (ServerPlayer player : level.players()) {
            if (!visible(center, frame.radius(), player.position())) continue;
            level.sendParticles(player, frame, true, false,
                    center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // Reach is the radius plus a fixed margin, because standing at the edge of an array is exactly when a player wants
    // to see where it ends.
    public static boolean visible(Vec3 center, double radius, Vec3 position) {
        double reach = radius + VISIBLE_MARGIN;
        return position.distanceToSqr(center) <= reach * reach;
    }
}
