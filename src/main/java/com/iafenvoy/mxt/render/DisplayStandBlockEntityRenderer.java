package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.item.block.entity.DisplayStandBlockEntity;
import com.iafenvoy.mxt.render.DisplayStandBlockEntityRenderer.State;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Renders the single item resting above a display stand.
 */
public final class DisplayStandBlockEntityRenderer implements BlockEntityRenderer<DisplayStandBlockEntity, State> {
    private final ItemModelResolver itemModelResolver;

    public DisplayStandBlockEntityRenderer(Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DisplayStandBlockEntity entity, State state, float partialTick, @NonNull Vec3 cameraPosition,
                                   CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(entity, state, breakProgress);
        this.itemModelResolver.updateForTopItem(state.item, entity.displayedItem(), ItemDisplayContext.GROUND,
                entity.getLevel(), null, (int) entity.getBlockPos().asLong());
        state.rotation = entity.getLevel() == null ? 0.0F : (entity.getLevel().getGameTime() + partialTick) * 4.0F;
        state.height = entity.getLevel() == null ? 1.65F : 1.65F + Mth.sin((entity.getLevel().getGameTime() + partialTick) / 10.0F) * 0.05F;
    }

    @Override
    public void submit(State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        if (state.item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, state.height, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotation));
        poseStack.scale(0.65F, 0.65F, 0.65F);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private float rotation;
        private float height;
    }
}
