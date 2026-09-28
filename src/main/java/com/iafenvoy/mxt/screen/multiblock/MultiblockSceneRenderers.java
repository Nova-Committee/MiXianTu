package com.iafenvoy.mxt.screen.multiblock;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class MultiblockSceneRenderers {
    private MultiblockSceneRenderers() {
    }

    @SubscribeEvent
    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(MultiblockSceneRenderState.class, MultiblockSceneRenderer::new);
    }
}
