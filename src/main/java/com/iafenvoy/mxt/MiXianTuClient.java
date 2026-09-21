package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.render.screen.ConfigSelectScreen;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtHudConfig;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.render.animation.CultivationAnimationController;
import com.iafenvoy.mxt.screen.overlay.resourcebar.ResourceBarOverlay;
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
        // The resource-bar elements - two movable columns plus two fixed rows about the entity under the
        // crosshair - are HUD elements before they are anything else. Registering them here, rather than on the
        // first frame the framework draws, is what lets the layout editor show them from the main menu as well
        // as from inside a world.
        ResourceBarOverlay.registerEntries();
        // The wheel's contents are the player's own twelve sectors, so the provider is published before the
        // wheel can be opened.
        WheelContent.register();
        // The cell showing which sector the use key would spend: the use key works with the wheel closed, and
        // this is what keeps that from being a blind cast.
        WheelSelectionEntry.register();
        event.getContainer().registerExtensionPoint(IConfigScreenFactory.class, (_, parent) -> ConfigSelectScreen.builder("config.mxt.title", parent).client(MxtClientConfig.INSTANCE).client(MxtHudConfig.INSTANCE).server(MxtServerConfig.INSTANCE).build());
    }
}
