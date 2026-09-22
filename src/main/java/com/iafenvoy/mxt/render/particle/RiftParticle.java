package com.iafenvoy.mxt.render.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * The vanilla portal particle in a rift's own colour: sprite, size, rise and fading light are inherited, only the
 * hard-coded violet is replaced by the colour the rift carries.
 */
public final class RiftParticle extends PortalParticle {
    private RiftParticle(ClientLevel level, double x, double y, double z, double velocityX, double velocityY,
                         double velocityZ, ColorParticleOption options, TextureAtlasSprite sprite) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprite);
        float brightness = this.random.nextFloat() * 0.6F + 0.4F;
        this.setColor(options.getRed() * brightness, options.getGreen() * brightness, options.getBlue() * brightness);
    }

    public static final class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ColorParticleOption options, @NonNull ClientLevel level, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, @NonNull RandomSource random) {
            return new RiftParticle(level, x, y, z, velocityX, velocityY, velocityZ, options, this.sprites.get(random));
        }
    }
}
