package com.iafenvoy.mxt.compat.ftb;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.UUID;

/**
 * FTB Chunks' protection state, kept behind a mod-list check so that neither this class nor anything that
 * asks it ever touches an FTB type when FTB Chunks is not installed.
 *
 * <p>The split between this class and {@link FtbChunksState} <em>is</em> the soft dependency, exactly as it
 * is for FTB Teams: this one declares nothing from FTB, so it can be referenced unconditionally, and the
 * class that does reference FTB is reached only from inside the guard, where the JVM loads it on first
 * use. One FTB type in this file would stop every server without FTB Chunks from starting.</p>
 *
 * <p>Reads are live rather than cached: a mod list is fixed once the game started, and the config value
 * behind {@link #claimsProtect()} is read from a field that is populated when the server starts and can be
 * edited in game, so caching it would only add a way to be wrong.</p>
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
     * Whether claim protection is actually in force right now.
     *
     * <p>Both halves matter to a caller that is about to hand its own protection over: the mod has to be
     * there, and its global {@code disable_protection} option has to be off. A server that runs FTB Chunks
     * purely so players can force-load chunks — which is what that option's own description suggests it
     * for — protects nothing, and a formation that delegated to it would protect nothing either.</p>
     *
     * <p>What this cannot see is the <em>team</em> level: FTB Chunks decides protection per owning team, so
     * a team whose privacy modes are all public is unprotected while this still reports true. Recomputing
     * that here would mean reimplementing the claim plugin's own rule, which is the one thing delegation
     * exists to avoid.</p>
     */
    public static boolean claimsProtect() {
        return loaded() && FtbChunksState.protectionEnabled();
    }

    /**
     * Whether the chunk containing a position is claimed by anybody.
     *
     * <p>Deliberately not "claimed by <em>you</em>": the linkage options make the claim the unit of
     * jurisdiction rather than a permission, so a ward standing in somebody else's claim is inside claim
     * land just the same — that the owner let it be built there is the owner's business.</p>
     */
    public static boolean chunkClaimed(ServerLevel level, BlockPos pos) {
        return loaded() && FtbChunksState.chunkClaimed(level, pos);
    }

    /**
     * The player whose friend list speaks for a claimed position, if the claim has one.
     */
    public static Optional<UUID> claimOwner(ServerLevel level, BlockPos pos) {
        return loaded() ? FtbChunksState.claimOwner(level, pos) : Optional.empty();
    }

    /**
     * Whether FTB Chunks would let this player edit blocks here.
     *
     * <p>Answered by FTB Chunks itself rather than by a copy of its rule, because this is the "has
     * permission" half of a permission check and a copy would be a second, drifting definition of it.</p>
     */
    public static boolean mayEdit(ServerLevel level, BlockPos pos, UUID actorId) {
        return !loaded() || FtbChunksState.mayEdit(level, pos, actorId);
    }
}
