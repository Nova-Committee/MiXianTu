package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.item.block.SpiritHerbPlotBlock;
import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Two crossed cutout planes above the soil bed. Size follows the saved progress, and a newly sown plant is still
 * drawn small so sowing is visible before the first growth tick.
 */
public final class SpiritHerbPlotBlockEntityRenderer implements BlockEntityRenderer<SpiritHerbPlotBlockEntity, SpiritHerbPlotBlockEntityRenderer.State> {
    private static final float MIN_SCALE = 0.15F;

    public SpiritHerbPlotBlockEntityRenderer(Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpiritHerbPlotBlockEntity entity, State state, float partialTick,
                                   @NonNull Vec3 cameraPosition, CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(entity, state, breakProgress);
        state.texture = null;
        if (entity.getLevel() == null || entity.herb() == null) return;
        SpiritHerb herb = MxtDatapackRegistries.get(entity.getLevel().registryAccess(), MxtResourceKeys.SPIRIT_HERB, entity.herb())
                .orElse(null);
        if (herb == null || herb.growth().isEmpty()) return;
        state.texture = herb.growth().get().textureResource();
        float span = 1.0F - (float) SpiritHerbPlotBlock.SOIL_HEIGHT;
        float scale = Math.min(1.0F, Math.max(MIN_SCALE, entity.progress() / herb.growth().get().maxAge()));
        state.top = (float) SpiritHerbPlotBlock.SOIL_HEIGHT + span * scale;
    }

    @Override
    public void submit(State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        if (state.texture == null) return;
        Identifier texture = state.texture;
        float top = state.top;
        float bottom = (float) SpiritHerbPlotBlock.SOIL_HEIGHT;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
            plane(buffer, pose, 0.0F, bottom, 0.0F, 1.0F, top, 1.0F, light);
            plane(buffer, pose, 0.0F, bottom, 1.0F, 1.0F, top, 0.0F, light);
        });
    }

    // One diagonal sheet, drawn on both sides so the cross stays visible from either approach.
    private static void plane(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0,
                              float x1, float y1, float z1, int light) {
        float nx = z1 - z0;
        float nz = x0 - x1;
        float length = (float) Math.sqrt(nx * nx + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            nz /= length;
        }
        quad(buffer, pose, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, nx, 0.0F, nz, light);
        quad(buffer, pose, x1, y0, z1, x0, y0, z0, x0, y1, z0, x1, y1, z1, -nx, 0.0F, -nz, light);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, int light) {
        vertex(buffer, pose, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz, light);
        vertex(buffer, pose, x1, y1, z1, 1.0F, 1.0F, nx, ny, nz, light);
        vertex(buffer, pose, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz, light);
        vertex(buffer, pose, x3, y3, z3, 0.0F, 0.0F, nx, ny, nz, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static final class State extends BlockEntityRenderState {
        @Nullable
        private Identifier texture;
        private float top = (float) SpiritHerbPlotBlock.SOIL_HEIGHT;
    }
}
