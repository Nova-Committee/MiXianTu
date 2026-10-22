package com.iafenvoy.mxt.data.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.stream.DoubleStream;

public record RiftAppearance(String style, int color, int edgeColor, double speed, double edgeWidth,
                             double glow, double particles, double textureScale, double textureRotation,
                             double textureU, double textureV) {
    public static final RiftAppearance DEFAULT = new RiftAppearance("rift", -1, -1, 1, 0.06, 0.2, 0.1, 1, 0, 0, 0);
    public static final Codec<RiftAppearance> CODEC = RecordCodecBuilder.<RiftAppearance>create(i -> i.group(
            Codec.STRING.optionalFieldOf("style", "rift").forGetter(RiftAppearance::style),
            Codec.INT.optionalFieldOf("color", -1).forGetter(RiftAppearance::color),
            Codec.INT.optionalFieldOf("edge_color", -1).forGetter(RiftAppearance::edgeColor),
            Codec.doubleRange(0, 10).optionalFieldOf("speed", 1D).forGetter(RiftAppearance::speed),
            Codec.doubleRange(0, 0.5).optionalFieldOf("edge_width", 0.06D).forGetter(RiftAppearance::edgeWidth),
            Codec.doubleRange(0, 1).optionalFieldOf("glow", 0.2D).forGetter(RiftAppearance::glow),
            Codec.doubleRange(0, 1).optionalFieldOf("particles", 0.1D).forGetter(RiftAppearance::particles),
            Codec.doubleRange(0.01, 100).optionalFieldOf("texture_scale", 1D).forGetter(RiftAppearance::textureScale),
            Codec.doubleRange(-360, 360).optionalFieldOf("texture_rotation", 0D).forGetter(RiftAppearance::textureRotation),
            Codec.doubleRange(-1024, 1024).optionalFieldOf("texture_u", 0D).forGetter(RiftAppearance::textureU),
            Codec.doubleRange(-1024, 1024).optionalFieldOf("texture_v", 0D).forGetter(RiftAppearance::textureV)
    ).apply(i, RiftAppearance::new)).validate(value -> DoubleStream.of(value.speed, value.edgeWidth,
            value.glow, value.particles, value.textureScale, value.textureRotation, value.textureU, value.textureV)
            .allMatch(Double::isFinite) ? DataResult.success(value) : DataResult.error(() -> "Non-finite rift appearance"))
            .validate(value -> value.style.equals("rift") || value.style.equals("portal")
            ? DataResult.success(value) : DataResult.error(() -> "Unknown rift style"));
}
