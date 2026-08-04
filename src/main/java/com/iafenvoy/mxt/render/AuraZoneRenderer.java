package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientRender;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Optional;

/** Applies the client-only portion of an aura-zone definition around the camera entity. */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class AuraZoneRenderer {
    private AuraZoneRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.getGameTime() % 5L != 0L) return;
        resolve(minecraft.player).ifPresent(render -> particle(render).ifPresent(options -> {
            for (int index = 0; index < render.particleDensity(); index++) {
                double x = minecraft.player.getX() + (minecraft.player.getRandom().nextDouble() - 0.5D) * 4.0D;
                double y = minecraft.player.getY() + minecraft.player.getRandom().nextDouble() * 2.0D;
                double z = minecraft.player.getZ() + (minecraft.player.getRandom().nextDouble() - 0.5D) * 4.0D;
                minecraft.level.addParticle(options, x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasVisualOverride).ifPresent(render -> {
            int color = color(render.fogColor());
            event.setRed(((color >>> 16) & 0xFF) / 255.0F);
            event.setGreen(((color >>> 8) & 0xFF) / 255.0F);
            event.setBlue((color & 0xFF) / 255.0F);
        });
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasVisualOverride)
                .ifPresent(render -> {
                    float far = Math.min(event.getFarPlaneDistance(), render.renderDistance());
                    event.setFarPlaneDistance(far);
                    event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.25F));
                });
    }

    private static Optional<ClientRender> resolve(Entity entity) {
        if (entity == null || entity.level() == null) return Optional.empty();
        AuraResult result = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        Registry<AuraZone> zones = entity.level().registryAccess().lookupOrThrow(MxtDatapackRegistries.AURA_ZONE);
        return zones.getOptional(result.source()).map(AuraZone::clientRender);
    }

    private static Optional<ParticleOptions> particle(ClientRender render) {
        if (render.particleDensity() <= 0) return Optional.empty();
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(render.particle())
                .filter(ParticleOptions.class::isInstance)
                .map(ParticleOptions.class::cast);
    }

    private static boolean hasVisualOverride(ClientRender render) {
        return render.particleDensity() > 0 || !"#FFFFFF".equalsIgnoreCase(render.fogColor());
    }

    private static int color(String value) {
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            return (int) Long.parseLong(hex.length() == 8 ? hex.substring(2) : hex, 16);
        } catch (RuntimeException ignored) {
            return 0xFFFFFF;
        }
    }
}
