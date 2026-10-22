package com.iafenvoy.mxt.data.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

public record RiftVertex(double u, double v) {
    public static final Codec<RiftVertex> CODEC = Codec.DOUBLE.listOf(2, 2).comapFlatMap(
            values -> Double.isFinite(values.getFirst()) && Double.isFinite(values.get(1))
                    ? DataResult.success(new RiftVertex(values.getFirst(), values.get(1)))
                    : DataResult.error(() -> "Rift vertices must be finite"),
            point -> List.of(point.u, point.v));

    public RiftVertex add(double u, double v) {
        return new RiftVertex(this.u + u, this.v + v);
    }
}
