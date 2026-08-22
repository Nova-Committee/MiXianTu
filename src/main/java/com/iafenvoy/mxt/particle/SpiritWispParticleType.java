package com.iafenvoy.mxt.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

/** Particle type for the local, entity-tracked spirit-burst trail. */
public final class SpiritWispParticleType extends ParticleType<SpiritWispParticleOptions> {
    public SpiritWispParticleType() {
        super(false);
    }

    @Override
    public @NonNull MapCodec<SpiritWispParticleOptions> codec() {
        return SpiritWispParticleOptions.CODEC;
    }

    @Override
    public @NonNull StreamCodec<? super RegistryFriendlyByteBuf, SpiritWispParticleOptions> streamCodec() {
        return SpiritWispParticleOptions.STREAM_CODEC;
    }
}
