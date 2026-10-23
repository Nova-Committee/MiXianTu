package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.RiftTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

public record RiftRecord(UUID id, long revision, RiftTemplate data, long expiresAt, Optional<UUID> owner,
                         Optional<Identifier> boundDimension, int boundIndex, Optional<UUID> boundInstance) {
    public static final Codec<RiftRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RiftRecord::id),
            Codec.LONG.fieldOf("revision").forGetter(RiftRecord::revision),
            RiftTemplate.CODEC.fieldOf("data").forGetter(RiftRecord::data),
            Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(RiftRecord::expiresAt),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(RiftRecord::owner),
            Identifier.CODEC.optionalFieldOf("bound_dimension").forGetter(RiftRecord::boundDimension),
            Codec.INT.optionalFieldOf("bound_index", -1).forGetter(RiftRecord::boundIndex),
            UUIDUtil.CODEC.optionalFieldOf("bound_instance").forGetter(RiftRecord::boundInstance)
    ).apply(i, RiftRecord::new));

    public boolean expired(long now) {
        return this.expiresAt >= 0 && now >= this.expiresAt;
    }

    public RiftRecord edit(RiftTemplate data, long expiresAt, boolean preserveBinding) {
        return new RiftRecord(this.id, this.revision + 1, data, expiresAt, this.owner,
                preserveBinding ? this.boundDimension : Optional.empty(), preserveBinding ? this.boundIndex : -1, preserveBinding ? this.boundInstance : Optional.empty());
    }

    public RiftRecord bind(Identifier dimension, int index, UUID instanceId) {
        return new RiftRecord(this.id, this.revision + 1, this.data, this.expiresAt, this.owner, Optional.of(dimension), index, Optional.of(instanceId));
    }
}
