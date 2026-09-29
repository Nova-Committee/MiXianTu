package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Draws one mount for one {@link MountRender} type. An addon registers its own pair - a verbatim
 * {@code MapCodec<T>} on the data side and this renderer on the client - and packs then name that type.
 *
 * <p>The pose stack handed to {@link #submit} already stands at the vehicle's origin and is turned by its yaw.
 * Pitch is <b>not</b> applied: a renderer applies it itself, because a declared display translation is authored
 * in the yaw-turned, un-pitched frame.
 *
 * <p>Client-side only: nothing on the server loads this interface.
 */
public interface MountRenderer<T extends MountRender> {
    MountRenderState createState();

    void extract(T definition, MountRenderContext context, MountRenderState state);

    void submit(T definition, MountRenderContext context, MountRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera);
}
