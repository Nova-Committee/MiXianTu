package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RiftChunkAttachment {
    public static final MapCodec<RiftChunkAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RiftRecord.CODEC.listOf().lenientOptionalFieldOf("portals", List.of()).forGetter(RiftChunkAttachment::records)
    ).apply(i, RiftChunkAttachment::new));
    private final Map<UUID, RiftRecord> records = new LinkedHashMap<>();

    public RiftChunkAttachment() { }

    private RiftChunkAttachment(List<RiftRecord> records) {
        for (RiftRecord record : records) this.records.put(record.id(), record);
    }

    public List<RiftRecord> records() { return List.copyOf(this.records.values()); }
    public @Nullable RiftRecord get(UUID id) { return this.records.get(id); }
    public void put(RiftRecord record) { this.records.put(record.id(), record); }
    public void remove(UUID id) { this.records.remove(id); }
}
