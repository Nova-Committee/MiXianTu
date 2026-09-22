package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.render.screen.ConfigSelectScreen;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtHudConfig;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.render.animation.CultivationAnimationController;
import com.iafenvoy.mxt.screen.resourcebar.ResourceBarOverlay;
import com.iafenvoy.mxt.screen.wheel.WheelSelectionEntry;
import com.iafenvoy.mxt.screen.wheel.content.WheelContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(Dist.CLIENT)
public final class MiXianTuClient {
    @SubscribeEvent
    public static void init(FMLClientSetupEvent event) {
        ConfigManager.getInstance().registerConfigHandler(MxtClientConfig.INSTANCE);
        ConfigManager.getInstance().registerConfigHandler(MxtHudConfig.INSTANCE);
        CultivationAnimationController.register();
        // Registered here rather than on the first frame the framework draws, so the layout editor shows the
        // resource-bar elements from the main menu as well as from inside a world.
        ResourceBarOverlay.registerEntries();
        // The provider is published before the wheel can be opened: the contents are the player's own sectors.
        WheelContent.register();
        // The use key works with the wheel closed, so this cell is what keeps that from being a blind cast.
        WheelSelectionEntry.register();
        event.getContainer().registerExtensionPoint(IConfigScreenFactory.class, (_, parent) -> ConfigSelectScreen.builder("config.mxt.title", parent).client(MxtClientConfig.INSTANCE).client(MxtHudConfig.INSTANCE).server(MxtServerConfig.INSTANCE).build());
    }
}
