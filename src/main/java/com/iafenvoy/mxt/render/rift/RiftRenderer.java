package com.iafenvoy.mxt.render.rift;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.data.rift.RiftAppearance;
import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.iafenvoy.mxt.render.MxtRenderPipelines;
import com.iafenvoy.mxt.render.MxtRenderTypes;
import com.iafenvoy.mxt.render.effect.EffectDraw;
import com.iafenvoy.mxt.render.effect.WorldEffects;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftGeometry;
import com.iafenvoy.mxt.runtime.rift.RiftVisual;
import com.iafenvoy.mxt.screen.rift.RiftEditorScreen;
import com.iafenvoy.mxt.util.ClientRenderResources;
import com.iafenvoy.mxt.util.CloseableResources;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

@EventBusSubscriber(Dist.CLIENT)
public final class RiftRenderer {
    private static final CloseableResources.Slot<Cache> CACHE = ClientRenderResources.world(Cache::new);
    private static final ContextKey<List<RiftClientState.Entry>> FALLBACK = new ContextKey<>(Identifier.fromNamespaceAndPath("mxt", "rift_fallback"));

    private RiftRenderer() {
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        WorldEffects.Frame frame = WorldEffects.frame(event);
        if (frame == null) return;
        Cache cache = CACHE.get();
        if (cache == null) return;
        List<RiftClientState.Entry> records = new ArrayList<>(RiftClientState.entries());
        List<RiftClientState.Entry> preview = RiftEditorScreen.preview();
        UUID editing = RiftEditorScreen.previewedId();
        if (editing != null) records.removeIf(record -> record.id().equals(editing));
        records.addAll(preview);
        cache.retain(records);
        List<RiftClientState.Entry> fallback = new ArrayList<>();
        for (RiftClientState.Entry record : records) {
            if (!record.visual().enabled() || record.mesh().bounds().distanceToSqr(frame.camera()) > 256 * 256
                    || !event.getFrustum().isVisible(record.mesh().bounds().inflate(0.5))) continue;
            if (!frame.opaqueSurface()) {
                fallback.add(record);
                continue;
            }
            Mesh mesh = cache.mesh(record);
            frame.submit(new Draw(record, mesh));
        }
        event.getRenderState().setRenderData(FALLBACK, List.copyOf(fallback));
    }

    @SubscribeEvent
    public static void submitFallback(SubmitCustomGeometryEvent event) {
        List<RiftClientState.Entry> records = event.getLevelRenderState().getRenderData(FALLBACK);
        if (records == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (RiftClientState.Entry entry : records) {
            event.getPoseStack().pushPose();
            Vec3 relative = entry.visual().placement().origin().subtract(camera);
            event.getPoseStack().translate(relative.x, relative.y, relative.z);
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), MxtRenderTypes.riftFallback(), (pose, buffer) -> {
                for (RiftGeometry.Triangle triangle : entry.mesh().triangles())
                    for (int i : new int[]{triangle.a(), triangle.b(), triangle.c()}) {
                        RiftVertex vertex = entry.visual().shape().vertices().get(i);
                        RiftPlacement p = entry.visual().placement();
                        Vec3 offset = p.tangent().scale(vertex.u()).add(p.bitangent().scale(vertex.v()));
                        buffer.addVertex(pose, (float) offset.x, (float) offset.y, (float) offset.z).setColor(entry.visual().color());
                    }
            });
            event.getPoseStack().popPose();
        }
    }

    private record Draw(RiftClientState.Entry entry, Mesh mesh) implements EffectDraw {
        @Override
        public Stage stage() {
            return Stage.OPAQUE_SURFACE;
        }

        @Override
        public Prepared prepare(WorldEffects.Frame frame, RenderTarget target) {
            Vec3 relative = this.entry.visual().placement().origin().subtract(frame.camera());
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewMatrix())
                    .translate((float) relative.x, (float) relative.y, (float) relative.z), new Vector4f(1), new Vector3f(), new Matrix4f());
            boolean shader = MxtClientConfig.INSTANCE.rifts.shaders.getValue()
                    && RenderSystem.getDevice().precompilePipeline(MxtRenderPipelines.RIFT).isValid();
            return pass -> {
                pass.setPipeline(shader ? MxtRenderPipelines.RIFT : MxtRenderPipelines.RIFT_BASIC);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("RiftParameters", this.mesh.parameters);
                pass.setVertexBuffer(0, this.mesh.vertices);
                pass.setIndexBuffer(this.mesh.indices, VertexFormat.IndexType.SHORT);
                pass.drawIndexed(0, 0, this.mesh.count, 1);
            };
        }
    }

    private static final class Cache implements AutoCloseable {
        private final Map<UUID, Mesh> meshes = new HashMap<>();

        void retain(List<RiftClientState.Entry> entries) {
            Set<UUID> ids = new HashSet<>();
            for (RiftClientState.Entry entry : entries) ids.add(entry.id());
            for (UUID id : Set.copyOf(this.meshes.keySet())) if (!ids.contains(id)) this.meshes.remove(id).close();
        }

        Mesh mesh(RiftClientState.Entry entry) {
            Mesh mesh = this.meshes.get(entry.id());
            if (mesh == null || mesh.revision != entry.revision()) {
                Mesh replacement = new Mesh(entry);
                if (mesh != null) mesh.close();
                mesh = replacement;
                this.meshes.put(entry.id(), mesh);
            }
            return mesh;
        }

        @Override
        public void close() {
            this.meshes.values().forEach(Mesh::close);
            this.meshes.clear();
        }
    }

    private static final class Mesh implements AutoCloseable {
        final long revision;
        final GpuBuffer vertices, indices, parameters;
        final int count;

        Mesh(RiftClientState.Entry entry) {
            this.revision = entry.revision();
            RiftVisual visual = entry.visual();
            RiftShape shape = visual.shape();
            RiftPlacement placement = visual.placement();
            RiftAppearance appearance = visual.appearance();
            this.count = entry.mesh().triangles().size() * 3;
            ByteBuffer vertex = MemoryUtil.memAlloc(shape.vertices().size() * 20), index = MemoryUtil.memAlloc(this.count * 2);
            ByteBuffer params = MemoryUtil.memCalloc(16 * (5 + 128));
            List<GpuBuffer> allocated = new ArrayList<>();
            try {
                for (RiftVertex point : shape.vertices()) {
                    Vec3 offset = placement.tangent().scale(point.u()).add(placement.bitangent().scale(point.v()));
                    vertex.putFloat((float) offset.x).putFloat((float) offset.y).putFloat((float) offset.z).putFloat((float) point.u()).putFloat((float) point.v());
                }
                for (RiftGeometry.Triangle triangle : entry.mesh().triangles())
                    index.putShort((short) triangle.a()).putShort((short) triangle.b()).putShort((short) triangle.c());
                int color = visual.color(), edge = appearance.edgeColor() == -1 ? RiftColors.rim(color) : appearance.edgeColor();
                tint(params, color);
                tint(params, edge);
                params.putFloat((float) appearance.speed()).putFloat((float) appearance.edgeWidth()).putFloat(appearance.style().equals("rift") ? 1 : 0).putFloat(shape.vertices().size());
                params.putFloat((float) appearance.textureScale()).putFloat((float) Math.toRadians(appearance.textureRotation()))
                        .putFloat((float) appearance.textureU()).putFloat((float) appearance.textureV());
                params.putFloat((float) appearance.glow()).putFloat((float) appearance.particles()).putFloat(0).putFloat(0);
                for (RiftVertex point : shape.vertices())
                    params.putFloat((float) point.u()).putFloat((float) point.v()).putFloat(0).putFloat(0);
                vertex.flip();
                index.flip();
                params.position(0);
                params.limit(params.capacity());
                this.vertices = RenderSystem.getDevice().createBuffer(() -> "Rift surface", GpuBuffer.USAGE_VERTEX, vertex);
                allocated.add(this.vertices);
                this.indices = RenderSystem.getDevice().createBuffer(() -> "Rift triangles", GpuBuffer.USAGE_INDEX, index);
                allocated.add(this.indices);
                this.parameters = RenderSystem.getDevice().createBuffer(() -> "Rift material", GpuBuffer.USAGE_UNIFORM, params);
            } catch (RuntimeException | Error failure) {
                allocated.forEach(GpuBuffer::close);
                throw failure;
            } finally {
                MemoryUtil.memFree(vertex);
                MemoryUtil.memFree(index);
                MemoryUtil.memFree(params);
            }
        }

        private static void tint(ByteBuffer buffer, int color) {
            buffer.putFloat(((color >> 16) & 255) / 255F).putFloat(((color >> 8) & 255) / 255F).putFloat((color & 255) / 255F).putFloat(1);
        }

        @Override
        public void close() {
            this.parameters.close();
            this.indices.close();
            this.vertices.close();
        }
    }
}
