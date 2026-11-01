package com.iafenvoy.mxt.runtime.rift;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.iafenvoy.saturn.util.dispatcher.TickDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RiftRuntime implements AutoCloseable {
    public final TickDispatcher<UUID> expiry = new TickDispatcher<>(0, 0);
    private final Map<UUID, RiftGeometry.Mesh> meshes = new HashMap<>();
    private final SetMultimap<Long, UUID> sections = LinkedHashMultimap.create(), chunks = LinkedHashMultimap.create();
    private final SetMultimap<UUID, Long> occupied = LinkedHashMultimap.create();

    public RiftRuntime(List<RiftRecord> records) {
        for (RiftRecord record : records)
            RiftGeometry.triangulate(record.data().shape()).result().ifPresent(triangles -> this.put(record, RiftGeometry.mesh(record.data().shape(), record.data().placement(), triangles)));
    }

    public @Nullable RiftGeometry.Mesh mesh(UUID id) {
        return this.meshes.get(id);
    }

    public boolean isEmpty() {
        return this.meshes.isEmpty();
    }

    public boolean fits(UUID replacing, RiftGeometry.Mesh mesh, int sectionLimit) {
        for (long key : sections(mesh.bounds().inflate(0.5))) {
            Set<UUID> ids = this.sections.get(key);
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
        this.occupied.putAll(id, keys);
        for (long key : keys) {
            this.sections.put(key, id);
            this.chunks.put(ChunkPos.pack(SectionPos.x(key), SectionPos.z(key)), id);
        }
    }

    public void evict(UUID id) {
        this.meshes.remove(id);
    }

    public Set<UUID> inChunk(long chunk) {
        return this.chunks.get(chunk);
    }

    public void remove(UUID id) {
        this.meshes.remove(id);
        for (long key : this.occupied.removeAll(id)) {
            this.sections.remove(key, id);
            this.chunks.remove(ChunkPos.pack(SectionPos.x(key), SectionPos.z(key)), id);
        }
    }

    public Set<UUID> query(AABB bounds) {
        Set<UUID> result = new HashSet<>();
        for (long key : sections(bounds)) result.addAll(this.sections.get(key));
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
