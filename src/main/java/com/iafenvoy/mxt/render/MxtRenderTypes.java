package com.iafenvoy.mxt.render;

import net.minecraft.client.renderer.rendertype.*;

public final class MxtRenderTypes {
    private static final RenderType SWORD_BLADE = RenderType.create("mxt_sword_blade", RenderSetup.builder(MxtRenderPipelines.SWORD_BLADE).createRenderSetup());
    private static final RenderType RIFT_FALLBACK = RenderType.create("mxt_rift_fallback", RenderSetup.builder(MxtRenderPipelines.RIFT_FALLBACK).createRenderSetup());
    private static final RenderType MULTIBLOCK_OUTLINE = RenderType.create("mxt_multiblock_outline", RenderSetup.builder(MxtRenderPipelines.MULTIBLOCK_OUTLINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

    private MxtRenderTypes() {
    }

    public static RenderType swordBlade() {
        return SWORD_BLADE;
    }

    public static RenderType riftFallback() { return RIFT_FALLBACK; }

    public static RenderType multiblockOutline() {
        return MULTIBLOCK_OUTLINE;
    }
}
