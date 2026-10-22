package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import com.iafenvoy.mxt.runtime.rift.RiftRuntime;
import com.iafenvoy.mxt.runtime.rift.RiftLocation;
import com.iafenvoy.mxt.runtime.rift.RiftGeometry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RiftWorldAttachment {
    public static final MapCodec<RiftWorldAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RiftLocation.CODEC.listOf().lenientOptionalFieldOf("locations", List.of()).forGetter(RiftWorldAttachment::locations),
            RiftRecord.CODEC.listOf().lenientOptionalFieldOf("portals", List.of()).forGetter(RiftWorldAttachment::legacy)
    ).apply(i, RiftWorldAttachment::new));
    private final Map<UUID, RiftLocation> locations = new LinkedHashMap<>();
    private final List<RiftRecord> legacy = new ArrayList<>();
    // Full records and meshes are only retained while their owner or covered chunks are loaded.
    private final Map<UUID, RiftRecord> records = new LinkedHashMap<>();
    private @Nullable RiftRuntime runtime;

    public RiftWorldAttachment() { }

    private RiftWorldAttachment(List<RiftLocation> locations, List<RiftRecord> legacy) {
        for (RiftLocation location : locations) this.locations.put(location.id(), location);
        this.legacy.addAll(legacy);
    }

    public List<RiftLocation> locations() { return List.copyOf(this.locations.values()); }
    public @Nullable RiftLocation location(UUID id) { return this.locations.get(id); }
    public List<RiftRecord> legacy() { return List.copyOf(this.legacy); }
    public void migrated(UUID id) { this.legacy.removeIf(record -> record.id().equals(id)); }
    // Active cache only; administrative enumeration goes through RiftService.records.
    public List<RiftRecord> records() { return List.copyOf(this.records.values()); }
    public @Nullable RiftRecord get(UUID id) { return this.records.get(id); }
    public void put(RiftRecord record, RiftGeometry.Mesh mesh) {
        this.locations.put(record.id(), RiftLocation.of(record, mesh));
        this.records.put(record.id(), record);
        this.runtime().put(record, mesh);
    }
    public void evict(UUID id) {
        this.records.remove(id);
        this.runtime().evict(id);
    }
    public void remove(UUID id) {
        this.locations.remove(id);
        this.records.remove(id);
        this.migrated(id);
        this.runtime().remove(id);
    }

    public RiftRuntime runtime() {
        if (this.runtime == null) {
            this.runtime = new RiftRuntime(List.of());
            for (RiftLocation location : this.locations.values()) this.runtime.index(location.id(), location.bounds());
            for (RiftRecord record : this.records.values()) RiftGeometry.triangulate(record.data().shape()).result()
                    .ifPresent(triangles -> this.runtime.put(record, RiftGeometry.mesh(record.data().shape(), record.data().placement(), triangles)));
        }
        return this.runtime;
    }

    public void release() {
        if (this.runtime != null) this.runtime.close();
        this.runtime = null;
        this.records.clear();
    }
}
