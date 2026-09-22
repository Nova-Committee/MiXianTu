package com.iafenvoy.mxt.compat.ftb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/**
 * FTB Chunks' protection state, kept behind a mod-list check. This class declares nothing from FTB and is
 * reachable unconditionally; the class that does reference FTB is reached only from inside the guard, since one
 * FTB type here would stop every server without FTB Chunks from starting.
 */
public final class FtbChunksCompat {
    private static final String FTB_CHUNKS = "ftbchunks";

    private FtbChunksCompat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(FTB_CHUNKS);
    }

    // Requires both the mod and its global disable_protection option; team-level privacy modes are not visible.
    public static boolean claimsProtect() {
        return loaded() && FtbChunksState.protectionEnabled();
    }

    // The claim is the unit of jurisdiction here, not a permission.
    public static boolean chunkClaimed(ServerLevel level, BlockPos pos) {
        return loaded() && FtbChunksState.chunkClaimed(level, pos);
    }

    public static Optional<UUID> claimOwner(ServerLevel level, BlockPos pos) {
        return loaded() ? FtbChunksState.claimOwner(level, pos) : Optional.empty();
    }

    public static boolean mayEdit(ServerLevel level, BlockPos pos, UUID actorId) {
        return !loaded() || FtbChunksState.mayEdit(level, pos, actorId);
    }
}
