package com.iafenvoy.mxt.render.entity;

import com.iafenvoy.mxt.data.StormCloudOptions;
import com.iafenvoy.mxt.entity.StormCloudEntity;
import com.iafenvoy.mxt.render.MxtRenderPipelines;
import com.iafenvoy.mxt.render.effect.EffectDraw;
import com.iafenvoy.mxt.render.effect.SceneDepth;
import com.iafenvoy.mxt.render.effect.WorldEffects;
import com.iafenvoy.saturn.util.ClientRenderResources;
import com.iafenvoy.saturn.util.CloseableResources;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.*;

@EventBusSubscriber(Dist.CLIENT)
public final class StormCloudRenderer {
    private static final int MAX_DRAWN = 16;
    private static final int UNIFORM_BYTES = 16 + MAX_DRAWN * 64;
    // Inward winding keeps the exit face visible when the camera enters the volume.
    private static final short[] INDICES = {0, 1, 3, 0, 3, 2, 4, 6, 7, 4, 7, 5,
            0, 4, 5, 0, 5, 1, 2, 3, 7, 2, 7, 6, 0, 2, 6, 0, 6, 4, 1, 5, 7, 1, 7, 3};
    private static final CloseableResources.Slot<Buffers> BUFFERS = ClientRenderResources.world(Buffers::new);
    private static final Map<ClientLevel, Set<StormCloudEntity>> CLOUDS = new HashMap<>();

    private StormCloudRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel level && event.getEntity() instanceof StormCloudEntity cloud)
            CLOUDS.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(cloud);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel level && event.getEntity() instanceof StormCloudEntity cloud) {
            Set<StormCloudEntity> clouds = CLOUDS.get(level);
            if (clouds != null && clouds.remove(cloud) && clouds.isEmpty()) CLOUDS.remove(level);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) CLOUDS.remove(level);
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Set<StormCloudEntity> clouds = CLOUDS.get(event.getLevel());
        if (clouds == null) return;
        // Join fires before insertion. Discard entries if another listener canceled the join.
        clouds.removeIf(cloud -> !cloud.isAddedToLevel() || cloud.isRemoved());
        if (clouds.isEmpty()) {
            CLOUDS.remove(event.getLevel());
            return;
        }
        WorldEffects.Frame frame = WorldEffects.frame(event);
        if (frame == null) return;
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<VisibleCloud> visible = new ArrayList<>();
        for (StormCloudEntity cloud : clouds) {
            StormCloudOptions options = cloud.options();
            // LevelRenderState.gameTime is populated after extraction.
            float age = cloud.visualAgeTicks(partialTick);
            if (cloud.expired() || (ARGB.alpha(options.cloudColor()) == 0 && ARGB.alpha(options.lightningColor()) == 0))
                continue;
            float life = options.lifetime() * 0.05F;
            if (cloud.isTribulationCloud()) {
                life = -cloud.tribulationOpacity(partialTick);
                if (life == 0) continue;
            }
            Vec3 center = cloud.getPosition(partialTick);
            AABB bounds = options.bounds(center.x, center.y, center.z);
            if (bounds.distanceToSqr(frame.camera()) > StormCloudEntity.RENDER_DISTANCE * StormCloudEntity.RENDER_DISTANCE
                    || !event.getFrustum().isVisible(bounds)) continue;
            float seed = (cloud.getUUID().hashCode() & 0xFFFFFF) / 16777216.0F;
            visible.add(new VisibleCloud(center.subtract(frame.camera()), options, age, life, seed));
        }
        if (visible.isEmpty()) return;
        visible.sort(Comparator.comparingDouble((VisibleCloud cloud) -> cloud.relativeCenter.lengthSqr()).reversed());
        frame.submit(new Draw(List.copyOf(visible.subList(Math.max(0, visible.size() - MAX_DRAWN), visible.size()))));
    }

    private record VisibleCloud(Vec3 relativeCenter, StormCloudOptions options, float ageTicks, float life,
                                float seed) {
    }

    private record Draw(List<VisibleCloud> clouds) implements EffectDraw {
        @Override
        public Stage stage() {
            return Stage.SKY_VOLUME;
        }

        @Override
        public boolean needsDepth() {
            return true;
        }

        @Override
        public @Nullable Prepared prepare(WorldEffects.Frame frame, RenderTarget target) {
            Buffers buffers = BUFFERS.get();
            if (buffers == null) return null;
            GpuDevice device = RenderSystem.getDevice();
            SceneDepth depth = frame.skyDepth(target);
            // The transparency compositor sorts the particle layer by its depth texture.
            boolean separate = target != Minecraft.getInstance().getMainRenderTarget();
            RenderPipeline basic = separate ? MxtRenderPipelines.STORM_CLOUD_SORTED : MxtRenderPipelines.STORM_CLOUD;
            RenderPipeline enhanced = separate ? MxtRenderPipelines.STORM_CLOUD_SORTED_DEPTH : MxtRenderPipelines.STORM_CLOUD_DEPTH;
            boolean useDepth = depth != null && device.precompilePipeline(enhanced).isValid();
            RenderPipeline pipeline = useDepth ? enhanced : basic;
            GpuBuffer parameters = buffers.upload(this.clouds);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrix(), new Vector4f(1), new Vector3f(), new Matrix4f());
            return pass -> {
                pass.setPipeline(pipeline);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setUniform("StormCloudInstances", parameters);
                if (useDepth) depth.bind(pass);
                pass.setVertexBuffer(0, buffers.vertices);
                pass.setIndexBuffer(buffers.indices, VertexFormat.IndexType.SHORT);
                buffers.drawn = true;
                pass.drawIndexed(0, 0, INDICES.length, this.clouds.size());
            };
        }
    }

    private static final class Buffers implements AutoCloseable {
        private final GpuBuffer vertices;
        private final GpuBuffer indices;
        private final MappableRingBuffer instances;
        private boolean drawn;

        private Buffers() {
            GpuDevice device = RenderSystem.getDevice();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer points = stack.malloc(8 * 3 * Float.BYTES);
                for (int i = 0; i < 8; i++)
                    points.putFloat((i & 1) == 0 ? -1 : 1).putFloat((i & 2) == 0 ? -1 : 1).putFloat((i & 4) == 0 ? -1 : 1);
                ByteBuffer triangles = stack.malloc(INDICES.length * Short.BYTES);
                for (short index : INDICES) triangles.putShort(index);
                this.vertices = device.createBuffer(() -> "Storm cloud bounds", GpuBuffer.USAGE_VERTEX, points.flip());
                try {
                    this.indices = device.createBuffer(() -> "Storm cloud triangles", GpuBuffer.USAGE_INDEX, triangles.flip());
                    try {
                        this.instances = new MappableRingBuffer(() -> "Storm cloud instances",
                                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_BYTES);
                    } catch (RuntimeException | Error failure) {
                        this.indices.close();
                        throw failure;
                    }
                } catch (RuntimeException | Error failure) {
                    this.vertices.close();
                    throw failure;
                }
            }
        }

        private GpuBuffer upload(List<VisibleCloud> clouds) {
            // Rotation inserts a GPU fence and must run outside the world effect render pass.
            if (this.drawn) {
                this.instances.rotate();
                this.drawn = false;
            }
            GpuDevice device = RenderSystem.getDevice();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(UNIFORM_BYTES);
                data.putFloat(device.isZZeroToOne() ? 1 : 0).position(16);
                for (VisibleCloud visible : clouds) {
                    StormCloudOptions options = visible.options;
                    Vec3 center = visible.relativeCenter;
                    data.putFloat((float) center.x).putFloat((float) center.y).putFloat((float) center.z).putFloat(options.radius());
                    data.putFloat(options.thickness() * 0.5F).putFloat(visible.ageTicks).putFloat(visible.life).putFloat(visible.seed);
                    putColor(data, options.cloudColor());
                    putColor(data, options.lightningColor());
                }
                GpuBuffer buffer = this.instances.currentBuffer();
                device.createCommandEncoder().writeToBuffer(buffer.slice(), data.position(0));
                return buffer;
            }
        }

        private static void putColor(ByteBuffer data, int color) {
            data.putFloat(ARGB.redFloat(color)).putFloat(ARGB.greenFloat(color))
                    .putFloat(ARGB.blueFloat(color)).putFloat(ARGB.alphaFloat(color));
        }

        @Override
        public void close() {
            //noinspection EmptyTryBlock
            try (this.vertices; this.indices; this.instances) {
                // Resources release in reverse allocation order.
            }
        }
    }
}
