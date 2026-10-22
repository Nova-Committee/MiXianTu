package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.dispatcher.TickDispatcher;
import com.iafenvoy.mxt.util.dispatcher.TickExecutionLimit;
import com.iafenvoy.mxt.util.dispatcher.TickInterval;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * Keeps the per-section aura cache of loaded chunks current. A block edit is applied as a delta, and every
 * sub-chunk is re-read on its own schedule so edits no event reports are still picked up.
 */
@EventBusSubscriber
public final class AuraChunkTicker {
    private static final TickInterval REFRESH_INTERVAL = TickInterval.random(600, 1199);
    // These maps cannot use weak keys: their values hold chunks or levels, so the key would
    // be reachable from the map itself and never collected. Unload events are the only way they are dropped.
    private static final Map<ServerLevel, Set<LevelChunk>> LOADED = Collections.synchronizedMap(new IdentityHashMap<>());
    // Loaded chunks whose pools are not built yet, so the next tick seeds them instead of waiting for a query.
    private static final Map<ServerLevel, Set<LevelChunk>> PENDING = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, RefreshState> REFRESH = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, AuraMaintenanceState> MAINTENANCE = Collections.synchronizedMap(new IdentityHashMap<>());

    private AuraChunkTicker() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
            LevelChunk chunk = event.getChunk();
            LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);
            PENDING.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);
            // The save can be older than the world, so the whole column is rebuilt before anything may read it.
            BlockAuraService.rebuild(chunk);
            refreshDeadlines(level, chunk);
            int interval = MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.getValue();
            MAINTENANCE.computeIfAbsent(level, key -> new AuraMaintenanceState(key, interval)).load(chunk, level.getGameTime(), interval);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (!(event.getChunk().getLevel() instanceof ServerLevel level)) return;
            forget(level, event.getChunk());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) release(level);
    }

    private static void release(ServerLevel level) {
        LOADED.remove(level);
        PENDING.remove(level);
        RefreshState refresh = REFRESH.remove(level);
        if (refresh != null) refresh.dispatcher.clear();
        AuraMaintenanceState maintenance = MAINTENANCE.remove(level);
        if (maintenance != null) maintenance.close();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
        PENDING.clear();
        REFRESH.values().forEach(state -> state.dispatcher.clear());
        REFRESH.clear();
        MAINTENANCE.values().forEach(AuraMaintenanceState::close);
        MAINTENANCE.clear();
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
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (radius < 0) throw new IllegalArgumentException("Cache clear radius cannot be negative");
            Set<LevelChunk> chunks = LOADED.get(level);
            if (chunks == null) return 0;
            int centerX = SectionPos.blockToSectionCoord(center.getX());
            int centerZ = SectionPos.blockToSectionCoord(center.getZ());
            int cleared = 0;
            for (LevelChunk chunk : new LinkedHashSet<>(chunks)) {
                int x = chunk.getPos().x() - centerX, z = chunk.getPos().z() - centerZ;
                if (Math.abs(x) > radius || Math.abs(z) > radius) continue;
                BlockAuraService.rebuild(chunk);
                refreshDeadlines(level, chunk);
                cleared++;
            }
            return cleared;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLevelTick(LevelTickEvent.Post event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            seedPending(level);
            RefreshState refresh = REFRESH.get(level);
            if (refresh != null) {
                TickDispatcher.TickReport<RefreshTask> report = refresh.dispatcher.tick(level.getGameTime());
                for (TickDispatcher.TaskFailure<RefreshTask> failure : report.failures())
                    MiXianTu.LOGGER.warn("Failed to refresh block aura cache in {} at chunk {} section {}",
                            level.dimension().identifier(), failure.key().chunk.getPos(), failure.key().sectionY, failure.exception());
            }
            AuraMaintenanceState maintenance = MAINTENANCE.get(level);
            if (maintenance != null)
                maintenance.tick(level.getGameTime(), MxtServerConfig.INSTANCE.aura.blockAuraTickInterval.getValue());
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
            AuraService.ensureInitialized(chunk, chunk.getPos().getMiddleBlockPosition(level.getMinY()));
        }
        pending.clear();
        PENDING.remove(level);
    }

    private static void refreshDeadlines(ServerLevel level, LevelChunk chunk) {
        REFRESH.computeIfAbsent(level, RefreshState::new).schedule(chunk, level.getGameTime());
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
        RefreshState refresh = REFRESH.get(level);
        if (refresh != null) {
            refresh.forget(chunk);
            if (refresh.chunks.isEmpty()) REFRESH.remove(level);
        }
        AuraMaintenanceState maintenance = MAINTENANCE.get(level);
        if (maintenance != null) maintenance.forget(chunk);
    }

    // Both handlers run last so a cancelled edit never reaches the cache, and touch only the block that changed.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BreakBlockEvent event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
            LevelChunk chunk = AuraService.loadedChunk(level, event.getPos());
            if (chunk == null) return;
            // Announced before the block is gone, so the state it leaves behind comes from the state itself.
            BlockAuraService.updateBlock(level, chunk, event.getPos(), event.getState(),
                    event.getState().getFluidState().createLegacyBlock());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockPlace(EntityPlaceEvent event) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.AURA)) {
            if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
            LevelChunk chunk = AuraService.loadedChunk(level, event.getPos());
            if (chunk == null) return;
            // The snapshot holds the state that was there before; for a player the event is post-placement, so
            // getState() is the new state and only the snapshot can tell what was replaced.
            BlockAuraService.updateBlock(level, chunk, event.getPos(), event.getBlockSnapshot().getState(), event.getPlacedBlock());
        }
    }

    private static final class RefreshState {
        private final TickDispatcher<RefreshTask> dispatcher;
        private final Map<LevelChunk, List<RefreshTask>> chunks = new IdentityHashMap<>();

        private RefreshState(ServerLevel level) {
            this.dispatcher = new TickDispatcher<>(level.getGameTime(), level.getSeed() ^ level.dimension().identifier().hashCode(), TickExecutionLimit.adaptive(8, 600));
        }

        private void schedule(LevelChunk chunk, long now) {
            this.dispatcher.advanceTo(now);
            this.forget(chunk);
            List<RefreshTask> tasks = new ArrayList<>();
            int minSectionY = chunk.getMinSectionY();
            for (int i = 0; i < chunk.getSectionsCount(); i++) this.schedule(chunk, minSectionY + i, tasks);
            for (int sectionY : chunk.getData(MxtAttachments.AURA_CHUNK).blockAuraSections().keySet())
                if (sectionY < minSectionY || sectionY >= minSectionY + chunk.getSectionsCount())
                    this.schedule(chunk, sectionY, tasks);
            this.chunks.put(chunk, tasks);
        }

        private void schedule(LevelChunk chunk, int sectionY, List<RefreshTask> tasks) {
            RefreshTask task = new RefreshTask(chunk, sectionY);
            this.dispatcher.schedule(task, REFRESH_INTERVAL, () -> BlockAuraService.rebuild(chunk, sectionY));
            tasks.add(task);
        }

        private void forget(LevelChunk chunk) {
            List<RefreshTask> tasks = this.chunks.remove(chunk);
            if (tasks != null) tasks.forEach(this.dispatcher::cancel);
        }
    }

    private record RefreshTask(LevelChunk chunk, int sectionY) {
    }
}
