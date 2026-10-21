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
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

public record LightBeamParticleOptions(int color, float x, float z, float yMin, float yMax, float width,
                                       FlowDirection flowDirection, float flowSpeed,
                                       int lifetime, boolean spread, float radius, float density,
                                       boolean bottomEffect, boolean topEffect, boolean filled, boolean glow) implements ParticleOptions {
    public static final int DEFAULT_COLOR = 0xFFFFE8A8;
    public static final float DEFAULT_WIDTH = 0.18F;
    public static final float DEFAULT_RADIUS = 0.5F;
    public static final float DEFAULT_DENSITY = 16.0F;
    public static final float DEFAULT_FLOW_SPEED = 1.0F;
    public static final int DEFAULT_LIFETIME = 40;

    public static final MapCodec<LightBeamParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("color", DEFAULT_COLOR).forGetter(LightBeamParticleOptions::color),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("x", 0.0F).forGetter(LightBeamParticleOptions::x),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("z", 0.0F).forGetter(LightBeamParticleOptions::z),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("y_min", 0.0F).forGetter(LightBeamParticleOptions::yMin),
            Codec.floatRange(-4096.0F, 4096.0F).optionalFieldOf("y_max", 8.0F).forGetter(LightBeamParticleOptions::yMax),
            Codec.floatRange(0.001F, 64.0F).optionalFieldOf("width", DEFAULT_WIDTH).forGetter(LightBeamParticleOptions::width),
            FlowDirection.CODEC.optionalFieldOf("flow_direction", FlowDirection.UP).forGetter(LightBeamParticleOptions::flowDirection),
            Codec.floatRange(0.0F, 100.0F).optionalFieldOf("flow_speed", DEFAULT_FLOW_SPEED).forGetter(LightBeamParticleOptions::flowSpeed),
            Codec.intRange(1, 1200).optionalFieldOf("lifetime", DEFAULT_LIFETIME).forGetter(LightBeamParticleOptions::lifetime),
            Codec.BOOL.optionalFieldOf("spread", false).forGetter(LightBeamParticleOptions::spread),
            Codec.floatRange(0.001F, 64.0F).optionalFieldOf("radius", DEFAULT_RADIUS).forGetter(LightBeamParticleOptions::radius),
            Codec.floatRange(0.001F, 1024.0F).optionalFieldOf("density", DEFAULT_DENSITY).forGetter(LightBeamParticleOptions::density),
            Codec.BOOL.optionalFieldOf("bottom_effect", false).forGetter(LightBeamParticleOptions::bottomEffect),
            Codec.BOOL.optionalFieldOf("top_effect", false).forGetter(LightBeamParticleOptions::topEffect),
            Codec.BOOL.optionalFieldOf("filled", true).forGetter(LightBeamParticleOptions::filled),
            Codec.BOOL.optionalFieldOf("glow", false).forGetter(LightBeamParticleOptions::glow)
    ).apply(i, LightBeamParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LightBeamParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, LightBeamParticleOptions::color,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::x,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::z,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::yMin,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::yMax,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::width,
            ByteBufCodecs.VAR_INT, options -> (options.flowDirection() == FlowDirection.UP ? 1 : 0)
                    | (options.bottomEffect() ? 2 : 0) | (options.topEffect() ? 4 : 0)
                    | (options.filled() ? 8 : 0) | (options.glow() ? 16 : 0),
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::flowSpeed,
            ByteBufCodecs.VAR_INT, LightBeamParticleOptions::lifetime,
            ByteBufCodecs.BOOL, LightBeamParticleOptions::spread,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::radius,
            ByteBufCodecs.FLOAT, LightBeamParticleOptions::density,
            (color, x, z, yMin, yMax, width, flags, flowSpeed, lifetime, spread, radius, density) ->
                    new LightBeamParticleOptions(color, x, z, yMin, yMax, width,
                            (flags & 1) != 0 ? FlowDirection.UP : FlowDirection.DOWN, flowSpeed, lifetime, spread, radius, density,
                            (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0));

    public LightBeamParticleOptions(int color, float x, float z, float yMin, float yMax, float width,
                                    FlowDirection flowDirection, float flowSpeed, int lifetime, boolean spread,
                                    float radius, float density, boolean bottomEffect, boolean topEffect) {
        this(color, x, z, yMin, yMax, width, flowDirection, flowSpeed, lifetime, spread, radius, density,
                bottomEffect, topEffect, true, false);
    }

    public LightBeamParticleOptions(int color, float x, float z, float yMin, float yMax, float width,
                                    FlowDirection flowDirection, float flowSpeed, int lifetime, boolean spread,
                                    float radius, float density) {
        this(color, x, z, yMin, yMax, width, flowDirection, flowSpeed, lifetime, spread, radius, density, false, false);
    }

    public LightBeamParticleOptions {
        if (!Float.isFinite(x) || !Float.isFinite(z) || !Float.isFinite(yMin) || !Float.isFinite(yMax)
                || !Float.isFinite(width) || !Float.isFinite(flowSpeed) || !Float.isFinite(radius) || !Float.isFinite(density))
            throw new IllegalArgumentException("Light beam particle coordinates and dimensions must be finite");
        if (yMax <= yMin) throw new IllegalArgumentException("Light beam particle y_max must be greater than y_min");
        if (width <= 0.0F) throw new IllegalArgumentException("Light beam particle width must be positive");
        if (radius <= 0.0F) throw new IllegalArgumentException("Light beam particle radius must be positive");
        if (density <= 0.0F) throw new IllegalArgumentException("Light beam particle density must be positive");
        if (flowSpeed < 0.0F) throw new IllegalArgumentException("Light beam particle flow speed cannot be negative");
        if (lifetime <= 0) throw new IllegalArgumentException("Light beam particle lifetime must be positive");
    }

    @Override
    public @NonNull ParticleType<?> getType() {
        return MxtParticleTypes.LIGHT_BEAM.get();
    }

    public float renderRadius() {
        return this.radius + this.strandHalfWidth() + (this.bottomEffect || this.topEffect ? this.terminalReach() : 0.0F);
    }

    public float terminalReach() {
        return Math.max(0.75F, Math.max(this.radius * 1.5F, this.width * 4.0F));
    }

    public float strandHalfWidth() {
        return this.width * 0.7F;
    }

    public AABB bounds(double x, double y, double z) {
        double radius = this.renderRadius();
        return new AABB(x + this.x - radius, y + this.yMin - (this.bottomEffect ? this.strandHalfWidth() : 0), z + this.z - radius,
                x + this.x + radius, y + this.yMax + (this.topEffect ? this.strandHalfWidth() : 0), z + this.z + radius);
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
