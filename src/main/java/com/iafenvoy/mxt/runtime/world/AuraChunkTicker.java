package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.attachment.AuraChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Regenerates aura only for chunks observed as loaded, without scanning a whole level each tick.
 */
public final class AuraChunkTicker {
    private static final long INTERVAL_TICKS = 20L;
    private static final Map<ServerLevel, Set<LevelChunk>> LOADED = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, Set<LevelChunk>> DIRTY = Collections.synchronizedMap(new IdentityHashMap<>());

    private AuraChunkTicker() {
    }

    public static void onChunkLoad(Load event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(event.getChunk());
        BlockAuraService.rebuild(level, event.getChunk());
    }

    public static void onChunkUnload(Unload event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        chunks.remove(event.getChunk());
        if (chunks.isEmpty()) LOADED.remove(level);
        Set<LevelChunk> dirty = DIRTY.get(level);
        if (dirty != null) {
            dirty.remove(event.getChunk());
            if (dirty.isEmpty()) DIRTY.remove(level);
        }
    }

    public static void markDirty(ServerLevel level, BlockPos pos) {
        DIRTY.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(level.getChunkAt(pos));
    }

    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL_TICKS != 0L) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        Set<LevelChunk> dirty = DIRTY.get(level);
        if (dirty != null) {
            for (LevelChunk chunk : Set.copyOf(dirty)) BlockAuraService.rebuild(level, chunk);
            dirty.clear();
            DIRTY.remove(level);
        }
        for (LevelChunk chunk : Set.copyOf(chunks)) {
            AuraChunkData aura = chunk.getData(MxtAttachments.AURA_CHUNK);
            if (!aura.initialized())
                AuraService.getPositionAura(level, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
            aura.regenerate(INTERVAL_TICKS);
        }
    }
}
