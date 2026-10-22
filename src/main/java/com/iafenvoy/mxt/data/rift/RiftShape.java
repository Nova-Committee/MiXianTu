package com.iafenvoy.mxt.data.rift;

import com.iafenvoy.mxt.runtime.rift.RiftGeometry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record RiftShape(List<RiftVertex> vertices) {
    public static final int MAX_VERTICES = 128;
    public static final double MAX_SIZE = 32;
    public static final RiftShape DEFAULT = new RiftShape(List.of(new RiftVertex(-1, 0), new RiftVertex(1, 0),
            new RiftVertex(0.8, 2), new RiftVertex(0, 2.5), new RiftVertex(-0.8, 2)));
    public static final Codec<RiftShape> CODEC = RecordCodecBuilder.<RiftShape>create(i -> i.group(
            RiftVertex.CODEC.listOf(3, MAX_VERTICES).fieldOf("vertices").forGetter(RiftShape::vertices)
    ).apply(i, RiftShape::new)).validate(shape -> RiftGeometry.triangulate(shape).map(_ -> shape));

    public RiftShape {
        vertices = List.copyOf(vertices);
    }
}
