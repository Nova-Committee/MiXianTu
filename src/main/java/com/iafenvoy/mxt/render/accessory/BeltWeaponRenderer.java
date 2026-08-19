package com.iafenvoy.mxt.render.accessory;

import com.iafenvoy.mxt.integration.CuriosIntegration;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class BeltWeaponRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ItemModelResolver itemModelResolver;

    public BeltWeaponRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector, int packedLight,
                       @NotNull AvatarRenderState state, float yRot, float xRot) {
        AbstractClientPlayer player = player(state);
        if (player == null) return;
        Map<CuriosIntegration.Place, ItemStack> stacks = CuriosIntegration.equippedForCosmetic(player);
        ItemStack left = stacks.get(CuriosIntegration.Place.BELT_LEFT);
        ItemStack right = stacks.get(CuriosIntegration.Place.BELT_RIGHT);
        if (left != null && !left.isEmpty()) renderItem(left, poseStack, collector, packedLight, player, state, true);
        if (right != null && !right.isEmpty()) renderItem(right, poseStack, collector, packedLight, player, state, false);
    }

    private void renderItem(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                            AbstractClientPlayer player, AvatarRenderState state, boolean left) {
        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        double side = 0.29D;
        poseStack.translate(side * (left ? 1.0D : -1.0D), 0.5D, 0.05D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.scale(1.5F, 1.5F, 1.5F);
        AccessoryRenderDefinition renderDefinition = AccessoryRenderDefinitions.belt(stack);
        renderDefinition.apply(poseStack, stack, left, false);
        CuriosIntegration.BeltHolder holder = CuriosIntegration.getBelt(stack.getItem());
        if (holder != null) holder.transformer().accept(poseStack, left);
        ItemStackRenderState itemState = new ItemStackRenderState();
        this.itemModelResolver.updateForLiving(itemState, stack, renderDefinition.displayContext(), player);
        itemState.submit(poseStack, collector, packedLight, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
        poseStack.popPose();
    }

    private static AbstractClientPlayer player(AvatarRenderState state) {
        if (Minecraft.getInstance().level == null) return null;
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        return entity instanceof AbstractClientPlayer player ? player : null;
    }
}
