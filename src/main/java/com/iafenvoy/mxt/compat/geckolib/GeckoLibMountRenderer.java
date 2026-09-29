package com.iafenvoy.mxt.compat.geckolib;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.api.MountRenderer;
import com.iafenvoy.mxt.api.MountRenderState;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a mount from a GeckoLib model. The pose order is the same the item renderer uses, so a declared display
 * means the same thing in both: it is applied after the yaw, before the pitch, in world axes.
 *
 * <p>GeckoLib wants one renderer per model, and two mounts naming the same assets can share one, so they are cached
 * by definition. A definition belongs to the pack, so this map never grows past what the pack declares.
 */
public final class GeckoLibMountRenderer implements MountRenderer<GeckoLibMountRender> {
    private final Map<GeckoLibMountRender, MountGeoRenderer> renderers = new ConcurrentHashMap<>();

    @Override
    public MountRenderState createState() {
        // GeckoLib builds its own render state inside performRenderPass, out of the context it is handed there.
        return MountRenderState.EMPTY;
    }

    @Override
    public void extract(GeckoLibMountRender definition, MountRenderContext context, MountRenderState state) {
        // Nothing to capture: the animation inputs travel in the context each frame.
    }

    @Override
    public void submit(GeckoLibMountRender definition, MountRenderContext context, MountRenderState state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        MountGeoRenderer renderer = this.renderers.computeIfAbsent(definition, MountGeoRenderer::new);
        FlightDisplay display = context.display().orElse(FlightDisplay.IDENTITY);
        Vec3 translation = display.translation();
        Vec3 rotation = display.rotation();
        Vec3 scale = display.scale();
        poseStack.pushPose();
        poseStack.translate(translation.x, translation.y, translation.z);
        poseStack.mulPose(Axis.XP.rotationDegrees(context.xRot()));
        poseStack.mulPose(new Quaternionf().rotationXYZ(radians(rotation.x), radians(rotation.y), radians(rotation.z)));
        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        renderer.performRenderPass(renderer.animatable(), context, poseStack, collector, camera, context.lightCoords(), context.partialTick());
        poseStack.popPose();
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
