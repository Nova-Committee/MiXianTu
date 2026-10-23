package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.util.dispatcher.TickDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.*;

public final class RiftRuntime implements AutoCloseable {
    public final TickDispatcher<UUID> expiry = new TickDispatcher<>(0, 0);
    private final Map<UUID, RiftGeometry.Mesh> meshes = new HashMap<>();
    private final Map<Long, Set<UUID>> sections = new HashMap<>();
    private final Map<UUID, Set<Long>> occupied = new HashMap<>();
    private final Map<Long, Set<UUID>> chunks = new HashMap<>();

    public RiftRuntime(List<RiftRecord> records) {
        for (RiftRecord record : records) {
            RiftGeometry.triangulate(record.data().shape()).result().ifPresent(triangles -> this.put(record,
                    RiftGeometry.mesh(record.data().shape(), record.data().placement(), triangles)));
        }
    }

    public RiftGeometry.Mesh mesh(UUID id) {
        return this.meshes.get(id);
    }

    public boolean empty() {
        return this.meshes.isEmpty();
    }

    public boolean fits(UUID replacing, RiftGeometry.Mesh mesh, int sectionLimit) {
        for (long key : sections(mesh.bounds().inflate(0.5))) {
            Set<UUID> ids = this.sections.getOrDefault(key, Set.of());
            if (ids.size() - (replacing != null && ids.contains(replacing) ? 1 : 0) >= sectionLimit) return false;
        }
        return true;
    }

    public void put(RiftRecord record, RiftGeometry.Mesh mesh) {
        this.index(record.id(), mesh.bounds());
        this.meshes.put(record.id(), mesh);
    }

    public void index(UUID id, AABB bounds) {
        this.remove(id);
        Set<Long> keys = sections(bounds.inflate(0.5));
        this.occupied.put(id, keys);
        for (long key : keys) {
            this.sections.computeIfAbsent(key, _ -> new HashSet<>()).add(id);
            this.chunks.computeIfAbsent(ChunkPos.pack(SectionPos.x(key), SectionPos.z(key)), _ -> new HashSet<>()).add(id);
        }
    }

    public void evict(UUID id) {
        this.meshes.remove(id);
    }

    public Set<UUID> inChunk(long chunk) {
        return Set.copyOf(this.chunks.getOrDefault(chunk, Set.of()));
    }

    public void remove(UUID id) {
        this.meshes.remove(id);
        Set<Long> keys = this.occupied.remove(id);
        if (keys != null) for (long key : keys) {
            Set<UUID> ids = this.sections.get(key);
            ids.remove(id);
            if (ids.isEmpty()) this.sections.remove(key);
            long column = ChunkPos.pack(SectionPos.x(key), SectionPos.z(key));
            Set<UUID> columnIds = this.chunks.get(column);
            if (columnIds != null) {
                columnIds.remove(id);
                if (columnIds.isEmpty()) this.chunks.remove(column);
            }
        }
    }

    public Set<UUID> query(AABB bounds) {
        Set<UUID> result = new HashSet<>();
        for (long key : sections(bounds)) result.addAll(this.sections.getOrDefault(key, Set.of()));
        return result;
    }

    private static Set<Long> sections(AABB box) {
        Set<Long> result = new HashSet<>();
        for (int x = SectionPos.blockToSectionCoord(box.minX); x <= SectionPos.blockToSectionCoord(box.maxX); x++)
            for (int y = SectionPos.blockToSectionCoord(box.minY); y <= SectionPos.blockToSectionCoord(box.maxY); y++)
                for (int z = SectionPos.blockToSectionCoord(box.minZ); z <= SectionPos.blockToSectionCoord(box.maxZ); z++)
                    result.add(SectionPos.asLong(x, y, z));
        return result;
    }

    @Override
    public void close() {
        this.expiry.clear();
        this.meshes.clear();
        this.sections.clear();
        this.occupied.clear();
        this.chunks.clear();
    }
}
