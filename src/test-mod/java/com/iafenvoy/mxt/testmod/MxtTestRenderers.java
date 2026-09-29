package com.iafenvoy.mxt.testmod;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

/**
 * The probe beast and the probe mount are drawn as nothing on purpose: they are asserted on by numbers and command
 * output, and a no-op renderer cannot fail on a variant, a model or a texture that the test mod does not ship. A
 * renderer per entity type is also what an addon has to register for the vehicle type it names in a definition.
 */
@EventBusSubscriber(modid = MxtTestMod.MOD_ID, value = Dist.CLIENT)
public final class MxtTestRenderers {
    @SubscribeEvent
    public static void registerRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(MxtTestEntities.PROBE_BEAST.get(), NoopRenderer::new);
        event.registerEntityRenderer(MxtTestEntities.PROBE_MOUNT.get(), NoopRenderer::new);
    }
}
