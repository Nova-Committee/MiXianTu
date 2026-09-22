package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.Formation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Resolves which block position a player actually meant when they clicked a formation plate: the clicked
 * position and the full 3x3x3 around it, searched in order of distance, so the click itself always wins when it
 * works. Neither entry point decides what should happen there; the caller looks the result up in the index.
 */
public final class FormationCenters {
    private FormationCenters() {
    }

    // The nearest position in the clicked 3x3x3 that satisfies the definition, or empty when none does.
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

    // Both the centre and which formation is standing there, for a plate that was never bound to one. The
    // caller decides which definitions may be tried and in what order, so a caller that sorts by id gets a
    // deterministic answer where two definitions describe the same structure.
    public static Optional<Match> resolveAny(ServerLevel level, BlockPos clicked, List<? extends Holder<Formation>> candidates) {
        Match best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = clicked.offset(x, y, z);
                    double distance = distanceSquared(candidate, clicked);
                    if (distance >= bestDistance) continue;
                    for (Holder<Formation> formation : candidates) {
                        if (!FormationStructureValidator.STRUCTURE.matches(level, candidate, formation.value()))
                            continue;
                        best = new Match(candidate, formation);
                        bestDistance = distance;
                        break;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public record Match(BlockPos center, Holder<Formation> formation) {
    }
}
