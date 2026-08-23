package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.particle.SpiritWispParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * A small full-bright, softly fading mote used to form a continuous spirit-burst trail.
 */
public final class SpiritWispParticle extends SingleQuadParticle {
    private static final int TRAIL_LIFETIME = 12;
    private static final float TRAIL_ALPHA = 0.30F;
    private final float initialSize;

    private SpiritWispParticle(ClientLevel level, double x, double y, double z, double velocityX, double velocityY,
                               double velocityZ, SpiritWispParticleOptions options, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.setParticleSpeed(velocityX, velocityY, velocityZ);
        this.setColor((options.color() >> 16 & 0xFF) / 255.0F, (options.color() >> 8 & 0xFF) / 255.0F, (options.color() & 0xFF) / 255.0F);
        this.setAlpha(TRAIL_ALPHA);
        this.initialSize = options.size();
        this.quadSize = this.initialSize;
        this.lifetime = TRAIL_LIFETIME;
        this.hasPhysics = false;
    }

    @Override
    public float getQuadSize(float partialTickTime) {
        float progress = Math.clamp((this.age + partialTickTime) / this.lifetime, 0.0F, 1.0F);
        return this.initialSize * (1.0F - progress * 0.45F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float fade = 1.0F - (float) this.age / this.lifetime;
        this.setAlpha(TRAIL_ALPHA * fade * fade);
    }

    @Override
    public int getLightCoords(float partialTickTime) {
        return 0xF000F0;
    }

    @Override
    protected @NonNull Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SpiritWispParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SpiritWispParticleOptions options, @NonNull ClientLevel level, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, @NonNull RandomSource random) {
            return new SpiritWispParticle(level, x, y, z, velocityX, velocityY, velocityZ, options, this.sprites.get(random));
        }
    }
}
