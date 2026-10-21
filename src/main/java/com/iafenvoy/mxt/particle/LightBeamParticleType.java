package com.iafenvoy.mxt.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

public final class LightBeamParticleType extends ParticleType<LightBeamParticleOptions> {
    public LightBeamParticleType() {
        // The origin can be far below the visible part of the column.
        super(true);
    }

    @Override
    public @NonNull MapCodec<LightBeamParticleOptions> codec() {
        return LightBeamParticleOptions.CODEC;
    }

    @Override
    public @NonNull StreamCodec<? super RegistryFriendlyByteBuf, LightBeamParticleOptions> streamCodec() {
        return LightBeamParticleOptions.STREAM_CODEC;
    }
}
