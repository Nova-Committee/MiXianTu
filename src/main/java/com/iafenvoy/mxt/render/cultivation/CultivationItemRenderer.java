package com.iafenvoy.mxt.render.cultivation;

import com.iafenvoy.mxt.attachment.FloatHoldingItemAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Renders the item currently reserved by the cultivation fuel consumer. */
public final class CultivationItemRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final float PIXEL = 1.0F / 16.0F;
    private final ItemModelResolver itemModelResolver;

    public CultivationItemRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> parent,
                                   ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector, int packedLight,
                       @NotNull AvatarRenderState state, float yRot, float xRot) {
        AbstractClientPlayer player = player(state);
        if (player == null) return;
        FloatHoldingItemAttachment holding = player.getData(MxtAttachments.FLOAT_HOLDING_ITEM);
        ItemStack stack = holding.item();
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        // Player-model space faces toward negative Z. The item therefore sits
        // five pixels forward, centered horizontally and eight pixels down.
        poseStack.translate(0.0F, 8.0F * PIXEL, -5.0F * PIXEL);
        ItemStackRenderState itemState = new ItemStackRenderState();
        this.itemModelResolver.updateForLiving(itemState, stack, ItemDisplayContext.GROUND, player);
        itemState.submit(poseStack, collector, packedLight,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
        poseStack.popPose();
    }

    private static AbstractClientPlayer player(AvatarRenderState state) {
        if (Minecraft.getInstance().level == null) return null;
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        return entity instanceof AbstractClientPlayer player ? player : null;
    }
}
