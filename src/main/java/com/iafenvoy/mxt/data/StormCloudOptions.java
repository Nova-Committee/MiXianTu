package com.iafenvoy.mxt.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.AABB;

public record StormCloudOptions(int cloudColor, int lightningColor, float radius, float thickness, int lifetime) {
    public static final float MIN_RADIUS = 0.5F, MAX_RADIUS = 256.0F;
    public static final Codec<Integer> LIFETIME_CODEC = Codec.intRange(1, 1200);
    public static final Codec<Float> RADIUS_CODEC = Codec.floatRange(MIN_RADIUS, MAX_RADIUS);
    public static final StormCloudOptions DEFAULT = Appearance.DEFAULT.options(16.0F, 160);
    public static final Codec<StormCloudOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Appearance.CODEC.forGetter(o -> new Appearance(o.cloudColor, o.lightningColor, o.thickness)),
            RADIUS_CODEC.optionalFieldOf("radius", DEFAULT.radius).forGetter(StormCloudOptions::radius),
            LIFETIME_CODEC.optionalFieldOf("lifetime", DEFAULT.lifetime).forGetter(StormCloudOptions::lifetime)
    ).apply(i, Appearance::options));

    public static final StreamCodec<RegistryFriendlyByteBuf, StormCloudOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, StormCloudOptions::cloudColor,
            ByteBufCodecs.INT, StormCloudOptions::lightningColor,
            ByteBufCodecs.FLOAT, StormCloudOptions::radius,
            ByteBufCodecs.FLOAT, StormCloudOptions::thickness,
            ByteBufCodecs.VAR_INT, StormCloudOptions::lifetime,
            StormCloudOptions::new);

    public StormCloudOptions {
        if (!(radius >= MIN_RADIUS && radius <= MAX_RADIUS && thickness >= 0.25F && thickness <= 32.0F)
                || lifetime < 1 || lifetime > 1200)
            throw new IllegalArgumentException("Storm cloud radius, thickness, or lifetime out of range");
    }

    public AABB bounds(double x, double y, double z) {
        double halfHeight = this.thickness * 0.5;
        double below = halfHeight + ((this.lightningColor >>> 24) == 0 ? 0.0 : this.radius * 0.5);
        return new AABB(x - this.radius, y - below, z - this.radius,
                x + this.radius, y + halfHeight, z + this.radius);
    }

    public record Appearance(int cloudColor, int lightningColor, float thickness) {
        public static final Appearance DEFAULT = new Appearance(0xFF584D82, 0xFFC2CDFF, 6.0F);
        public static final MapCodec<Appearance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("cloud_color", DEFAULT.cloudColor).forGetter(Appearance::cloudColor),
                ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("lightning_color", DEFAULT.lightningColor).forGetter(Appearance::lightningColor),
                Codec.floatRange(0.25F, 32.0F).optionalFieldOf("thickness", DEFAULT.thickness).forGetter(Appearance::thickness)
        ).apply(i, Appearance::new));

        public StormCloudOptions options(float radius, int lifetime) {
            return new StormCloudOptions(this.cloudColor, this.lightningColor, radius, this.thickness, lifetime);
        }
    }
}
