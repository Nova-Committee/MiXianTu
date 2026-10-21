package com.iafenvoy.mxt.render.effect;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;

// One opaque-scene snapshot, borrowed by consumers of the same world view only.
public final class SceneDepth implements AutoCloseable {
    private final EffectTargetPool<EffectTargets.Description, EffectTargets.Texture>.Lease lease;
    private final GpuTexture source;
    private final GpuBufferSlice projection;
    private final int width;
    private final int height;

    private SceneDepth(EffectTargetPool<EffectTargets.Description, EffectTargets.Texture>.Lease lease,
                       GpuTexture source, GpuBufferSlice projection, int width, int height) {
        this.lease = lease;
        this.source = source;
        this.projection = projection;
        this.width = width;
        this.height = height;
    }

    public static @Nullable SceneDepth capture(EffectTargets targets, RenderTarget source) {
        GpuTexture depth = source.getDepthTexture();
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        if (depth == null || projection == null || depth.isClosed() || depth.getFormat() != TextureFormat.DEPTH32
                || (depth.usage() & GpuTexture.USAGE_COPY_SRC) == 0 || depth.getDepthOrLayers() != 1
                || depth.getWidth(0) != source.width || depth.getHeight(0) != source.height) return null;
        EffectTargetPool<EffectTargets.Description, EffectTargets.Texture>.Lease lease = targets.acquire(new EffectTargets.Description(source.width, source.height, depth.getFormat(),
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING));
        if (lease == null) return null;
        try {
            EffectTargets.Texture texture = lease.get();
            if (texture == null) {
                lease.close();
                return null;
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(depth, texture.texture(),
                    0, 0, 0, 0, 0, source.width, source.height);
            return new SceneDepth(lease, depth, projection, source.width, source.height);
        } catch (RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    public boolean matches(RenderTarget main, RenderTarget target) {
        return this.lease.get() != null && !this.source.isClosed() && main.getDepthTexture() == this.source
                && this.projection == RenderSystem.getProjectionMatrixBuffer()
                && target.width == this.width && target.height == this.height && EffectTargets.valid(target);
    }

    public void bind(RenderPass pass) {
        EffectTargets.Texture texture = this.lease.get();
        if (texture != null) pass.bindTexture("SceneDepth", texture.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    @Override
    public void close() {
        this.lease.close();
    }
}
