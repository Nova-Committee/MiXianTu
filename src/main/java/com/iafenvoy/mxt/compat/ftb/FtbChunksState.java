package com.iafenvoy.mxt.compat.ftb;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.config.FTBChunksWorldConfig;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.UUID;

/**
 * The part of the FTB Chunks integration that names FTB types. Loaded only from inside
 * {@link FtbChunksCompat}'s mod-list guard, never directly.
 */
final class FtbChunksState {
    private FtbChunksState() {
    }

    /**
     * Whether FTB Chunks has its global protection switch on. {@code DISABLE_PROTECTION} is a server config
     * value read directly, because FTB Chunks exposes no API for it.
     */
    static boolean protectionEnabled() {
        return !FTBChunksWorldConfig.DISABLE_PROTECTION.get();
    }

    /**
     * Whether the chunk containing a position is claimed by anybody. Asked through the public API so the
     * one query this mod makes stays on the surface FTB Chunks documents.
     */
    static boolean chunkClaimed(ServerLevel level, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return false;
        return FTBChunksAPI.api().getOwningTeam(level, chunk(pos)).isPresent();
    }

    /**
     * The player to ask about a claimed position, if there is one. A console-owned server team has no
     * player to ask and is filtered out here rather than answered about by a nil UUID.
     */
    static Optional<UUID> claimOwner(ServerLevel level, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBChunksAPI.api().getOwningTeam(level, chunk(pos))
                .map(Team::getOwner)
                .filter(owner -> !Util.NIL_UUID.equals(owner));
    }

    /**
     * Whether FTB Chunks itself would let this player edit blocks at the position. This is the claim
     * plugin's own answer, asked through its own predicate rather than reimplemented from the privacy mode;
     * an unclaimed position counts as editable.
     */
    static boolean mayEdit(ServerLevel level, BlockPos pos, UUID actorId) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return true;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(actorId);
        if (player == null) return false;
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        return chunk == null || chunk.getTeamData().canPlayerUse(player, FTBChunksProperties.BLOCK_EDIT_MODE);
    }

    private static ChunkPos chunk(BlockPos pos) {
        return new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
