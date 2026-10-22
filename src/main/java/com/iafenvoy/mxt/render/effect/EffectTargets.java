package com.iafenvoy.mxt.render.effect;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jspecify.annotations.Nullable;

public final class EffectTargets implements AutoCloseable {
    private final EffectTargetPool<Description, Texture> pool = new EffectTargetPool<>(64L * 1024 * 1024, 32,
            exception -> MiXianTu.LOGGER.error("Failed to close an effect texture", exception));

    public EffectTargetPool<Description, Texture>.@Nullable Lease acquire(Description description) {
        RenderSystem.assertOnRenderThread();
        int maxSize = RenderSystem.getDevice().getMaxTextureSize();
        if (description.width <= 0 || description.height <= 0 || description.width > maxSize || description.height > maxSize)
            return null;
        long bytes = (long) description.width * description.height * description.format.pixelSize();
        return this.pool.acquire(description, bytes, () -> new Texture(description));
    }

    public static @Nullable RenderTarget particles(LevelRenderer renderer) {
        RenderTarget particles = renderer.getParticlesTarget();
        RenderTarget target = particles == null ? Minecraft.getInstance().getMainRenderTarget() : particles;
        return valid(target) ? target : null;
    }

    public static boolean valid(RenderTarget target) {
        GpuTextureView color = target.getColorTextureView();
        GpuTextureView depth = target.getDepthTextureView();
        return target.width > 0 && target.height > 0 && color != null && !color.isClosed()
                && color.texture().getWidth(0) == target.width && color.texture().getHeight(0) == target.height
                && (depth == null || !depth.isClosed() && depth.texture().getWidth(0) == target.width
                && depth.texture().getHeight(0) == target.height);
    }

    public void endFrame() {
        this.pool.endFrame();
    }

    @Override
    public void close() {
        this.pool.close();
    }

    public record Description(int width, int height, TextureFormat format, int usage) {
        public static Description color(int width, int height) {
            return new Description(width, height, TextureFormat.RGBA8,
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST);
        }
    }

    public static final class Texture implements AutoCloseable {
        private final GpuTexture texture;
        private final GpuTextureView view;

        private Texture(Description description) {
            this.texture = RenderSystem.getDevice().createTexture(() -> "MXT effect texture", description.usage,
                    description.format, description.width, description.height, 1, 1);
            try {
                this.view = RenderSystem.getDevice().createTextureView(this.texture);
            } catch (RuntimeException | Error failure) {
                this.texture.close();
                throw failure;
            }
        }

        public GpuTexture texture() {
            return this.texture;
        }

        public GpuTextureView view() {
            return this.view;
        }

        @Override
        public void close() {
            try {
                this.view.close();
            } finally {
                this.texture.close();
            }
        }
    }
}
