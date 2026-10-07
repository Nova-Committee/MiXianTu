package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.compat.geckolib.GeckoLibCompat;
import com.iafenvoy.mxt.compat.geckolib.GeckoLibMountRenderers;
import com.iafenvoy.mxt.render.*;
import com.iafenvoy.mxt.render.accessory.BackWeaponRenderer;
import com.iafenvoy.mxt.render.accessory.BeltWeaponRenderer;
import com.iafenvoy.mxt.render.cultivation.CultivationItemRenderer;
import com.iafenvoy.mxt.render.lightning.ColoredLightningBoltRenderer;
import com.iafenvoy.mxt.render.mount.MountRenderers;
import com.iafenvoy.mxt.render.particle.RiftParticle.Provider;
import com.iafenvoy.mxt.render.particle.SpiritWispParticle;
import com.iafenvoy.mxt.render.sword.SwordAuraRenderer;
import com.iafenvoy.mxt.render.talisman.TalismanProjectileRenderer;
import com.iafenvoy.mxt.screen.gui.*;
import com.iafenvoy.mxt.screen.menu.*;
import net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.inventory.MenuType;
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
        event.registerEntityRenderer(MxtEntityTypes.FLYING_SWORD.get(), FlyingSwordRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SOUL.get(), NoopRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SPIRIT_BURST.get(), NoopRenderer::new);
        // A thrown carrier is drawn as the item itself, turning over in flight at its own random rate.
        event.registerEntityRenderer(MxtEntityTypes.TALISMAN_PROJECTILE.get(), TalismanProjectileRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.COLORED_LIGHTNING.get(), ColoredLightningBoltRenderer::new);
        event.registerEntityRenderer(MxtEntityTypes.SWORD_AURA.get(), SwordAuraRenderer::new);
        // The mount's own renderer never draws a mount by itself: it asks this registry, so the default look and the
        // optional GeckoLib one are two registrations rather than two code paths.
        MountRenderers.registerBuiltins();
        // The GeckoLib half is reached only from inside the guard: one com.geckolib type in this class would stop
        // every client without the mod from starting.
        if (GeckoLibCompat.loaded()) GeckoLibMountRenderers.register();

        event.registerBlockEntityRenderer(MxtBlockEntities.TRADE_STATION.get(), StationBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.SYSTEM_STATION.get(), StationBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.DISPLAY_STAND.get(), DisplayStandBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.RIFT.get(), RiftBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MxtBlockEntities.SPIRIT_HERB_PLOT.get(), SpiritHerbPlotBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MxtParticleTypes.SPIRIT_WISP.get(), SpiritWispParticle.Provider::new);
        event.registerSpriteSet(MxtParticleTypes.RIFT.get(), Provider::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        page(event, MxtMenus.CHEQUE_TABLE.get(), (menu, inventory, title) ->
                new ChequeTableScreen((ChequeTableMenu) menu, inventory, title));
        page(event, MxtMenus.EXCHANGE_STATION.get(), (menu, inventory, title) ->
                new ExchangeStationScreen((ExchangeStationMenu) menu, inventory, title));
        page(event, MxtMenus.SYSTEM_STATION_OWNER.get(), (menu, inventory, title) ->
                new StationScreen((StationMenu) menu, inventory, title));
        page(event, MxtMenus.SYSTEM_STATION_CUSTOMER.get(), (menu, inventory, title) ->
                new StationScreen((StationMenu) menu, inventory, title));
        page(event, MxtMenus.TRADE_STATION_OWNER.get(), (menu, inventory, title) ->
                new StationScreen((StationMenu) menu, inventory, title));
        page(event, MxtMenus.TRADE_STATION_CUSTOMER.get(), (menu, inventory, title) ->
                new StationScreen((StationMenu) menu, inventory, title));
        page(event, MxtMenus.PLAYER_TRADE.get(), (menu, inventory, title) ->
                new PlayerTradeScreen((PlayerTradeMenu) menu, inventory, title));
        page(event, MxtMenus.SPIRIT_CRAFTING_TABLE.get(), (menu, inventory, title) ->
                new SpiritCraftingScreen((SpiritCraftingMenu) menu, inventory, title));
        page(event, MxtMenus.FORGING_TABLE.get(), (menu, inventory, title) ->
                new ForgingScreen((ForgingMenu) menu, inventory, title));
        page(event, MxtMenus.ALCHEMY_FURNACE.get(), (menu, inventory, title) ->
                new AlchemyFurnaceScreen((AlchemyFurnaceMenu) menu, inventory, title));
        page(event, MxtMenus.TALISMAN_WORKSTATION.get(), (menu, inventory, title) ->
                new TalismanWorkstationScreen((TalismanWorkstationMenu) menu, inventory, title));
        event.register(MxtMenus.ARTIFACT_STORAGE.get(), ContainerScreen::new);
    }

    /**
     * Registers one page screen. The event's own bound wants a screen typed to the menu its type creates, while
     * every page screen extends ApricityUI's container screen, which is typed to ApricityUI's menu base - so that
     * bound cannot be written and the erasure is taken here instead. It is only a formality: the client hands this
     * factory the menu its own menu type has just built, which is the page's menu.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void page(RegisterMenuScreensEvent event, MenuType<?> type, ScreenConstructor constructor) {
        event.register((MenuType) type, constructor);
    }

    @SubscribeEvent
    public static void addPlayerLayers(AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer == null) continue;
            renderer.addLayer(new BackWeaponRenderer(renderer, event.getContext().getItemModelResolver()));
            renderer.addLayer(new BeltWeaponRenderer(renderer, event.getContext().getItemModelResolver()));
            renderer.addLayer(new CultivationItemRenderer(renderer, event.getContext().getItemModelResolver()));
        }
    }
}
