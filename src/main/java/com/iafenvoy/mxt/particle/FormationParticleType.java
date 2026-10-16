package com.iafenvoy.mxt.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

public final class FormationParticleType extends ParticleType<FormationParticleOptions> {
    public FormationParticleType() {
        // The limiter has to be off for this type: the figure is laid out around a camera that may be far outside the
        // 32 blocks the client-side check measures, and a formation's radius is exactly the number that must not clip.
        super(true);
    }

    @Override
    public @NonNull MapCodec<FormationParticleOptions> codec() {
        return FormationParticleOptions.CODEC;
    }

    @Override
    public @NonNull StreamCodec<? super RegistryFriendlyByteBuf, FormationParticleOptions> streamCodec() {
        return FormationParticleOptions.STREAM_CODEC;
    }
}
