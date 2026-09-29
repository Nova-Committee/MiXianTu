package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.api.MountRenderer;
import com.iafenvoy.mxt.api.MountRenderState;
import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.iafenvoy.mxt.data.ability.type.FlightDisplay;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.render.mount.MountContext;
import com.iafenvoy.mxt.render.mount.MountRenderers;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * The one renderer the entity type is registered with, and therefore the only place a mount's look can be decided:
 * vanilla maps one entity type to one renderer, so "a model per artifact" has to be a choice made inside this class.
 *
 * <p>It poses the body and picks a renderer out of the definition; the drawing itself belongs to that renderer, so
 * the default look, a GeckoLib model and an addon's own renderer are three registrations rather than three code
 * paths here.
 */
public final class FlyingSwordRenderer extends EntityRenderer<FlyingSwordEntity, FlyingSwordRenderState> {
    public FlyingSwordRenderer(Context context) {
        super(context);
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
        // The definition is cached on the entity by the stack it carries, and light comes from the renderer: the
        // render state's own field is only filled in after this method returns.
        MountAbilityType mount = entity.mountDefinition().orElse(null);
        MountRender definition = mount == null ? ItemMountRender.INSTANCE : mount.render();
        Optional<FlightDisplay> display = mount == null ? Optional.empty() : mount.display();
        // Position, not rotation: the server owns the movement and the client interpolates towards it, so a driver
        // looking around must not read as flying (see MountPose).
        Vec3 moved = entity.position().subtract(entity.oldPosition());
        int riders = entity.getPassengers().size();
        state.context = new MountContext(visual, entity, display, state.yRot, state.xRot, state.partialTick,
                this.getPackedLightCoords(entity, partialTicks),
                MountPose.motion(moved.horizontalDistance(), moved.y), MountPose.crew(riders), riders);
        MountRenderer<MountRender> renderer = MountRenderers.resolve(definition);
        state.definition = definition;
        state.renderer = renderer;
        state.rendererState = renderer.createState();
        renderer.extract(definition, state.context, state.rendererState);
    }

    @Override
    public void submit(FlyingSwordRenderState state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        MountRenderer<?> renderer = state.renderer;
        MountRenderContext context = state.context;
        MountRenderState scratch = state.rendererState;
        MountRender definition = state.definition;
        if (renderer != null && context != null && scratch != null && definition != null) {
            poseStack.pushPose();
            // Only the yaw: a declared display is authored in the yaw-turned frame, so the pitch belongs to the
            // renderer together with everything inside it.
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
            submit(renderer, definition, context, scratch, poseStack, collector, camera);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    @SuppressWarnings("unchecked")
    private static <T extends MountRender> void submit(MountRenderer<T> renderer, MountRender definition,
                                                       MountRenderContext context, MountRenderState state,
                                                       PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        renderer.submit((T) definition, context, state, poseStack, collector, camera);
    }

    // A carried item model can be several blocks wide while the collision box is a fraction of that, so culling on the
    // box would pop the mount out of view while it is still on screen.
    @Override
    protected boolean affectedByCulling(FlyingSwordEntity entity) {
        return false;
    }
}
