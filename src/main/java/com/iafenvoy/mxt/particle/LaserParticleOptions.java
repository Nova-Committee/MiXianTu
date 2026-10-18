package com.iafenvoy.mxt.particle;

import com.iafenvoy.mxt.registry.MxtParticleTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

public record LaserParticleOptions(int color, float x, float z, float yMin, float yMax, float width,
                                   FlowDirection flowDirection, float flowSpeed,
                                   int lifetime) implements ParticleOptions {
    public static final int DEFAULT_COLOR = 0xFFFFE8A8;
    public static final float DEFAULT_WIDTH = 0.18F;
    public static final float DEFAULT_FLOW_SPEED = 1.0F;
    public static final int DEFAULT_LIFETIME = 40;

    public static final MapCodec<LaserParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("color", DEFAULT_COLOR).forGetter(LaserParticleOptions::color),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("x", 0.0F).forGetter(LaserParticleOptions::x),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("z", 0.0F).forGetter(LaserParticleOptions::z),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("y_min", 0.0F).forGetter(LaserParticleOptions::yMin),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("y_max", 8.0F).forGetter(LaserParticleOptions::yMax),
            Codec.floatRange(0.001F, 64.0F).optionalFieldOf("width", DEFAULT_WIDTH).forGetter(LaserParticleOptions::width),
            FlowDirection.CODEC.optionalFieldOf("flow_direction", FlowDirection.UP).forGetter(LaserParticleOptions::flowDirection),
            Codec.floatRange(0.0F, 100.0F).optionalFieldOf("flow_speed", DEFAULT_FLOW_SPEED).forGetter(LaserParticleOptions::flowSpeed),
            Codec.intRange(1, 1200).optionalFieldOf("lifetime", DEFAULT_LIFETIME).forGetter(LaserParticleOptions::lifetime)
    ).apply(i, LaserParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaserParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LaserParticleOptions::color,
            ByteBufCodecs.FLOAT, LaserParticleOptions::x,
            ByteBufCodecs.FLOAT, LaserParticleOptions::z,
            ByteBufCodecs.FLOAT, LaserParticleOptions::yMin,
            ByteBufCodecs.FLOAT, LaserParticleOptions::yMax,
            ByteBufCodecs.FLOAT, LaserParticleOptions::width,
            ByteBufCodecs.BOOL, options -> options.flowDirection() == FlowDirection.UP,
            ByteBufCodecs.FLOAT, LaserParticleOptions::flowSpeed,
            ByteBufCodecs.VAR_INT, LaserParticleOptions::lifetime,
            (color, x, z, yMin, yMax, width, flowUp, flowSpeed, lifetime) ->
                    new LaserParticleOptions(color, x, z, yMin, yMax, width,
                            flowUp ? FlowDirection.UP : FlowDirection.DOWN, flowSpeed, lifetime));

    public LaserParticleOptions {
        if (!Float.isFinite(x) || !Float.isFinite(z) || !Float.isFinite(yMin) || !Float.isFinite(yMax)
                || !Float.isFinite(width) || !Float.isFinite(flowSpeed))
            throw new IllegalArgumentException("Laser particle coordinates and dimensions must be finite");
        if (yMax <= yMin) throw new IllegalArgumentException("Laser particle y_max must be greater than y_min");
        if (width <= 0.0F) throw new IllegalArgumentException("Laser particle width must be positive");
        if (flowSpeed < 0.0F) throw new IllegalArgumentException("Laser particle flow speed cannot be negative");
        if (lifetime <= 0) throw new IllegalArgumentException("Laser particle lifetime must be positive");
    }

    @Override
    public @NonNull ParticleType<?> getType() {
        return MxtParticleTypes.LASER.get();
    }

    public enum FlowDirection implements StringRepresentable {
        UP("up", 1.0F),
        DOWN("down", -1.0F);

        public static final Codec<FlowDirection> CODEC = StringRepresentable.fromEnum(FlowDirection::values);
        private final String name;
        private final float sign;

        FlowDirection(String name, float sign) {
            this.name = name;
            this.sign = sign;
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name;
        }

        public float sign() {
            return this.sign;
        }
    }
}
