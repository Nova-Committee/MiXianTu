package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.particle.FormationParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * The carrier of one order. It is not a picture of anything: on its first tick it hands the order to
 * {@link FormationFigures} and takes itself out, and that figure keeps being drawn by this side's own clock until the
 * orders for it stop coming.
 */
public final class FormationParticle extends NoRenderParticle {
    private final FormationParticleOptions options;

    private FormationParticle(ClientLevel level, double x, double y, double z, FormationParticleOptions options) {
        super(level, x, y, z);
        this.options = options;
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        this.remove();
        FormationFigures.accept(this.level, new Vec3(this.x, this.y, this.z), this.options);
    }

    public static final class Provider implements ParticleProvider<FormationParticleOptions> {
        @Override
        public Particle createParticle(FormationParticleOptions options, @NonNull ClientLevel level, double x, double y,
                                       double z, double velocityX, double velocityY, double velocityZ, @NonNull RandomSource random) {
            return new FormationParticle(level, x, y, z, options);
        }
    }
}
