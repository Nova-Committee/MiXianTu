package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.api.MountRenderer;
import com.iafenvoy.mxt.api.MountRenderState;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * What the dispatcher hands from extraction to submission: the body's yaw, and whichever renderer the definition
 * named together with the one frame of context and scratch it was given.
 */
public class FlyingSwordRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public @Nullable MountRender definition;
    public @Nullable MountRenderer<?> renderer;
    public @Nullable MountRenderState rendererState;
    public @Nullable MountRenderContext context;
}
