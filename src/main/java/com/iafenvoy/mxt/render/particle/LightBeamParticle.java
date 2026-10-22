package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.particle.LightBeamParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

public final class LightBeamParticle extends Particle {
    final LightBeamParticleShape shape;
    final float phase;

    private LightBeamParticle(ClientLevel level, double x, double y, double z, LightBeamParticleOptions options,
                              float phase, long seed) {
        super(level, x, y, z);
        this.shape = new LightBeamParticleShape(options, x, y, z, seed);
        this.phase = phase;
        this.setLifetime(options.lifetime());
        this.hasPhysics = false;
        this.setBoundingBox(this.shape.bounds());
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) this.remove();
    }

    float renderAge(float partialTick) {
        return this.age + partialTick;
    }

    @Override
    public @NonNull ParticleRenderType getGroup() {
        return LightBeamParticleGroup.TYPE;
    }

    public static final class Provider implements ParticleProvider<LightBeamParticleOptions> {
        @Override
        public Particle createParticle(LightBeamParticleOptions options, @NonNull ClientLevel level, double x, double y,
                                       double z, double velocityX, double velocityY, double velocityZ,
                                       @NonNull RandomSource random) {
            return new LightBeamParticle(level, x, y, z, options, random.nextFloat() * 64.0F, random.nextLong());
        }
    }
}
