package com.iafenvoy.mxt.render.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import org.jspecify.annotations.Nullable;

// prepare runs outside a pass; render may only bind resources and submit geometry/composites.
public interface EffectDraw {
    default boolean needsDepth() {
        return false;
    }

    @Nullable Prepared prepare(WorldEffects.Frame frame, RenderTarget target);

    interface Prepared extends AutoCloseable {
        void render(RenderPass pass);

        @Override
        default void close() {
        }
    }
}
