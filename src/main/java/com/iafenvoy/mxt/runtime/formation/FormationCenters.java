package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Resolves which block position a player actually meant when they clicked a formation plate.
 *
 * <p>A formation is activated by clicking the block at its centre, and clicking the block one step off
 * is the most common way to fail at it: nothing in the world distinguishes the centre from its
 * neighbours, so the player is told the structure does not match and has no way to tell which of the
 * twenty-six blocks around them would have worked. The candidates are therefore the clicked position
 * and the full 3x3x3 block around it — exactly the set a player can mean by "here", one block in any
 * direction including diagonally.</p>
 *
 * <p>The search is ordered by distance to the clicked block, so the click itself always wins when it
 * works: nudging a slab or a stair toward the intended centre can never silently move an activation
 * somewhere else. Only when the clicked position is not a valid centre does it consider its
 * neighbours, and it takes the nearest one.</p>
 *
 * <p>This answers only "where is the structure", not "should anything happen there". The caller looks
 * the result up in the level's index and decides whether that means activating or dismantling.</p>
 */
public final class FormationCenters {
    private FormationCenters() {
    }

    /**
     * @return the nearest position in the clicked 3x3x3 that satisfies the definition, or empty when
     * none does
     */
    public static Optional<BlockPos> resolve(ServerLevel level, BlockPos clicked, Formation definition) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = clicked.offset(x, y, z);
                    // Reading through the level keeps a candidate in an unloaded chunk from throwing or
                    // generating terrain; it simply reads as air and fails the structure check.
                    if (!FormationStructureValidator.STRUCTURE.matches(level, candidate, definition)) continue;
                    double distance = distanceSquared(candidate, clicked);
                    if (distance >= bestDistance) continue;
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static double distanceSquared(BlockPos first, BlockPos second) {
        double x = first.getX() - second.getX();
        double y = first.getY() - second.getY();
        double z = first.getZ() - second.getZ();
        return x * x + y * y + z * z;
    }
}
