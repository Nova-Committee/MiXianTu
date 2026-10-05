package com.iafenvoy.mxt.render.sword;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtSwordAuraRenderTypes {
    private static final RenderPipeline SWORD_AURA = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pipeline/sword_aura"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/sword_aura"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/sword_aura"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final RenderPipeline SWORD_BLADE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pipeline/sword_blade"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/sword_blade"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "core/sword_blade"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    @Nullable
    private static RenderType bladeType;
    @Nullable
    private static RenderType shaderType;
    private MxtSwordAuraRenderTypes() {
    }

    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SWORD_BLADE);
        event.registerPipeline(SWORD_AURA);
    }

    public static RenderType swordBlade() {
        if (bladeType == null) {
            bladeType = RenderType.create("mxt_sword_blade", RenderSetup.builder(SWORD_BLADE).createRenderSetup());
        }
        return bladeType;
    }

    public static RenderType swordAura() {
        if (shaderType == null) {
            shaderType = RenderType.create("mxt_sword_aura", RenderSetup.builder(SWORD_AURA)
                    .withTexture("Sampler0", Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/entity/sword_aura.png"))
                    .createRenderSetup());
        }
        return shaderType;
    }
}
