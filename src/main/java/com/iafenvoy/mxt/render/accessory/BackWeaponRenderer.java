package com.iafenvoy.mxt.render.accessory;

import com.iafenvoy.mxt.integration.CuriosIntegration;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class BackWeaponRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ItemModelResolver itemModelResolver;

    public BackWeaponRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver itemModelResolver) {
        super(parent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(@NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector, int packedLight,
                       @NotNull AvatarRenderState state, float yRot, float xRot) {
        AbstractClientPlayer player = player(state);
        if (player == null || player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) return;
        Map<CuriosIntegration.Place, ItemStack> stacks = CuriosIntegration.equippedForCosmetic(player);
        ItemStack left = stacks.get(CuriosIntegration.Place.BACK_LEFT);
        ItemStack right = stacks.get(CuriosIntegration.Place.BACK_RIGHT);
        if (left != null && !left.isEmpty()) renderItem(left, poseStack, collector, packedLight, player, state, true);
        if (right != null && !right.isEmpty()) renderItem(right, poseStack, collector, packedLight, player, state, false);
    }

    private void renderItem(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                            AbstractClientPlayer player, AvatarRenderState state, boolean left) {
        poseStack.pushPose();
        CuriosRenderHelper.translateToChest(poseStack, this.getParentModel(), player);
        poseStack.translate(0.0D, 0.0D, 0.3D);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) poseStack.translate(0.0D, 0.0D, 0.05D);
        if (left) poseStack.translate(0.0D, 0.0D, 0.05D);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        if (left) poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(145.0F));
        poseStack.translate(0.0D, -0.2D, 0.1D);
        AccessoryRenderDefinition renderDefinition = AccessoryRenderDefinitions.back(stack);
        renderDefinition.apply(poseStack, stack, left, true);
        CuriosIntegration.BackHolder holder = CuriosIntegration.getBack(stack.getItem());
        if (holder != null) holder.transformer().accept(poseStack, left);
        this.submitItem(stack, renderDefinition, poseStack, collector, packedLight, player, state);
        poseStack.popPose();
    }

    private void submitItem(ItemStack stack, AccessoryRenderDefinition renderDefinition, PoseStack poseStack,
                             SubmitNodeCollector collector, int packedLight,
                             AbstractClientPlayer player, AvatarRenderState state) {
        ItemStackRenderState itemState = new ItemStackRenderState();
        this.itemModelResolver.updateForLiving(itemState, stack, renderDefinition.displayContext(), player);
        itemState.submit(poseStack, collector, packedLight, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
    }

    private static AbstractClientPlayer player(AvatarRenderState state) {
        if (Minecraft.getInstance().level == null) return null;
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        return entity instanceof AbstractClientPlayer player ? player : null;
    }
}
