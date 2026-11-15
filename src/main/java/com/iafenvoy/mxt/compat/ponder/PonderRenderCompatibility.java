package com.iafenvoy.mxt.compat.ponder;

import net.createmod.catnip.api.client.gui.render.pip.GuiBlockEntityRenderState;
import net.createmod.catnip.api.client.gui.render.pip.GuiBlockModelRenderState;
import net.createmod.catnip.api.client.gui.render.pip.GuiFluidStateRenderState;
import net.createmod.catnip.api.client.platform.ModClientHooksHelper;
import net.createmod.catnip.impl.client.gui.element.pip.GuiBlockEntityRenderer;
import net.createmod.catnip.impl.client.gui.element.pip.GuiBlockModelRenderer;
import net.createmod.catnip.impl.client.gui.element.pip.GuiFluidStateRenderer;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderState;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

// Ponder/Catnip 1.0.52 queue PiP factories in client setup, after NeoForge has collected them.
// Remove this bridge when upstream registers before GameRenderer construction.
@EventBusSubscriber(Dist.CLIENT)
public final class PonderRenderCompatibility {
    private PonderRenderCompatibility() {
    }

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        event.enqueueWork(PonderRenderCompatibility::registerRenderers);
    }

    static void registerRenderers() {
        ModClientHooksHelper hooks = ModClientHooksHelper.INSTANCE;
        hooks.registerPictureInPictureRenderer(PonderSceneRenderState.class, PonderSceneRenderer::new);
        hooks.registerPictureInPictureRenderer(GuiBlockModelRenderState.class, GuiBlockModelRenderer::new);
        hooks.registerPictureInPictureRenderer(GuiBlockEntityRenderState.class, GuiBlockEntityRenderer::new);
        hooks.registerPictureInPictureRenderer(GuiFluidStateRenderState.class, GuiFluidStateRenderer::new);
    }
}
