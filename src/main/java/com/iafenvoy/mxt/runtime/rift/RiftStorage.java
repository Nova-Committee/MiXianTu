package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.attachment.RiftChunkAttachment;
import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@EventBusSubscriber
public final class RiftStorage {
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    private RiftStorage() {
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, _ -> new State());
    }

    static void initialize(ServerLevel level, RiftWorldAttachment world) {
        State state = state(level);
        if (state.initialized == world) return;
        state.initialized = world;
        state.pending.putAll(state.loaded);
        for (RiftRecord record : world.legacy()) {
            if (!record.expired(RiftService.now(level))) write(level, record, mesh(record));
            world.migrated(record.id());
        }
        for (RiftLocation location : world.locations()) RiftService.schedule(level, location);
    }

    static @Nullable RiftRecord read(ServerLevel level, UUID id) {
        RiftWorldAttachment world = RiftService.world(level);
        RiftLocation location = world.location(id);
        if (location == null) return null;
        if (location.expired(RiftService.now(level))) {
            RiftService.remove(level, id);
            return null;
        }
        RiftRecord cached = world.get(id);
        if (cached != null) return cached;
        ChunkPos pos = ChunkPos.unpack(location.chunk());
        LevelChunk chunk = level.getChunk(pos.x(), pos.z());
        RiftChunkAttachment data = chunk.getExistingData(MxtAttachments.RIFT_CHUNK).orElse(null);
        RiftRecord record = data == null ? null : data.get(id);
        if (record == null || record.expired(RiftService.now(level))) {
            RiftService.remove(level, id);
            return null;
        }
        restore(level, record);
        state(level).dirty.add(id);
        return record;
    }

    static void write(ServerLevel level, RiftRecord record, RiftGeometry.Mesh mesh) {
        RiftWorldAttachment world = RiftService.world(level);
        RiftLocation old = world.location(record.id());
        RiftLocation target = RiftLocation.of(record, mesh);
        ChunkPos pos = ChunkPos.unpack(target.chunk());
        LevelChunk chunk = level.getChunk(pos.x(), pos.z());
        chunk.getData(MxtAttachments.RIFT_CHUNK).put(record);
        chunk.markUnsaved();
        if (old != null && old.chunk() != target.chunk()) eraseLoaded(level, old.chunk(), record.id());
        world.put(record, mesh);
        state(level).dirty.add(record.id());
    }

    private static void restore(ServerLevel level, RiftRecord record) {
        RiftWorldAttachment world = RiftService.world(level);
        RiftGeometry.Mesh mesh = mesh(record);
        // Older directories may still point to the placement origin; move the record before changing its locator.
        if (world.location(record.id()).chunk() != RiftLocation.of(record, mesh).chunk()) write(level, record, mesh);
        else world.put(record, mesh);
    }

    static void eraseLoaded(ServerLevel level, long position, UUID id) {
        ChunkPos pos = ChunkPos.unpack(position);
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null) return;
        RiftChunkAttachment data = chunk.getExistingData(MxtAttachments.RIFT_CHUNK).orElse(null);
        if (data != null && data.get(id) != null) {
            data.remove(id);
            chunk.markUnsaved();
        }
    }

    private static RiftGeometry.Mesh mesh(RiftRecord record) {
        return RiftGeometry.mesh(record.data().shape(), record.data().placement(),
                RiftGeometry.triangulate(record.data().shape()).result().orElseThrow());
    }

    @SubscribeEvent
    public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = state(level);
        LevelChunk chunk = event.getChunk();
        state.loaded.put(chunk.getPos().pack(), chunk);
        // Load fires before FULL; cross-chunk reads and tracking must wait for the next tick.
        state.pending.put(chunk.getPos().pack(), chunk);
    }

    @SubscribeEvent
    public static void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = STATES.get(level);
        if (state == null) return;
        long pos = event.getChunk().getPos().pack();
        if (!state.loaded.remove(pos, event.getChunk())) return;
        state.pending.remove(pos, event.getChunk());
        RiftWorldAttachment world = level.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
        if (world == null) return;
        state.dirty.addAll(world.runtime().inChunk(pos));
        for (RiftLocation location : world.locations()) if (location.chunk() == pos) state.dirty.add(location.id());
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) refresh(level);
    }

    public static void refresh(ServerLevel level) {
        RiftWorldAttachment world = level.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
        State state = STATES.get(level);
        if (world == null) {
            if (state != null) state.pending.clear();
            return;
        }
        initialize(level, world);
        state = state(level);
        List<LevelChunk> pending = List.copyOf(state.pending.values());
        state.pending.clear();
        boolean changed = false;
        for (LevelChunk chunk : pending) {
            long pos = chunk.getPos().pack();
            if (state.loaded.get(pos) != chunk) continue;
            RiftChunkAttachment data = chunk.getExistingData(MxtAttachments.RIFT_CHUNK).orElse(null);
            if (data != null) for (RiftRecord record : data.records()) {
                RiftLocation location = world.location(record.id());
                // The directory rejects stale copies after an unloaded portal was moved or removed.
                if (location == null || location.chunk() != pos || record.expired(RiftService.now(level))) {
                    data.remove(record.id());
                    chunk.markUnsaved();
                } else {
                    RiftRecord cached = world.get(record.id());
                    if (cached == null || cached.revision() != record.revision()) changed = true;
                    restore(level, record);
                    state.dirty.add(record.id());
                }
            }
            state.dirty.addAll(world.runtime().inChunk(pos));
        }
        Set<UUID> dirty = Set.copyOf(state.dirty);
        state.dirty.clear();
        for (UUID id : dirty) {
            RiftLocation location = world.location(id);
            if (location == null) continue;
            if (active(state, location)) {
                if (world.get(id) == null) {
                    read(level, id);
                    changed = true;
                }
            } else if (world.get(id) != null) {
                world.evict(id);
                RiftTravelService.cancel(level, id);
                changed = true;
            }
        }
        if (changed) RiftTracking.changed(level, null);
    }

    private static boolean active(State state, RiftLocation location) {
        if (state.loaded.containsKey(location.chunk())) return true;
        AABB bounds = location.bounds().inflate(0.5);
        for (int x = (int) Math.floor(bounds.minX / 16); x <= (int) Math.floor(bounds.maxX / 16); x++)
            for (int z = (int) Math.floor(bounds.minZ / 16); z <= (int) Math.floor(bounds.maxZ / 16); z++)
                if (state.loaded.containsKey(ChunkPos.pack(x, z))) return true;
        return false;
    }

    static void release(ServerLevel level) {
        STATES.remove(level);
    }

    static void clear() {
        STATES.clear();
    }

    private static final class State {
        final Map<Long, LevelChunk> loaded = new HashMap<>();
        final Map<Long, LevelChunk> pending = new LinkedHashMap<>();
        final Set<UUID> dirty = new HashSet<>();
        @Nullable RiftWorldAttachment initialized;
    }
}
