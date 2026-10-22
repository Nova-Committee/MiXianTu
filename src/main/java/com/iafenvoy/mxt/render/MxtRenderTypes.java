package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtClientConfig;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;

public final class MxtRenderTypes {
    private static final RenderType SWORD_BLADE = RenderType.create("mxt_sword_blade", RenderSetup.builder(MxtRenderPipelines.SWORD_BLADE).createRenderSetup());
    private static final RenderType RIFT_SHADER = RenderType.create("mxt_rift", RenderSetup.builder(MxtRenderPipelines.RIFT)
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/entity/rift.png"))
            .createRenderSetup());
    private static final RenderType MULTIBLOCK_OUTLINE = RenderType.create("mxt_multiblock_outline", RenderSetup.builder(MxtRenderPipelines.MULTIBLOCK_OUTLINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

    private MxtRenderTypes() {
    }

    public static RenderType swordBlade() {
        return SWORD_BLADE;
    }

    public static RenderType rift() {
        return MxtClientConfig.INSTANCE.rifts.shaders.getValue() ? RIFT_SHADER : RenderTypes.debugQuads();
    }

    public static RenderType multiblockOutline() {
        return MULTIBLOCK_OUTLINE;
    }
}
