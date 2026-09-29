package com.iafenvoy.mxt.render.mount;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.api.MountRenderState;
import com.iafenvoy.mxt.api.MountRenderer;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

/**
 * The default look, unchanged from what the mount always did: the item model the vehicle carries, posed by the
 * artifact's declared display. The model, its texture and the pose it lies in all come from the item, the resource
 * pack and the definition, which is what lets one entity type serve every vehicle.
 */
public final class ItemMountRenderer implements MountRenderer<ItemMountRender> {
    // The model's lowest point sits on the entity origin, which is the bottom of the collision box, so the blade the
    // player sees is the box the game collides with by default. The seat that puts the rider on top is the entity
    // type's passenger attachment, and a definition can move the model from here with its display translation.
    private static final float MODEL_REST_HEIGHT = 0.0F;

    @Override
    public MountRenderState createState() {
        return new State();
    }

    @Override
    public void extract(ItemMountRender definition, MountRenderContext context, MountRenderState state) {
        State item = (State) state;
        ItemStack visual = context.visual();
        if (visual.isEmpty()) return;
        // Asked of the client rather than kept from construction: this runs while rendering, and the resolver is
        // only needed then.
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item.item, visual, ItemDisplayContext.FIXED,
                context.vehicle().level(), context.vehicle(), context.vehicle().getId());
    }

    @Override
    public void submit(ItemMountRender definition, MountRenderContext context, MountRenderState state,
                       @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        State item = (State) state;
        if (item.item.isEmpty()) return;
        FlightDisplay display = context.display().orElse(FlightDisplay.DEFAULT);
        Vec3 translation = display.translation();
        Vec3 rotation = display.rotation();
        Vec3 scale = display.scale();
        poseStack.pushPose();
        // The pitch belongs to this renderer, and the offset below is applied while the axes are still the world's:
        // after the declared rotation a Y offset is no longer up. The resting term is what puts the model on the box.
        poseStack.translate(translation.x, MODEL_REST_HEIGHT + scale.z * (float) item.item.getModelBoundingBox().maxZ + translation.y, translation.z);
        poseStack.mulPose(Axis.XP.rotationDegrees(context.xRot()));
        poseStack.mulPose(new Quaternionf().rotationXYZ(radians(rotation.x), radians(rotation.y), radians(rotation.z)));
        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        item.item.submit(poseStack, collector, context.lightCoords(), OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static final class State implements MountRenderState {
        private final ItemStackRenderState item = new ItemStackRenderState();
    }
}
