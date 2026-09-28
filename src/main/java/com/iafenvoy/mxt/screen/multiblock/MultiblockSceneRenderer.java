package com.iafenvoy.mxt.screen.multiblock;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.List;

/**
 * Draws the scene into its own texture. Everything is lit as an item, because there is no world here to light it
 * from, and one texture is shared by the whole frame, so the camera is applied before the cells are walked.
 */
final class MultiblockSceneRenderer extends PictureInPictureRenderer<MultiblockSceneRenderState> {
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    private static final float HOVER_LINE_WIDTH = 2.0F;

    MultiblockSceneRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<MultiblockSceneRenderState> getRenderStateClass() {
        return MultiblockSceneRenderState.class;
    }

    @Override
    protected void renderToTexture(MultiblockSceneRenderState renderState, PoseStack poseStack) {
        List<MultiblockSceneRenderState.SceneBlock> blocks = renderState.blocks();
        if (blocks.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeCollector nodes = features.getSubmitNodeStorage();
        ItemModelResolver itemModels = minecraft.getItemModelResolver();
        // Safe to share between cells: a block submit copies the model parts and the tints it was handed.
        BlockModelRenderState blockModel = new BlockModelRenderState();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);

        // The camera lives in one place so picking and drawing cannot drift apart.
        renderState.camera().apply(poseStack);

        for (MultiblockSceneRenderState.SceneBlock block : blocks) {
            poseStack.pushPose();
            if (block.state() != null) {
                poseStack.translate(block.x(), block.y(), block.z());
                minecraft.getBlockModelResolver().update(blockModel, block.state(), BlockDisplayContext.create());
                blockModel.submit(poseStack, nodes, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            } else {
                // An item model is modelled around its own centre, so the cell centre is where it goes. Its state
                // must be a new one per cell: it hands its own quad list to the collector, which only draws that
                // list after this loop, so a shared state would leave every cell with the last item's quads.
                ItemStackRenderState itemModel = new ItemStackRenderState();
                itemModels.updateForTopItem(itemModel, block.stack(), ItemDisplayContext.NONE, minecraft.level, null, block.index());
                poseStack.translate(block.x() + 0.5F, block.y() + 0.5F, block.z() + 0.5F);
                itemModel.submit(poseStack, nodes, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            }
            poseStack.popPose();
        }
        features.renderAllFeatures();
        this.renderHoverOutline(minecraft, renderState, poseStack);
    }

    // Drawn once the cells are down. The cube is not inflated and the line ignores depth, because the outline has to
    // read as a box: every edge of the cell is shown, including the ones the cell itself would hide. The line grows
    // with the GUI scale because the texture is that many times the size of the rectangle it is blitted into.
    private void renderHoverOutline(Minecraft minecraft, MultiblockSceneRenderState renderState, PoseStack poseStack) {
        MultiblockSceneRenderState.SceneBlock hovered = renderState.hoveredBlock();
        if (hovered == null) return;
        int guiScale = minecraft.gameRenderer.getGameRenderState().windowRenderState.guiScale;
        VertexConsumer outline = this.bufferSource.getBuffer(MultiblockOutlineRenderTypes.outline());
        poseStack.pushPose();
        poseStack.translate(hovered.x(), hovered.y(), hovered.z());
        ShapeRenderer.renderShape(poseStack, outline, Shapes.block(), 0.0D, 0.0D, 0.0D, HOVER_COLOR, HOVER_LINE_WIDTH * guiScale);
        poseStack.popPose();
        this.bufferSource.endBatch();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "mxt_multiblock";
    }
}
