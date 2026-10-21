package com.iafenvoy.mxt.render.particle;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.ClientRenderResources;
import com.iafenvoy.mxt.util.CloseableResources;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(Dist.CLIENT)
public final class LightBeamParticleGroup extends ParticleGroup<LightBeamParticle> {
    public static final ParticleRenderType TYPE = new ParticleRenderType("mxt:light_beam");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pipeline/light_beam"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/light_beam"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/light_beam"))
            .withUniform("LightBeamInstances", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(true).build();
    private static final CloseableResources.Slot<Buffers> BUFFERS = ClientRenderResources.world(Buffers::new);
    private final State state = new State();

    private LightBeamParticleGroup(ParticleEngine engine) {
        super(engine);
    }

    @SubscribeEvent
    public static void registerGroups(RegisterParticleGroupsEvent event) {
        event.register(TYPE, LightBeamParticleGroup::new);
    }

    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
        this.state.frame.clear();
        this.state.camera = camera.position();
        for (LightBeamParticle particle : this.particles)
            if (frustum.isVisible(particle.getBoundingBox()))
                this.state.frame.add(particle.shape, particle.renderAge(partialTickTime), particle.phase, this.state.camera);
        this.state.frame.build(this.state.camera, frustum::isVisible);
        return this.state;
    }

    private static final class State implements ParticleGroupRenderState, SubmitNodeCollector.ParticleGroupRenderer {
        private final LightBeamParticleFrame frame = new LightBeamParticleFrame();
        private Vec3 camera = Vec3.ZERO;

        @Override
        public void clear() {
            this.frame.clear();
        }

        @Override
        public boolean isEmpty() {
            return this.frame.entries().isEmpty();
        }

        @Override
        public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            if (!this.isEmpty()) collector.submitParticleGroup(this);
        }

        @Override
        public QuadParticleRenderState.@Nullable PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache cache, boolean translucent) {
            if (!translucent || this.isEmpty()) return null;
            Buffers buffers = BUFFERS.get();
            if (buffers == null) return null;
            buffers.prepare(this.frame.entries(), this.camera);
            return new QuadParticleRenderState.PreparedBuffers(0, RenderSystem.getDynamicUniforms()
                    .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F), new Vector3f(), new Matrix4f()), Map.of());
        }

        @Override
        public void render(QuadParticleRenderState.PreparedBuffers prepared, ParticleFeatureRenderer.ParticleBufferCache cache, RenderPass pass, TextureManager textures) {
            Buffers buffers = BUFFERS.peek();
            if (buffers == null) return;
            pass.setPipeline(PIPELINE);
            pass.setUniform("DynamicTransforms", prepared.dynamicTransforms());
            buffers.render(pass);
        }
    }

    private static final class Buffers implements AutoCloseable {
        private static final int BATCH_BYTES = LightBeamParticleMesh.BATCH_SIZE * LightBeamParticleMesh.INSTANCE_BYTES;
        private static final int MAX_BATCHES = Math.ceilDiv(LightBeamParticleMesh.MAX_INSTANCES, LightBeamParticleMesh.BATCH_SIZE);
        private final GpuBuffer[] vertices = new GpuBuffer[LightBeamParticleMesh.LOD_COUNT];
        private final GpuBuffer[] indices = new GpuBuffer[LightBeamParticleMesh.LOD_COUNT];
        private final List<AutoCloseable> allocations = new ArrayList<>();
        private final GpuBufferSlice[] batches = new GpuBufferSlice[MAX_BATCHES];
        private final int[] counts = new int[MAX_BATCHES];
        private final int[] lods = new int[MAX_BATCHES];
        private final int stride;
        private final MappableRingBuffer instances;
        private final ByteBuffer staging;
        private int batchCount;
        private boolean closed;
        private boolean pendingRotation;

        private Buffers() {
            try {
                int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
                this.stride = Math.ceilDiv(BATCH_BYTES, alignment) * alignment;
                this.staging = MemoryUtil.memCalloc(this.stride * MAX_BATCHES);
                this.allocations.add(() -> MemoryUtil.memFree(this.staging));
                this.instances = new MappableRingBuffer(() -> "Light beam instances", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, this.staging.capacity());
                this.allocations.add(this.instances);
                for (int lod = 0; lod < LightBeamParticleMesh.LOD_COUNT; lod++) {
                    ByteBuffer vertexData = MemoryUtil.memAlloc(LightBeamParticleMesh.vertexCount(lod) * 20);
                    ByteBuffer indexData = null;
                    try {
                        ByteBuffer indexOutput = indexData = MemoryUtil.memAlloc(LightBeamParticleMesh.indexCount(lod) * Short.BYTES);
                        LightBeamParticleMesh.write(lod, (x, y, ring, mode, u) -> vertexData.putFloat(x).putFloat(y).putFloat(ring).putFloat(mode).putFloat(u), index -> indexOutput.putShort((short) index));
                        vertexData.flip();
                        indexData.flip();
                        this.vertices[lod] = RenderSystem.getDevice().createBuffer(() -> "Light beam template", GpuBuffer.USAGE_VERTEX, vertexData);
                        this.allocations.add(this.vertices[lod]);
                        this.indices[lod] = RenderSystem.getDevice().createBuffer(() -> "Light beam indices", GpuBuffer.USAGE_INDEX, indexData);
                        this.allocations.add(this.indices[lod]);
                    } finally {
                        MemoryUtil.memFree(indexData);
                        MemoryUtil.memFree(vertexData);
                    }
                }
            } catch (RuntimeException | Error failure) {
                try {
                    this.close();
                } catch (Exception closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        private void prepare(List<LightBeamParticleFrame.Entry> entries, Vec3 camera) {
            // The previous draw's RenderPass is closed before this preparation starts.
            if (this.pendingRotation) {
                this.instances.rotate();
                this.pendingRotation = false;
            }
            GpuBuffer buffer = this.instances.currentBuffer();
            this.staging.clear();
            this.batchCount = 0;
            for (int start = 0; start < entries.size(); start += LightBeamParticleMesh.BATCH_SIZE) {
                int count = Math.min(LightBeamParticleMesh.BATCH_SIZE, entries.size() - start);
                int offset = this.batchCount * this.stride;
                this.staging.position(offset);
                for (int i = 0; i < count; i++) {
                    LightBeamParticleFrame.Entry entry = entries.get(start + i);
                    entry.shape().writeInstance(entry.segment(), camera, this.staging);
                }
                // Bind the full declared UBO array, including unused records in the final batch.
                while (this.staging.position() < offset + BATCH_BYTES) this.staging.putFloat(0);
                this.counts[this.batchCount] = count;
                this.lods[this.batchCount] = LightBeamParticleMesh.lod(entries.get(start + count - 1).distanceSquared());
                this.batches[this.batchCount++] = buffer.slice(offset, BATCH_BYTES);
            }
            this.staging.limit(this.batchCount * this.stride).position(0);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, this.staging.remaining()), this.staging);
        }

        private void render(RenderPass pass) {
            this.pendingRotation = true;
            for (int batch = 0; batch < this.batchCount; batch++) {
                int lod = this.lods[batch];
                pass.setUniform("LightBeamInstances", this.batches[batch]);
                pass.setVertexBuffer(0, this.vertices[lod]);
                pass.setIndexBuffer(this.indices[lod], VertexFormat.IndexType.SHORT);
                pass.drawIndexed(0, 0, LightBeamParticleMesh.indexCount(lod), this.counts[batch]);
            }
        }

        @Override
        public void close() throws Exception {
            if (this.closed) return;
            this.closed = true;
            Exception failure = null;
            for (int i = this.allocations.size() - 1; i >= 0; i--) {
                try {
                    this.allocations.get(i).close();
                } catch (Exception exception) {
                    if (failure == null) failure = exception;
                    else if (failure != exception) failure.addSuppressed(exception);
                }
            }
            this.allocations.clear();
            if (failure != null) throw failure;
        }
    }
}
