package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.render.screen.ConfigSelectScreen;
import com.iafenvoy.mxt.config.MxtServerConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(Dist.CLIENT)
public final class MiXianTuClient {
    @SubscribeEvent
    public static void init(FMLClientSetupEvent event) {
        event.getContainer().registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> ConfigSelectScreen.builder("config.mxt.title", parent).server(MxtServerConfig.INSTANCE).build());
    }
}
