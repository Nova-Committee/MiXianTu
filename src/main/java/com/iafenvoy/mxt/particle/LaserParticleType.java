package com.iafenvoy.mxt.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

public final class LaserParticleType extends ParticleType<LaserParticleOptions> {
    public LaserParticleType() {
        super(false);
    }

    @Override
    public @NonNull MapCodec<LaserParticleOptions> codec() {
        return LaserParticleOptions.CODEC;
    }

    @Override
    public @NonNull StreamCodec<? super RegistryFriendlyByteBuf, LaserParticleOptions> streamCodec() {
        return LaserParticleOptions.STREAM_CODEC;
    }
}
