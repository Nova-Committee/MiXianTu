package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * Keeps the per-section aura cache of loaded chunks current. A block edit is applied as a delta, and every
 * sub-chunk is re-read on its own schedule so edits no event reports are still picked up.
 */
@EventBusSubscriber
public final class AuraChunkTicker {
    // 30 to 60 seconds, spread per section: the whole column is never re-read in one tick.
    private static final int REFRESH_MIN_TICKS = 600, REFRESH_SPREAD_TICKS = 600;
    // These three cannot be weak maps: their values hold LevelChunks, a chunk holds its level, so the key would
    // be reachable from the map itself and never collected. Unload events are the only way they are dropped.
    private static final Map<ServerLevel, Set<LevelChunk>> LOADED = Collections.synchronizedMap(new IdentityHashMap<>());
    // Loaded chunks whose pools are not built yet, so the next tick seeds them instead of waiting for a query.
    private static final Map<ServerLevel, Set<LevelChunk>> PENDING = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, Map<LevelChunk, Int2LongMap>> REFRESH = Collections.synchronizedMap(new IdentityHashMap<>());

    private AuraChunkTicker() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        LevelChunk chunk = event.getChunk();
        LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);
        PENDING.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);
        // The save can be older than the world, so the whole column is rebuilt before anything may read it.
        BlockAuraService.rebuild(level, chunk);
        refreshDeadlines(level, chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
        forget(level, event.getChunk());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) release(level);
    }

    private static void release(ServerLevel level) {
        LOADED.remove(level);
        PENDING.remove(level);
        REFRESH.remove(level);
    }

    // A snapshot of the chunks this level currently has loaded, for other per-chunk work that must not scan a
    // whole level every tick. Copied, because the caller iterates while chunks may still load or unload.
    public static Set<LevelChunk> loadedChunks(ServerLevel level) {
        Set<LevelChunk> chunks = LOADED.get(level);
        return chunks == null ? Set.of() : new LinkedHashSet<>(chunks);
    }

    // Clears then immediately rebuilds the persisted block-aura subsection caches of the loaded chunks around
    // a position; returns how many chunks were invalidated.
    public static int clearBlockAuraCachesAround(ServerLevel level, BlockPos center, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Cache clear radius cannot be negative");
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return 0;
        int centerX = SectionPos.blockToSectionCoord(center.getX());
        int centerZ = SectionPos.blockToSectionCoord(center.getZ());
        int cleared = 0;
        for (LevelChunk chunk : new LinkedHashSet<>(chunks)) {
            int x = chunk.getPos().x() - centerX, z = chunk.getPos().z() - centerZ;
            if (Math.abs(x) > radius || Math.abs(z) > radius) continue;
            BlockAuraService.rebuild(level, chunk);
            cleared++;
        }
        return cleared;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        seedPending(level);
        long interval = MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.getValue();
        if (level.getGameTime() % interval != 0L) return;
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        refreshAuraVisitors(level);
        Map<LevelChunk, Int2LongMap> deadlines = REFRESH.get(level);
        for (LevelChunk chunk : new LinkedHashSet<>(chunks)) {
            AuraChunkAttachment aura = chunk.getData(MxtAttachments.AURA_CHUNK);
            if (deadlines != null) refreshDue(level, chunk, deadlines.get(chunk));
            if (!aura.seeded())
                AuraService.getPositionAura(level, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
            aura.regenerateAuras(interval);
        }
    }

    private static void refreshDue(ServerLevel level, LevelChunk chunk, Int2LongMap deadlines) {
        if (deadlines == null) return;
        long now = level.getGameTime();
        IntList due = new IntArrayList();
        for (Int2LongMap.Entry entry : deadlines.int2LongEntrySet())
            if (now >= entry.getLongValue()) due.add(entry.getIntKey());
        for (int i = 0; i < due.size(); i++) {
            int sectionY = due.getInt(i);
            BlockAuraService.rebuild(level, chunk, sectionY);
            deadlines.put(sectionY, nextRefresh(level));
        }
    }

    // The cache a chunk keeps is never trusted from the save alone, so the first tick after a load builds the
    // pools as well: seeding a chunk inside the load callback would resolve zones against a half-built chunk.
    private static void seedPending(ServerLevel level) {
        Set<LevelChunk> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) return;
        for (LevelChunk chunk : new LinkedHashSet<>(pending)) {
            if (chunk.getLevel() != level) continue;
            if (chunk.getData(MxtAttachments.AURA_CHUNK).seeded()) continue;
            AuraService.getPositionAura(level, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
        }
        pending.clear();
        PENDING.remove(level);
    }

    private static void refreshDeadlines(ServerLevel level, LevelChunk chunk) {
        Int2LongMap deadlines = new Int2LongOpenHashMap();
        int minSectionY = chunk.getMinSectionY();
        for (int i = 0; i < chunk.getSectionsCount(); i++) deadlines.put(minSectionY + i, nextRefresh(level));
        // A cached section outside the chunk's own range is stale and has no deadline of its own, so it would
        // otherwise never be cleared.
        for (int sectionY : chunk.getData(MxtAttachments.AURA_CHUNK).blockAuraSections().keySet())
            deadlines.put(sectionY, nextRefresh(level));
        REFRESH.computeIfAbsent(level, ignored -> Collections.synchronizedMap(new IdentityHashMap<>())).put(chunk, deadlines);
    }

    private static long nextRefresh(ServerLevel level) {
        return level.getGameTime() + REFRESH_MIN_TICKS + level.getRandom().nextInt(REFRESH_SPREAD_TICKS);
    }

    private static void forget(ServerLevel level, LevelChunk chunk) {
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks != null) {
            chunks.remove(chunk);
            if (chunks.isEmpty()) LOADED.remove(level);
        }
        Set<LevelChunk> pending = PENDING.get(level);
        if (pending != null) {
            pending.remove(chunk);
            if (pending.isEmpty()) PENDING.remove(level);
        }
        Map<LevelChunk, Int2LongMap> refresh = REFRESH.get(level);
        if (refresh != null) {
            refresh.remove(chunk);
            if (refresh.isEmpty()) REFRESH.remove(level);
        }
    }

    // Rebuilds the transient section visitor counts used to split emitter aura between players. A player counts
    // as an accessor of every loaded section in the same bounded query volume AuraService uses.
    private static void refreshAuraVisitors(ServerLevel level) {
        Set<LevelChunk> chunks = LOADED.get(level);
        if (chunks == null) return;
        for (LevelChunk chunk : new LinkedHashSet<>(chunks))
            chunk.getData(MxtAttachments.AURA_CHUNK).clearAuraVisitors();

        for (ServerPlayer player : level.players()) {
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

    // Both handlers run last so a cancelled edit never reaches the cache, and touch only the block that changed.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        LevelChunk chunk = AuraService.loadedChunk(level, event.getPos());
        if (chunk == null) return;
        // Announced before the block is gone, so the state it leaves behind comes from the state itself.
        BlockAuraService.updateBlock(level, chunk, event.getPos(), event.getState(),
                event.getState().getFluidState().createLegacyBlock());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockPlace(EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        LevelChunk chunk = AuraService.loadedChunk(level, event.getPos());
        if (chunk == null) return;
        // The snapshot holds the state that was there before; for a player the event is post-placement, so
        // getState() is the new state and only the snapshot can tell what was replaced.
        BlockAuraService.updateBlock(level, chunk, event.getPos(), event.getBlockSnapshot().getState(), event.getPlacedBlock());
    }
}
