package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.render.StationBlockEntityRenderer;
import com.iafenvoy.mxt.render.DisplayStandBlockEntityRenderer;
import com.iafenvoy.mxt.render.accessory.BackWeaponRenderer;
import com.iafenvoy.mxt.render.accessory.BeltWeaponRenderer;
import com.iafenvoy.mxt.render.particle.SpiritWispParticle.Provider;
import com.iafenvoy.mxt.screen.gui.ChequeTableScreen;
import com.iafenvoy.mxt.screen.gui.ExchangeStationScreen;
import com.iafenvoy.mxt.screen.gui.PlayerTradeScreen;
import com.iafenvoy.mxt.screen.gui.StationScreen;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtRenderers {
    @SubscribeEvent
    public static void registerRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(MxtEntityTypes.FLYING_SWORD.get(), NoopRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SOUL.get(), NoopRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SPIRIT_BURST.get(), NoopRenderer::new);

        event.registerBlockEntityRenderer(MxtBlockEntities.TRADE_STATION.get(), StationBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.SYSTEM_STATION.get(), StationBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.DISPLAY_STAND.get(), DisplayStandBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MxtParticleTypes.SPIRIT_WISP.get(), Provider::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MxtMenus.CHEQUE_TABLE.get(), ChequeTableScreen::new);
        event.register(MxtMenus.EXCHANGE_STATION.get(), ExchangeStationScreen::new);
        event.register(MxtMenus.SYSTEM_STATION_OWNER.get(), StationScreen::new);
        event.register(MxtMenus.SYSTEM_STATION_CUSTOMER.get(), StationScreen::new);
        event.register(MxtMenus.TRADE_STATION_OWNER.get(), StationScreen::new);
        event.register(MxtMenus.TRADE_STATION_CUSTOMER.get(), StationScreen::new);
        event.register(MxtMenus.PLAYER_TRADE.get(), PlayerTradeScreen::new);
    }

    /**
     * Adds Curios back and belt weapon layers to both vanilla player model variants.
     */
    @SubscribeEvent
    public static void addPlayerLayers(AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer == null) continue;
            renderer.addLayer(new BackWeaponRenderer(renderer, event.getContext().getItemModelResolver()));
            renderer.addLayer(new BeltWeaponRenderer(renderer, event.getContext().getItemModelResolver()));
        }
    }
}
