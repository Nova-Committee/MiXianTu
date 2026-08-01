package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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

    private AuraChunkTicker() {
    }

    public static void onChunkLoad(Load event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(event.getChunk());
    }

    public static void onChunkUnload(Unload event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        chunks.remove(event.getChunk());
        if (chunks.isEmpty()) LOADED.remove(level);
    }

    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL_TICKS != 0L) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        for (LevelChunk chunk : Set.copyOf(chunks)) {
            chunk.getData(MxtAttachments.AURA_CHUNK).regenerate(INTERVAL_TICKS);
        }
    }
}
