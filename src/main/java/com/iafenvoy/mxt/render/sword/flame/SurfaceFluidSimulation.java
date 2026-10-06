package com.iafenvoy.mxt.render.sword.flame;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.OptionalInt;

public final class SurfaceFluidSimulation implements AutoCloseable {
    public static final int TILE_SIZE = 64;
    public static final int ATLAS_COLUMNS = 32;
    public static final int CAPACITY = ATLAS_COLUMNS * ATLAS_COLUMNS;
    private final GpuTexture[] textures = new GpuTexture[2];
    private final GpuTextureView[] views = new GpuTextureView[2];
    private int readIndex;

    public SurfaceFluidSimulation() {
        int size = TILE_SIZE * ATLAS_COLUMNS;
        for (int i = 0; i < 2; i++) {
            this.textures[i] = RenderSystem.getDevice().createTexture("Sword surface fluid " + i,
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    TextureFormat.RGBA8, size, size, 1, 1);
            this.views[i] = RenderSystem.getDevice().createTextureView(this.textures[i]);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.textures[i], 0);
        }
    }

    public void update(CommandEncoder encoder, ParticleSampler sampler, GpuBufferSlice frame,
                       GpuBufferSlice[] batches, int[] counts, int batchCount) {
        try (RenderPass pass = encoder.createRenderPass(() -> "Sword surface simulation", this.views[1 - this.readIndex], OptionalInt.empty())) {
            pass.setPipeline(FlamePipelines.FLUID);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FlameFrame", frame);
            pass.bindTexture("FluidField", this.views[this.readIndex], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, sampler.tile());
            pass.setIndexBuffer(sampler.indices(), sampler.indexType());
            for (int i = 0; i < batchCount; i++) {
                pass.setUniform("FlameItems", batches[i]);
                pass.drawIndexed(0, 0, 6, counts[i]);
            }
        }
        this.readIndex = 1 - this.readIndex;
    }

    public GpuTextureView field() {
        return this.views[this.readIndex];
    }

    @Override
    public void close() {
        for (GpuTextureView view : this.views) view.close();
        for (GpuTexture texture : this.textures) texture.close();
    }
}
