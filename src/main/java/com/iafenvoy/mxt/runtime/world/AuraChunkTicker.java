package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Regenerates aura only for chunks observed as loaded, without scanning a whole level each tick.
 */
@EventBusSubscriber
public final class AuraChunkTicker {
    private static final long INTERVAL_TICKS = 20L;
    private static final Map<ServerLevel, Set<LevelChunk>> LOADED = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, Set<LevelChunk>> DIRTY = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, Map<LevelChunk, Long>> NEXT_REFRESH = Collections.synchronizedMap(new IdentityHashMap<>());

    private AuraChunkTicker() {
    }

    @SubscribeEvent
    public static void onChunkLoad(Load event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(event.getChunk());
        NEXT_REFRESH.computeIfAbsent(level, ignored -> Collections.synchronizedMap(new IdentityHashMap<>()))
                .put(event.getChunk(), nextRefresh(level));
        BlockAuraService.rebuild(level, event.getChunk());
    }

    @SubscribeEvent
    public static void onChunkUnload(Unload event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        chunks.remove(event.getChunk());
        if (chunks.isEmpty()) LOADED.remove(level);
        Map<LevelChunk, Long> refresh = NEXT_REFRESH.get(level);
        if (refresh != null) {
            refresh.remove(event.getChunk());
            if (refresh.isEmpty()) NEXT_REFRESH.remove(level);
        }
        Set<LevelChunk> dirty = DIRTY.get(level);
        if (dirty != null) {
            dirty.remove(event.getChunk());
            if (dirty.isEmpty()) DIRTY.remove(level);
        }
    }

    public static void markDirty(ServerLevel level, BlockPos pos) {
        DIRTY.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(level.getChunkAt(pos));
    }

    /**
     * Clears and immediately rebuilds the persisted block-aura subsection
     * caches in the loaded chunks around a position.
     *
     * @return number of chunks invalidated
     */
    public static int clearBlockAuraCachesAround(ServerLevel level, BlockPos center, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Cache clear radius cannot be negative");
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return 0;
        ChunkPos centerChunk = new ChunkPos(SectionPos.blockToSectionCoord(center.getX()),
                SectionPos.blockToSectionCoord(center.getZ()));
        int cleared = 0;
        for (LevelChunk chunk : new LinkedHashSet<>(chunks)) {
            ChunkPos chunkPos = chunk.getPos();
            if (Math.abs(chunkPos.x() - centerChunk.x()) > radius || Math.abs(chunkPos.z() - centerChunk.z()) > radius)
                continue;
            chunk.getData(MxtAttachments.AURA_CHUNK).clearBlockAuraCache();
            BlockAuraService.rebuild(level, chunk);
            cleared++;
        }
        return cleared;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % INTERVAL_TICKS != 0L) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        Set<LevelChunk> dirty = DIRTY.get(level);
        if (dirty != null) {
            for (LevelChunk chunk : new LinkedHashSet<>(dirty)) BlockAuraService.rebuild(level, chunk);
            dirty.clear();
            DIRTY.remove(level);
        }
        refreshAuraVisitors(level);
        for (LevelChunk chunk : new LinkedHashSet<>(chunks)) {
            AuraChunkAttachment aura = chunk.getData(MxtAttachments.AURA_CHUNK);
            Map<LevelChunk, Long> refresh = NEXT_REFRESH.get(level);
            if (refresh != null && level.getGameTime() >= refresh.getOrDefault(chunk, Long.MAX_VALUE)) {
                BlockAuraService.rebuild(level, chunk);
                refresh.put(chunk, nextRefresh(level));
            }
            if (!aura.initialized())
                AuraService.getPositionAura(level, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
            aura.regenerateAuras(INTERVAL_TICKS);
        }
    }

    private static long nextRefresh(ServerLevel level) {
        return level.getGameTime() + 200L + level.getRandom().nextInt(400);
    }

    /**
     * Rebuilds the transient section visitor counts used to split emitter aura
     * between players. A player is considered an accessor of every loaded
     * section in the same bounded 7x7x7 query volume used by AuraService.
     */
    private static void refreshAuraVisitors(ServerLevel level) {
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        for (LevelChunk chunk : new LinkedHashSet<>(chunks))
            chunk.getData(MxtAttachments.AURA_CHUNK).clearAuraVisitors();

        for (var player : level.players()) {
            SectionPos center = SectionPos.of(player.blockPosition());
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        SectionPos section = SectionPos.of(center.x() + dx, center.y() + dy, center.z() + dz);
                        if (!level.getChunkSource().hasChunk(section.x(), section.z())) continue;
                        BlockPos sectionOrigin = new BlockPos(SectionPos.sectionToBlockCoord(section.x()),
                                SectionPos.sectionToBlockCoord(section.y()),
                                SectionPos.sectionToBlockCoord(section.z()));
                        level.getChunkAt(sectionOrigin).getData(MxtAttachments.AURA_CHUNK).addAuraVisitor(section);
                    }
                }
            }
        }
    }

    /**
     * Runs after protection handlers so canceled block changes do not invalidate aura caches.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level
                && BlockAuraService.matches(level, event.getState())) markDirty(level, event.getPos());
    }

    /**
     * Runs after protection handlers so canceled block changes do not invalidate aura caches.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockPlace(EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level
                && BlockAuraService.matches(level, event.getPlacedBlock())) markDirty(level, event.getPos());
    }
}
