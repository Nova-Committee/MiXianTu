package com.iafenvoy.mxt.compat.ftb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/**
 * FTB Chunks' protection state, kept behind a mod-list check. This class declares nothing from FTB and is
 * reachable unconditionally; the class that does reference FTB is reached only from inside the guard, and
 * one FTB type here would stop every server without FTB Chunks from starting.
 */
public final class FtbChunksCompat {
    private static final String FTB_CHUNKS = "ftbchunks";

    private FtbChunksCompat() {
    }

    /**
     * Whether FTB Chunks is installed.
     */
    public static boolean loaded() {
        return ModList.get().isLoaded(FTB_CHUNKS);
    }

    /**
     * Whether claim protection is in force: both the mod and its global {@code disable_protection} option
     * have to be in the right state. Team-level privacy modes are not visible here.
     */
    public static boolean claimsProtect() {
        return loaded() && FtbChunksState.protectionEnabled();
    }

    /**
     * Whether the chunk containing a position is claimed by anybody: the claim is the unit of
     * jurisdiction here, not a permission.
     */
    public static boolean chunkClaimed(ServerLevel level, BlockPos pos) {
        return loaded() && FtbChunksState.chunkClaimed(level, pos);
    }

    public static Optional<UUID> claimOwner(ServerLevel level, BlockPos pos) {
        return loaded() ? FtbChunksState.claimOwner(level, pos) : Optional.empty();
    }

    /**
     * Whether FTB Chunks would let this player edit blocks here, answered by FTB Chunks itself rather than
     * by a copy of its rule.
     */
    public static boolean mayEdit(ServerLevel level, BlockPos pos, UUID actorId) {
        return !loaded() || FtbChunksState.mayEdit(level, pos, actorId);
    }
}
