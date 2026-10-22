package com.iafenvoy.mxt.runtime.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

// The dimension directory contains no outline, destination, appearance or realm binding.
public record RiftLocation(UUID id, long chunk, long revision, Vec3 min, Vec3 max, long expiresAt, Optional<UUID> owner) {
    public static final Codec<RiftLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RiftLocation::id),
            Codec.LONG.fieldOf("chunk").forGetter(RiftLocation::chunk),
            Codec.LONG.fieldOf("revision").forGetter(RiftLocation::revision),
            Vec3.CODEC.fieldOf("min").forGetter(RiftLocation::min),
            Vec3.CODEC.fieldOf("max").forGetter(RiftLocation::max),
            Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(RiftLocation::expiresAt),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(RiftLocation::owner)
    ).apply(i, RiftLocation::new));

    public static RiftLocation of(RiftRecord record, RiftGeometry.Mesh mesh) {
        AABB bounds = mesh.bounds();
        return new RiftLocation(record.id(), ChunkPos.containing(BlockPos.containing(bounds.getCenter())).pack(),
                record.revision(), bounds.getMinPosition(), bounds.getMaxPosition(), record.expiresAt(), record.owner());
    }

    public AABB bounds() { return new AABB(this.min, this.max); }
    public boolean expired(long now) { return this.expiresAt >= 0 && now >= this.expiresAt; }
}
