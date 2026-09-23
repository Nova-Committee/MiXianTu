package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.FlightDisplay;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

/**
 * Draws the mount as the item model it carries, which is what lets one entity type serve every vehicle: the model, its
 * texture and the pose it lies in all come from the item, the resource pack and the artifact's declared display.
 */
public final class FlyingSwordRenderer extends EntityRenderer<FlyingSwordEntity, FlyingSwordRenderState> {
    // The model's lowest point sits on the entity origin, which is the bottom of the collision box, so the blade the
    // player sees is the box the game collides with by default. The seat that puts the rider on top is the entity
    // type's passenger attachment, and a definition can move the model from here with its display translation.
    private static final float MODEL_REST_HEIGHT = 0.0F;
    private final ItemModelResolver itemModelResolver;

    public FlyingSwordRenderer(Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.4F;
    }

    @Override
    public FlyingSwordRenderState createRenderState() {
        return new FlyingSwordRenderState();
    }

    @Override
    public void extractRenderState(FlyingSwordEntity entity, FlyingSwordRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        ItemStack visual = entity.visual();
        if (visual.isEmpty()) return;
        this.itemModelResolver.updateForTopItem(state.item, visual, ItemDisplayContext.FIXED, entity.level(), entity, entity.getId());
        state.display = ArtifactService.flight(entity.level().registryAccess(), visual)
                .map(FlightArtifactAbility::display).orElse(FlightDisplay.DEFAULT);
    }

    @Override
    public void submit(FlyingSwordRenderState state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        if (!state.item.isEmpty()) {
            FlightDisplay display = state.display;
            Vec3 translation = display.translation();
            Vec3 rotation = display.rotation();
            Vec3 scale = display.scale();
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
            // The body's own rotations are outside the declared ones, so a definition describes the model and never has
            // to know which way the mount happens to be facing. This offset is applied while the axes are still the
            // world's: after the declared rotation, a Y offset is no longer up. The turned model's lowest point is
            // -maxZ once its local Z is the vertical one, which is what the resting term puts on the resting plane.
            poseStack.translate(translation.x, MODEL_REST_HEIGHT + scale.z * (float) state.item.getModelBoundingBox().maxZ + translation.y, translation.z);
            poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
            poseStack.mulPose(new Quaternionf().rotationXYZ(radians(rotation.x), radians(rotation.y), radians(rotation.z)));
            poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    // A carried item model can be several blocks wide while the collision box is a fraction of that, so culling on the
    // box would pop the mount out of view while it is still on screen.
    @Override
    protected boolean affectedByCulling(FlyingSwordEntity entity) {
        return false;
    }
}
