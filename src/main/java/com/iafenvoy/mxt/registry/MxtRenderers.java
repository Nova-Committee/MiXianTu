package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.compat.geckolib.GeckoLibCompat;
import com.iafenvoy.mxt.compat.geckolib.GeckoLibMountRenderers;
import com.iafenvoy.mxt.render.DisplayStandBlockEntityRenderer;
import com.iafenvoy.mxt.render.FlyingSwordRenderer;
import com.iafenvoy.mxt.render.RiftBlockEntityRenderer;
import com.iafenvoy.mxt.render.StationBlockEntityRenderer;
import com.iafenvoy.mxt.render.accessory.BackWeaponRenderer;
import com.iafenvoy.mxt.render.accessory.BeltWeaponRenderer;
import com.iafenvoy.mxt.render.cultivation.CultivationItemRenderer;
import com.iafenvoy.mxt.render.lightning.ColoredLightningBoltRenderer;
import com.iafenvoy.mxt.render.mount.MountRenderers;
import com.iafenvoy.mxt.render.particle.RiftParticle.Provider;
import com.iafenvoy.mxt.render.particle.SpiritWispParticle;
import com.iafenvoy.mxt.render.sword.SwordAuraRenderer;
import com.iafenvoy.mxt.render.talisman.TalismanProjectileRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
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
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MxtParticleTypes.SPIRIT_WISP.get(), SpiritWispParticle.Provider::new);
        event.registerSpriteSet(MxtParticleTypes.RIFT.get(), Provider::new);
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
