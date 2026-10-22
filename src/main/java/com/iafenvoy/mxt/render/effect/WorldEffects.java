package com.iafenvoy.mxt.render.effect;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.util.ClientRenderResources;
import com.iafenvoy.mxt.util.CloseableResources;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@EventBusSubscriber(Dist.CLIENT)
public final class WorldEffects implements AutoCloseable {
    private static final int MAX_VIEWS = 8;
    private static final ContextKey<Frame> FRAME = new ContextKey<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "world_effects"));
    private static final CloseableResources.Slot<WorldEffects> RESOURCES = ClientRenderResources.world(WorldEffects::new);
    private final EffectTargets targets = new EffectTargets();
    private final List<Frame> frames = new ArrayList<>();
    private long generation;
    private boolean captureFailureLogged;
    private boolean opaqueUnavailable;
    private boolean closed;

    private WorldEffects() {
    }

    // The event identity separates repeated extractions of the same LevelRenderState.
    public static @Nullable Frame frame(ExtractLevelRenderStateEvent event) {
        RenderSystem.assertOnRenderThread();
        WorldEffects resources = RESOURCES.get();
        if (resources == null || resources.closed) return null;
        LevelRenderState state = event.getRenderState();
        Frame existing = state.getRenderData(FRAME);
        if (existing != null && existing.owner == resources) {
            if (existing.extraction == event && existing.active()) return existing;
            existing.close();
        }
        if (resources.frames.size() >= MAX_VIEWS) return null;
        Frame created = resources.new Frame(event);
        resources.frames.add(created);
        state.setRenderData(FRAME, created);
        return created;
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Frame frame = frame(event);
        if (frame == null) return;
        for (ParticleGroupRenderState particle : event.getRenderState().particlesRenderState.particles)
            if (particle instanceof WorldEffectSource source) source.collect(frame);
    }

    @SubscribeEvent
    public static void opaque(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null || frame.renderer != event.getLevelRenderer() || !frame.active()) return;
        frame.opaqueReached = true;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (frame.opaqueSurface() && EffectTargets.valid(main)) frame.opaque.drain(draw -> frame.render(draw, main));
        frame.state.capture(() -> {
            if (!MxtClientConfig.INSTANCE.effects.sceneDepth.getValue() || frame.panoramic) return null;
            try {
                SceneDepth depth = SceneDepth.capture(frame.owner.targets, Minecraft.getInstance().getMainRenderTarget());
                if (depth != null) frame.owner.captureFailureLogged = false;
                return depth;
            } catch (RuntimeException failure) {
                if (!frame.owner.captureFailureLogged)
                    MiXianTu.LOGGER.warn("World effect scene depth is unavailable; using basic rendering", failure);
                frame.owner.captureFailureLogged = true;
                return null;
            }
        });
    }

    @SubscribeEvent
    public static void translucent(RenderLevelStageEvent.AfterTranslucentParticles event) {
        try (Frame frame = event.getLevelRenderState().getRenderData(FRAME)) {
            if (frame == null || frame.renderer != event.getLevelRenderer() || !frame.active()) return;
            RenderTarget target = EffectTargets.particles(event.getLevelRenderer());
            if (target != null) frame.state.drain(draw -> frame.render(draw, target));
        }
    }

    @SubscribeEvent
    public static void endFrame(RenderFrameEvent.Post event) {
        WorldEffects resources = RESOURCES.peek();
        if (resources != null) resources.finishFrame();
    }

    private void finishFrame() {
        for (Frame frame : List.copyOf(this.frames)) frame.close();
        this.generation++;
        this.targets.endFrame();
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.finishFrame();
        this.targets.close();
    }

    public final class Frame implements AutoCloseable {
        private final WorldEffects owner = WorldEffects.this;
        private final LevelRenderState view;
        private final LevelRenderer renderer;
        private final EffectFrame<EffectDraw, SceneDepth> state;
        private final EffectFrame<EffectDraw, SceneDepth> opaque;
        private final Vec3 camera;
        private final boolean panoramic;
        private boolean opaqueSubmitted, opaqueReached;
        private @Nullable ExtractLevelRenderStateEvent extraction;

        private Frame(ExtractLevelRenderStateEvent event) {
            this.extraction = event;
            this.view = event.getRenderState();
            this.renderer = event.getLevelRenderer();
            this.camera = this.view.cameraRenderState.pos;
            this.panoramic = this.view.cameraRenderState.isPanoramicMode;
            this.state = new EffectFrame<>(this.view, WorldEffects.this.generation,
                    exception -> MiXianTu.LOGGER.error("Failed to finish a world effect view", exception));
            this.opaque = new EffectFrame<>(this.view, WorldEffects.this.generation,
                    exception -> MiXianTu.LOGGER.error("Failed to finish an opaque world effect view", exception));
        }

        public boolean active() {
            RenderSystem.assertOnRenderThread();
            return !this.owner.closed && this.view.getRenderData(FRAME) == this
                    && this.view.cameraRenderState.pos.equals(this.camera)
                    && this.state.matches(this.view, this.owner.generation);
        }

        public Vec3 camera() {
            return this.camera;
        }

        public boolean opaqueSurface() {
            return !this.owner.opaqueUnavailable && !this.panoramic && this.renderer == Minecraft.getInstance().levelRenderer
                    && this.renderer.getClass() == LevelRenderer.class;
        }

        public boolean requestDepth() {
            return this.active() && this.state.requestDepth();
        }

        public boolean submit(EffectDraw draw) {
            if (draw.stage() == EffectDraw.Stage.OPAQUE_SURFACE) {
                boolean accepted = this.active() && this.opaqueSurface() && !draw.needsDepth() && this.opaque.submit(draw);
                this.opaqueSubmitted |= accepted;
                return accepted;
            }
            return this.active() && (!draw.needsDepth() || this.state.requestDepth()) && this.state.submit(draw);
        }

        public @Nullable EffectTargets targets() {
            return this.active() ? this.owner.targets : null;
        }

        public @Nullable SceneDepth depth(RenderTarget target) {
            if (!this.active()) return null;
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (target != main && target != this.renderer.getParticlesTarget()) return null;
            SceneDepth depth = this.state.resource();
            return depth != null && depth.matches(main, target) ? depth : null;
        }

        private void render(EffectDraw draw, RenderTarget target) {
            if (!this.active()) return;
            EffectDraw.Prepared prepared = draw.prepare(this, target);
            if (prepared == null) return;
            try (prepared) {
                if (!this.active() || !EffectTargets.valid(target)) return;
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "MXT world effects", target.getColorTextureView(), OptionalInt.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    prepared.render(pass);
                }
            }
        }

        @Override
        public void close() {
            RenderSystem.assertOnRenderThread();
            if (this.opaqueSubmitted && !this.opaqueReached) this.owner.opaqueUnavailable = true;
            this.extraction = null;
            this.opaque.close();
            this.state.close();
            this.owner.frames.remove(this);
        }
    }
}
