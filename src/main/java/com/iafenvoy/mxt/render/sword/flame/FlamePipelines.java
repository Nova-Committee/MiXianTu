package com.iafenvoy.mxt.render.sword.flame;

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
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

@EventBusSubscriber(Dist.CLIENT)
public final class FlamePipelines {
    private static final RenderPipeline.Snippet PROJECTION_ONLY = RenderPipeline.builder()
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    public static final RenderPipeline FLUID = base("sword_fluid")
            .withSampler("FluidField")
            .withDepthStencilState(Optional.empty())
            .build();
    public static final RenderPipeline SURFACE = world("sword_flame_surface").build();
    public static final RenderPipeline PARTICLES = world("sword_flame_particle").build();

    private FlamePipelines() {
    }

    private static RenderPipeline.Builder base(String name) {
        return RenderPipeline.builder(PROJECTION_ONLY)
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/" + name))
                .withFragmentShader(id("core/" + name))
                .withUniform("FlameFrame", UniformType.UNIFORM_BUFFER)
                .withUniform("FlameItems", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .withCull(false);
    }

    private static RenderPipeline.Builder world(String name) {
        return base(name)
                .withSampler("FluidField")
                .withSampler("SceneDepth")
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withColorTargetState(new ColorTargetState(Optional.of(new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE)), ColorTargetState.WRITE_COLOR));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(FLUID);
        event.registerPipeline(SURFACE);
        event.registerPipeline(PARTICLES);
    }
}
