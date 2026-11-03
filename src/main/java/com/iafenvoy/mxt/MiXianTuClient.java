package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.render.screen.ConfigSelectScreen;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.render.animation.CultivationAnimationController;
import com.iafenvoy.mxt.screen.profiling.ProfilerOpenScreenData;
import com.iafenvoy.mxt.screen.profiling.ProfilerScreen;
import com.iafenvoy.mxt.screen.resourcebar.AuraZoneBarContributor;
import com.iafenvoy.mxt.screen.wheel.WheelSelectionEntry;
import com.iafenvoy.mxt.screen.wheel.content.WheelContent;
import com.iafenvoy.saturn.screen.opener.ClientScreenDispatcher;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarContributors;
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
        event.getContainer().registerExtensionPoint(IConfigScreenFactory.class, (_, parent) -> ConfigSelectScreen.builder("config.mxt.title", parent).client(MxtClientConfig.INSTANCE).server(MxtServerConfig.INSTANCE).build());

        CultivationAnimationController.register();
        // The resource-bar HUD entries are the library's; the aura bars hang off the zone, so they are contributed.
        ResourceBarContributors.register(AuraZoneBarContributor.INSTANCE);
        WheelContent.register();
        WheelSelectionEntry.register();
        ClientScreenDispatcher.register(ProfilerOpenScreenData.STREAM_CODEC, ProfilerScreen::create);
    }
}
