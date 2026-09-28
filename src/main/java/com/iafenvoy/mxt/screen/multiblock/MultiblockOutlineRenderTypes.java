package com.iafenvoy.mxt.screen.multiblock;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The line pipeline the hover outline is drawn with: the vanilla one with its depth test off. A depth-tested outline
 * only reads as a box for the faces pointing at the camera - the block it belongs to hides the rest of it.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class MultiblockOutlineRenderTypes {
    private static final RenderPipeline OUTLINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "pipeline/multiblock_outline"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static RenderType type;

    private MultiblockOutlineRenderTypes() {
    }

    // A pipeline may only join the list here, on the mod event bus.
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(OUTLINE);
    }

    // Built on first use rather than up front, since a render type needs its pipeline to be registered first.
    public static RenderType outline() {
        if (type == null) {
            type = RenderType.create(MiXianTu.MOD_ID + "_multiblock_outline",
                    RenderSetup.builder(OUTLINE)
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());
        }
        return type;
    }
}
