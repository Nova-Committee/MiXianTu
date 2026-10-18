package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.particle.LaserParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

/**
 * A vertical laser is represented by stacked animated glow quads so it remains a normal networked particle.
 */
public final class LaserParticle extends SingleQuadParticle {
    private static final int MAX_SEGMENTS = 128;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final int FULL_BRIGHT = 0xF000F0;
    private final LaserParticleOptions options;
    private final float lower;
    private final float upper;

    private LaserParticle(ClientLevel level, double x, double y, double z, LaserParticleOptions options,
                          TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.options = options;
        this.lower = (float) (y + options.yMin());
        this.upper = (float) (y + options.yMax());
        this.setLifetime(options.lifetime());
        this.hasPhysics = false;
        this.setBoundingBox(new AABB(x + options.x() - options.width() * 3.0F, this.lower - options.width(),
                z + options.z() - options.width() * 3.0F, x + options.x() + options.width() * 3.0F,
                this.upper + options.width(), z + options.z() + options.width() * 3.0F));
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) this.remove();
    }

    @Override
    public void extract(@NonNull QuadParticleRenderState state, @NonNull Camera camera, float partialTick) {
        Vec3 cameraPosition = camera.position();
        float centerX = (float) (this.x + this.options.x() - cameraPosition.x());
        float centerZ = (float) (this.z + this.options.z() - cameraPosition.z());
        float range = this.upper - this.lower;
        int segmentCount = Math.clamp((int) Math.ceil(range / Math.max(this.options.width() * 0.70F, 0.01F)), 4, MAX_SEGMENTS);
        float step = range / segmentCount;
        float segmentSize = Math.max(this.options.width(), step * 1.15F);
        float time = this.age + partialTick;
        float direction = this.options.flowDirection().sign();
        Quaternionf rotation = new Quaternionf();
        SingleQuadParticle.FacingCameraMode.LOOKAT_Y.setRotation(rotation, camera, partialTick);
        for (int index = 0; index < segmentCount; index++) {
            float height = (index + 0.5F) / segmentCount;
            float phase = height * 8.0F * TWO_PI - time * this.options.flowSpeed() * direction * 0.12F;
            float wave = 0.5F + 0.5F * Mth.sin(phase);
            float edge = 0.72F + 0.28F * Mth.sin(height * (float) Math.PI);
            float y = this.lower + step * (index + 0.5F) - (float) cameraPosition.y();
            this.add(state, centerX, y, centerZ, rotation, segmentSize * 2.8F, edge * (0.035F + wave * 0.075F));
            this.add(state, centerX, y, centerZ, rotation, segmentSize * 1.75F, edge * (0.10F + wave * 0.16F));
            this.add(state, centerX, y, centerZ, rotation, segmentSize * 0.90F, edge * (0.52F + wave * 0.40F));
        }
    }

    private void add(QuadParticleRenderState state, float x, float y, float z, Quaternionf rotation,
                     float size, float alpha) {
        state.add(this.getLayer(), x, y, z, rotation.x, rotation.y, rotation.z, rotation.w, size,
                this.getU0(), this.getU1(), this.getV0(), this.getV1(), ARGB.multiplyAlpha(this.options.color(), alpha),
                FULL_BRIGHT);
    }

    @Override
    public int getLightCoords(float partialTickTime) {
        return FULL_BRIGHT;
    }

    @Override
    public @NonNull ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    @Override
    protected @NonNull Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<LaserParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(LaserParticleOptions options, @NonNull ClientLevel level, double x, double y,
                                       double z, double velocityX, double velocityY, double velocityZ,
                                       @NonNull RandomSource random) {
            return new LaserParticle(level, x, y, z, options, this.sprites.get(random));
        }
    }
}
