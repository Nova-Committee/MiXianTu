package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.item.block.entity.StationBlockEntity;
import com.iafenvoy.mxt.render.StationBlockEntityRenderer.State;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Renders the configured station display item above its workstation block.
 */
public final class StationBlockEntityRenderer<T extends StationBlockEntity> implements BlockEntityRenderer<T, State> {
    private final ItemModelResolver itemModelResolver;

    public StationBlockEntityRenderer(Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick, @NonNull Vec3 cameraPosition, CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(entity, state, breakProgress);
        this.itemModelResolver.updateForTopItem(state.item, entity.displayStack(), ItemDisplayContext.GROUND,
                entity.getLevel(), null, (int) entity.getBlockPos().asLong());
        state.rotation = entity.isSystemStation() && entity.getLevel() != null
                ? (entity.getLevel().getGameTime() + partialTick) * 4.0F : 0.0F;
    }

    @Override
    public void submit(State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector submitNodeCollector, @NonNull CameraRenderState camera) {
        if (state.item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotation));
        poseStack.scale(1.5F, 1.5F, 1.5F);
        state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private float rotation;
    }
}
