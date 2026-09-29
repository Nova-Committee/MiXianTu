package com.iafenvoy.mxt.compat.geckolib;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * The three assets a definition names. GeckoLib's own resource reloader bakes everything under
 * {@code assets/<namespace>/geckolib/}, so the ids are passed through as written.
 */
final class MountGeoModel extends GeoModel<MountAnimatable> {
    private final GeckoLibMountRender definition;

    MountGeoModel(GeckoLibMountRender definition) {
        this.definition = definition;
    }

    @Override
    public @NonNull Identifier getModelResource(@NonNull GeoRenderState renderState) {
        return this.definition.model();
    }

    @Override
    public @NonNull Identifier getTextureResource(@NonNull GeoRenderState renderState) {
        return this.definition.texture();
    }

    @Override
    public @NonNull Identifier getAnimationResource(MountAnimatable animatable) {
        // Only asked when an animation is played, and no controller exists unless the definition names a file.
        return this.definition.animations().orElse(this.definition.model());
    }
}
