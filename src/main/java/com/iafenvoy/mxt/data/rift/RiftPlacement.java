package com.iafenvoy.mxt.data.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record RiftPlacement(Vec3 origin, Vec3 normal, Vec3 tangent) {
    public static final Codec<RiftPlacement> CODEC = RecordCodecBuilder.<RiftPlacement>create(i -> i.group(
            Vec3.CODEC.fieldOf("origin").forGetter(RiftPlacement::origin),
            Vec3.CODEC.fieldOf("normal").forGetter(RiftPlacement::normal),
            Vec3.CODEC.fieldOf("tangent").forGetter(RiftPlacement::tangent)
    ).apply(i, RiftPlacement::new)).flatXmap(RiftPlacement::normalized, RiftPlacement::normalized);

    public DataResult<RiftPlacement> normalized() {
        if (!finite(this.origin) || !finite(this.normal) || !finite(this.tangent)
                || Math.abs(this.origin.x) > 30_000_000 || Math.abs(this.origin.z) > 30_000_000
                || Math.abs(this.origin.y) > 30_000_000 || this.normal.lengthSqr() < 1e-12
                || !Double.isFinite(this.normal.lengthSqr()) || !Double.isFinite(this.tangent.lengthSqr()))
            return DataResult.error(() -> "Invalid rift placement");
        Vec3 normal = this.normal.normalize();
        Vec3 tangent = this.tangent.subtract(normal.scale(normal.dot(this.tangent)));
        if (tangent.lengthSqr() < 1e-12) return DataResult.error(() -> "Rift basis is degenerate");
        return DataResult.success(new RiftPlacement(this.origin, normal, tangent.normalize()));
    }

    public Vec3 bitangent() {
        return this.normal.cross(this.tangent);
    }

    public Vec3 world(RiftVertex point) {
        return this.origin.add(this.tangent.scale(point.u())).add(this.bitangent().scale(point.v()));
    }

    public RiftVertex local(Vec3 point) {
        Vec3 delta = point.subtract(this.origin);
        return new RiftVertex(delta.dot(this.tangent), delta.dot(this.bitangent()));
    }

    public static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
