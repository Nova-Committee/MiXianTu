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
import org.jspecify.annotations.NonNull;

/**
 * Client-side visual parameters for a tracked spirit-burst trail particle.
 */
public record SpiritWispParticleOptions(int color, float size) implements ParticleOptions {
    public static final MapCodec<SpiritWispParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(SpiritWispParticleOptions::color),
            Codec.floatRange(0.001F, 16.0F).fieldOf("size").forGetter(SpiritWispParticleOptions::size)
    ).apply(i, SpiritWispParticleOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritWispParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SpiritWispParticleOptions::color,
            ByteBufCodecs.FLOAT, SpiritWispParticleOptions::size,
            SpiritWispParticleOptions::new
    );

    public SpiritWispParticleOptions {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Particle color must be an RGB value");
        if (!Float.isFinite(size) || size <= 0.0F)
            throw new IllegalArgumentException("Particle size must be finite and positive");
    }

    @Override
    public @NonNull ParticleType<?> getType() {
        return MxtParticleTypes.SPIRIT_WISP.get();
    }
}
