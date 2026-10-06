package com.iafenvoy.mxt.render.sword.flame;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class FlameRenderer implements AutoCloseable {
    // 64 * 9 vec4 stays below the OpenGL 3.3 minimum uniform-block limit (16 KiB).
    private static final int BATCH_SIZE = 64;
    private static final int ITEM_BYTES = 9 * 16;
    private static final int FRAME_BYTES = 64 + 5 * 16;
    private static final int MAX_BATCHES = SurfaceFluidSimulation.CAPACITY / BATCH_SIZE + 3;
    private final int batchStride;
    private final int itemStart;
    private final MappableRingBuffer uniforms;
    private final ByteBuffer staging;
    private final GpuBufferSlice[] batches = new GpuBufferSlice[MAX_BATCHES];
    private final int[] counts = new int[MAX_BATCHES];
    private final int[] lods = new int[MAX_BATCHES];
    private final ParticleSampler sampler;
    private final SurfaceFluidSimulation fluid;
    private final Vector3f axis = new Vector3f();
    private GpuTexture depthCopy;
    private GpuTextureView depthView;
    private boolean invalidLogged;

    public FlameRenderer() {
        int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
        this.batchStride = align(BATCH_SIZE * ITEM_BYTES, alignment);
        this.itemStart = align(FRAME_BYTES, alignment);
        int bytes = this.itemStart + this.batchStride * MAX_BATCHES;
        this.staging = MemoryUtil.memCalloc(bytes);
        this.uniforms = new MappableRingBuffer(() -> "Sword flame records",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, bytes);
        this.sampler = new ParticleSampler();
        this.fluid = new SurfaceFluidSimulation();
    }

    public boolean render(List<List<BurningItemManager.Entry>> visible, CameraRenderState camera, float time, float delta) {
        GpuDevice device = RenderSystem.getDevice();
        if (!device.precompilePipeline(FlamePipelines.FLUID).isValid()
                || !device.precompilePipeline(FlamePipelines.SURFACE).isValid()
                || !device.precompilePipeline(FlamePipelines.PARTICLES).isValid()) {
            if (!this.invalidLogged)
                MiXianTu.LOGGER.error("Sword flame shader compilation failed; inspect the preceding shader diagnostics");
            this.invalidLogged = true;
            return false;
        }
        this.invalidLogged = false;
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (target.getColorTextureView() == null || target.getDepthTexture() == null) return false;
        this.ensureDepth(target.getDepthTexture());
        CommandEncoder encoder = device.createCommandEncoder();
        // Sampling the attached depth texture is undefined; use a snapshot taken after world transparency.
        encoder.copyTextureToTexture(target.getDepthTexture(), this.depthCopy, 0, 0, 0, 0, 0, target.width, target.height);
        GpuBuffer buffer = this.uniforms.currentBuffer();
        this.staging.clear();
        camera.viewRotationMatrix.get(0, this.staging);
        this.staging.position(64);
        camera.orientation.transform(this.axis.set(1, 0, 0));
        this.vector(this.axis.x, this.axis.y, this.axis.z, 0);
        camera.orientation.transform(this.axis.set(0, 1, 0));
        this.vector(this.axis.x, this.axis.y, this.axis.z, 0);
        MxtClientConfig.Flames config = MxtClientConfig.INSTANCE.flames;
        this.vector(time, delta, config.intensity.getValue().floatValue(), 0);
        this.vector(config.softDistance.getValue().floatValue(), device.isZZeroToOne() ? 1 : 0,
                config.surfaceOpacity.getValue().floatValue(), 0);
        this.vector(LODController.particles(0), LODController.particles(1), LODController.particles(2), 0);
        int batchCount = 0;
        for (int lod = 0; lod < visible.size(); lod++) {
            List<BurningItemManager.Entry> group = visible.get(lod);
            for (int start = 0; start < group.size(); start += BATCH_SIZE) {
                int offset = this.itemStart + this.batchStride * batchCount;
                this.staging.position(offset);
                int count = Math.min(BATCH_SIZE, group.size() - start);
                for (int index = 0; index < count; index++) group.get(start + index).write(this.staging, camera);
                // The bound range must cover the entire declared GLSL block, including unused instances.
                while (this.staging.position() < offset + BATCH_SIZE * ITEM_BYTES) this.staging.putFloat(0);
                this.batches[batchCount] = buffer.slice(offset, BATCH_SIZE * ITEM_BYTES);
                this.counts[batchCount] = count;
                this.lods[batchCount++] = lod;
            }
        }
        this.staging.limit(this.itemStart + this.batchStride * batchCount).position(0);
        encoder.writeToBuffer(buffer.slice(0, this.staging.remaining()), this.staging);
        GpuBufferSlice frame = buffer.slice(0, FRAME_BYTES);
        int fluidBatches = 0;
        while (fluidBatches < batchCount && this.lods[fluidBatches] < 2) fluidBatches++;
        if (fluidBatches > 0) this.fluid.update(encoder, this.sampler, frame, this.batches, this.counts, fluidBatches);
        try (RenderPass pass = encoder.createRenderPass(() -> "Sword flames", target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("FlameFrame", frame);
            pass.bindTexture("FluidField", this.fluid.field(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.bindTexture("SceneDepth", this.depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setIndexBuffer(this.sampler.indices(), this.sampler.indexType());
            for (int i = 0; i < batchCount; i++) {
                pass.setUniform("FlameItems", this.batches[i]);
                if (this.lods[i] < 2 && config.surfaceOpacity.getValue() > 0) {
                    pass.setPipeline(FlamePipelines.SURFACE);
                    pass.setVertexBuffer(0, this.sampler.surface());
                    pass.drawIndexed(0, 0, (ParticleSampler.SURFACE_RINGS - 1) * 8 * 6, this.counts[i]);
                }
                pass.setPipeline(FlamePipelines.PARTICLES);
                pass.setVertexBuffer(0, this.sampler.particles());
                pass.drawIndexed(0, 0, LODController.particles(this.lods[i]) * 6, this.counts[i]);
            }
        }
        this.uniforms.rotate();
        return true;
    }

    private void vector(float x, float y, float z, float w) {
        this.staging.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private void ensureDepth(GpuTexture source) {
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        if (this.depthCopy != null && this.depthCopy.getWidth(0) == width && this.depthCopy.getHeight(0) == height
                && this.depthCopy.getFormat() == source.getFormat()) return;
        if (this.depthView != null) this.depthView.close();
        if (this.depthCopy != null) this.depthCopy.close();
        this.depthCopy = RenderSystem.getDevice().createTexture("Sword flame soft depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), width, height, 1, 1);
        this.depthView = RenderSystem.getDevice().createTextureView(this.depthCopy);
    }

    private static int align(int bytes, int alignment) {
        return (bytes + alignment - 1) / alignment * alignment;
    }

    @Override
    public void close() {
        if (this.depthView != null) this.depthView.close();
        if (this.depthCopy != null) this.depthCopy.close();
        this.fluid.close();
        this.sampler.close();
        this.uniforms.close();
        MemoryUtil.memFree(this.staging);
    }
}
