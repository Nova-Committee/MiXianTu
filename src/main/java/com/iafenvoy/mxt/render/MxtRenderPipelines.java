package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtRenderPipelines {
    private static final RenderPipeline.Snippet PROJECTION_ONLY = RenderPipeline.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).buildSnippet();
    public static final RenderPipeline SWORD_BLADE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(id("pipeline/sword_blade"))
            .withVertexShader(id("core/sword_blade"))
            .withFragmentShader(id("core/sword_blade"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();
    public static final RenderPipeline RIFT = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(id("pipeline/rift"))
            .withVertexShader(id("core/rift"))
            .withFragmentShader(id("core/rift"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();
    public static final RenderPipeline SWORD_FLUID = flameBase("sword_fluid")
            .withSampler("FluidField")
            .withDepthStencilState(Optional.empty())
            .build();
    public static final RenderPipeline SWORD_FLAME_SURFACE = flameWorld("sword_flame_surface").build();
    public static final RenderPipeline SWORD_FLAME_PARTICLE = flameWorld("sword_flame_particle").build();
    public static final RenderPipeline METEOR_FLAME = flameWorld("meteor_flame").build();
    public static final RenderPipeline FLAME_COMPOSITE = RenderPipeline.builder(PROJECTION_ONLY)
            .withLocation(id("pipeline/flame_composite"))
            .withVertexShader(id("core/flame_composite"))
            .withFragmentShader(id("core/flame_composite"))
            .withUniform("FlameFrame", UniformType.UNIFORM_BUFFER)
            .withSampler("FlameColor").withSampler("SceneDepth")
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withDepthStencilState(Optional.empty()).withCull(false)
            .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(SourceFactor.ONE, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE)), ColorTargetState.WRITE_COLOR))
            .build();

    public static final RenderPipeline LIGHT_BEAM = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(id("pipeline/light_beam"))
            .withVertexShader(id("core/light_beam"))
            .withFragmentShader(id("core/light_beam"))
            .withUniform("LightBeamInstances", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(true).build();
    public static final RenderPipeline LIGHT_BEAM_DEPTH = lightBeamDepth("light_beam_depth", CompareOp.LESS_THAN_OR_EQUAL).build();
    public static final RenderPipeline LIGHT_BEAM_GLOW_DEPTH = lightBeamDepth("light_beam_glow_depth", CompareOp.ALWAYS_PASS).build();
    // Depth test off: a depth-tested outline only reads as a box for the faces pointing at the camera.
    public static final RenderPipeline MULTIBLOCK_OUTLINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(id("pipeline/multiblock_outline"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static RenderPipeline.Builder flameBase(String name) {
        return RenderPipeline.builder(PROJECTION_ONLY)
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/" + name))
                .withFragmentShader(id("core/" + name))
                .withUniform("FlameFrame", UniformType.UNIFORM_BUFFER)
                .withUniform("FlameItems", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .withCull(false);
    }

    private static RenderPipeline.Builder flameWorld(String name) {
        return flameBase(name)
                .withSampler("FluidField")
                .withSampler("SceneDepth")
                // Flame targets have no depth attachment; shaders test the full-resolution scene snapshot.
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE)), ColorTargetState.WRITE_COLOR));
    }

    private static RenderPipeline.Builder lightBeamDepth(String name, CompareOp depth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/light_beam"))
                .withFragmentShader(id("core/light_beam"))
                .withShaderDefine("MXT_SCENE_DEPTH")
                .withUniform("LightBeamInstances", UniformType.UNIFORM_BUFFER)
                .withUniform("BeamDraw", UniformType.UNIFORM_BUFFER)
                .withSampler("SceneDepth")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depth, false))
                .withCull(true);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SWORD_BLADE);
        event.registerPipeline(RIFT);
        event.registerPipeline(SWORD_FLUID);
        event.registerPipeline(SWORD_FLAME_SURFACE);
        event.registerPipeline(SWORD_FLAME_PARTICLE);
        event.registerPipeline(METEOR_FLAME);
        event.registerPipeline(FLAME_COMPOSITE);
        event.registerPipeline(LIGHT_BEAM);
        event.registerPipeline(LIGHT_BEAM_DEPTH);
        event.registerPipeline(LIGHT_BEAM_GLOW_DEPTH);
        event.registerPipeline(MULTIBLOCK_OUTLINE);
    }
}
