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

public record SpiritWispParticleOptions(int color, float size) implements ParticleOptions {
    public static final MapCodec<SpiritWispParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(SpiritWispParticleOptions::color),
            Codec.floatRange(0.001F, 16.0F).fieldOf("size").forGetter(SpiritWispParticleOptions::size)
    ).apply(i, SpiritWispParticleOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritWispParticleOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SpiritWispParticleOptions::color,
            ByteBufCodecs.FLOAT, SpiritWispParticleOptions::size,
            SpiritWispParticleOptions::new
    );

    public SpiritWispParticleOptions {
        // The colour is authored through vanilla's string colour codec, which carries an alpha byte; the wisp is RGB
        // and draws with its own fade, so the alpha is dropped here instead of at every caller.
        color &= 0xFFFFFF;
        if (!Float.isFinite(size) || size <= 0.0F)
            throw new IllegalArgumentException("Particle size must be finite and positive");
    }

    @Override
    public @NonNull ParticleType<?> getType() {
        return MxtParticleTypes.SPIRIT_WISP.get();
    }
}
