package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.render.StationBlockEntityRenderer;
import com.iafenvoy.mxt.screen.gui.ChequeTableScreen;
import com.iafenvoy.mxt.screen.gui.ExchangeStationScreen;
import com.iafenvoy.mxt.screen.gui.PlayerTradeScreen;
import com.iafenvoy.mxt.screen.gui.StationScreen;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtRenderers {
    @SubscribeEvent
    public static void register(RegisterRenderers event) {
        event.registerEntityRenderer(MxtEntityTypes.FLYING_SWORD.get(), NoopRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SOUL.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(MxtMenus.CHEQUE_TABLE.get(), ChequeTableScreen::new);
        event.register(MxtMenus.EXCHANGE_STATION.get(), ExchangeStationScreen::new);
        event.register(MxtMenus.SYSTEM_STATION_OWNER.get(), StationScreen::new);
        event.register(MxtMenus.SYSTEM_STATION_CUSTOMER.get(), StationScreen::new);
        event.register(MxtMenus.TRADE_STATION_OWNER.get(), StationScreen::new);
        event.register(MxtMenus.TRADE_STATION_CUSTOMER.get(), StationScreen::new);
        event.register(MxtMenus.PLAYER_TRADE.get(), PlayerTradeScreen::new);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(RegisterRenderers event) {
        event.registerBlockEntityRenderer(MxtBlockEntities.TRADE_STATION.get(), StationBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.SYSTEM_STATION.get(), StationBlockEntityRenderer::new);
    }
}
