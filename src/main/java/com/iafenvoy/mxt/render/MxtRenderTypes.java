package com.iafenvoy.mxt.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class MxtRenderTypes {
    private static final RenderType SWORD_BLADE = RenderType.create("mxt_sword_blade", RenderSetup.builder(MxtRenderPipelines.SWORD_BLADE).createRenderSetup());
    private static final RenderType RIFT_FALLBACK = RenderType.create("mxt_rift_fallback", RenderSetup.builder(MxtRenderPipelines.RIFT_FALLBACK).createRenderSetup());

    private MxtRenderTypes() {
    }

    public static RenderType swordBlade() {
        return SWORD_BLADE;
    }

    public static RenderType riftFallback() {
        return RIFT_FALLBACK;
    }

}
