package com.iafenvoy.mxt.compat.geckolib;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.GeoObjectRenderer;
import org.jspecify.annotations.Nullable;

/**
 * One definition's model plus its controller state. GeckoLib's object renderer is the right base here because the
 * vehicle is not a {@code GeoAnimatable} itself - it is a plain entity type that must load without GeckoLib - so the
 * animatable is this renderer's own singleton and each vehicle is told apart by its instance id.
 */
final class MountGeoRenderer extends GeoObjectRenderer<MountAnimatable, MountRenderContext, GeoRenderState> {
    private final MountAnimatable animatable;

    MountGeoRenderer(GeckoLibMountRender definition) {
        super(new MountGeoModel(definition));
        this.animatable = new MountAnimatable(definition);
    }

    MountAnimatable animatable() {
        return this.animatable;
    }

    // Per vehicle, not per definition: two mounts in the air at once must not share one animation frame.
    @Override
    public long getInstanceId(MountAnimatable animatable, @Nullable MountRenderContext context) {
        return context == null ? animatable.hashCode() : context.vehicle().getId();
    }

    @Override
    public void addRenderData(MountAnimatable animatable, @Nullable MountRenderContext context, GeoRenderState renderState, float partialTick) {
        if (context == null) return;
        renderState.addGeckolibData(MountAnimatable.MOTION, context.motion());
        renderState.addGeckolibData(MountAnimatable.CREW, context.crew());
    }

    // The base centres a model on a block and the caller has already applied the declared display, so this adds
    // nothing: a mount is drawn from its own origin.
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
    }
}
