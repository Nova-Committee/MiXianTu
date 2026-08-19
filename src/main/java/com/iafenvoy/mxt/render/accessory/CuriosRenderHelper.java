package com.iafenvoy.mxt.render.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Client-side model transforms shared by Curios accessory layers.
 */
public final class CuriosRenderHelper {
    private CuriosRenderHelper() {
    }

    public static void translateToChest(PoseStack poseStack, PlayerModel model, AbstractClientPlayer player) {
        if (player.isCrouching() && !player.isPassenger() && !player.isSwimming()) {
            poseStack.translate(0.0F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.XP.rotation(model.body.xRot));
        }
        poseStack.mulPose(Axis.YP.rotation(model.body.yRot));
        poseStack.translate(0.0F, 0.4F, -0.16F);
    }
}
