package com.iafenvoy.mxt.testmod;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

/**
 * The probe beast is drawn as nothing on purpose: it is asserted on by numbers and command output, and a
 * no-op renderer cannot fail on a variant, a model or a texture that the test mod does not ship.
 */
@EventBusSubscriber(modid = MxtTestMod.MOD_ID, value = Dist.CLIENT)
public final class MxtTestRenderers {
    @SubscribeEvent
    public static void registerRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(MxtTestEntities.PROBE_BEAST.get(), NoopRenderer::new);
    }
}
