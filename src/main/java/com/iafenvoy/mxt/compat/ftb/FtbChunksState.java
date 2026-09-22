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
 * The FTB-typed half of the FTB Chunks integration, loaded only from inside {@link FtbChunksCompat}'s mod-list
 * guard, never directly.
 */
final class FtbChunksState {
    private FtbChunksState() {
    }

    // DISABLE_PROTECTION is a server config value read directly: FTB Chunks exposes no API for it.
    static boolean protectionEnabled() {
        return !FTBChunksWorldConfig.DISABLE_PROTECTION.get();
    }

    static boolean chunkClaimed(ServerLevel level, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return false;
        return FTBChunksAPI.api().getOwningTeam(level, chunk(pos)).isPresent();
    }

    // A console-owned server team has no player to ask and is filtered out, not answered about by a nil UUID.
    static Optional<UUID> claimOwner(ServerLevel level, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBChunksAPI.api().getOwningTeam(level, chunk(pos))
                .map(Team::getOwner)
                .filter(owner -> !Util.NIL_UUID.equals(owner));
    }

    // FTB Chunks' own predicate rather than a reimplementation of its privacy mode; unclaimed counts as editable.
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
