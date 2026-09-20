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
 * A rift's sparkle: the vanilla portal particle in the rift's own colour.
 *
 * <p>Everything that makes the portal particle what it is - the sprite, the size, the rise, the fading light -
 * is inherited unchanged; the only thing replaced is the colour, which the portal particle hard-codes as violet
 * and a rift takes from the door it came out of. Brightness is still varied per particle the same way, so a
 * cloud of them keeps the uneven shimmer instead of looking like a flat wash.
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
