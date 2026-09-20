package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The pipeline and render type a rift is drawn with.
 *
 * <p>The pipeline is the rift's own: it takes position-coloured quads like a plain debug surface, but its
 * fragment shader samples the mod's rift texture nine times through the projected position with a different
 * rotation and scroll per layer, which is what gives the points, links and fills their drifting kaleidoscope
 * look. Colour still arrives per vertex, so one pipeline draws every rift whatever colour it is.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class MxtRiftRenderTypes {
    public static final RenderPipeline RIFT = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pipeline/rift"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/rift"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/rift"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    @Nullable
    private static RenderType shaderType;
    @Nullable
    private static RenderType plainType;

    private MxtRiftRenderTypes() {
    }

    /**
     * Registered on the mod event bus, which is the only point at which a custom pipeline may join the list.
     */
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(RIFT);
    }

    /**
     * The render type for a rift. Both variants accept exactly the same position-colour quads, so the client
     * config can fall back to a plain translucent drawing without the renderer changing at all.
     */
    public static RenderType rift() {
        if (!MxtClientConfig.INSTANCE.rifts.shaders.getValue()) {
            if (plainType == null) plainType = RenderTypes.debugQuads();
            return plainType;
        }
        if (shaderType == null) {
            shaderType = RenderType.create("mxt_rift", RenderSetup.builder(RIFT)
                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/entity/rift.png"))
                    .createRenderSetup());
        }
        return shaderType;
    }
}
