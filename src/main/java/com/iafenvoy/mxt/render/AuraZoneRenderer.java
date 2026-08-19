package com.iafenvoy.mxt.render;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientRender;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;
import net.neoforged.neoforge.client.event.ViewportEvent.RenderFog;

import java.util.Optional;

/**
 * Applies the client-only portion of an aura-zone definition around the camera entity.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class AuraZoneRenderer {
    private AuraZoneRenderer() {
    }

    @SubscribeEvent
    public static void onFogColor(ComputeFogColor event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasVisualOverride).ifPresent(fog -> {
            int color = color(fog.render().fogColor());
            float strength = fog.strength();
            event.setRed(blend(event.getRed(), ((color >>> 16) & 0xFF) / 255.0F, strength));
            event.setGreen(blend(event.getGreen(), ((color >>> 8) & 0xFF) / 255.0F, strength));
            event.setBlue(blend(event.getBlue(), (color & 0xFF) / 255.0F, strength));
        });
    }

    @SubscribeEvent
    public static void onRenderFog(RenderFog event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasVisualOverride)
                .ifPresent(fog -> {
                    float originalFar = event.getFarPlaneDistance();
                    float targetFar = Math.min(originalFar, fog.render().renderDistance());
                    float far = blend(originalFar, targetFar, fog.strength());
                    event.setFarPlaneDistance(far);
                    event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.25F));
                });
    }

    private static Optional<ResolvedFog> resolve(Entity entity) {
        if (entity == null) return Optional.empty();
        Snapshot snapshot = AuraClientState.current();
        Registry<AuraZone> zones = entity.level().registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE);
        return zones.getOptional(snapshot.source()).map(zone -> new ResolvedFog(zone.clientRender(), snapshot.concentration(), zone.baseAura()));
    }

    private static boolean hasVisualOverride(ResolvedFog fog) {
        return fog.strength() > 0.0F && !"#FFFFFF".equalsIgnoreCase(fog.render().fogColor());
    }

    private static float blend(float current, float target, float strength) {
        return current + (target - current) * strength;
    }

    private static int color(String value) {
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            return (int) Long.parseLong(hex.length() == 8 ? hex.substring(2) : hex, 16);
        } catch (RuntimeException ignored) {
            return 0xFFFFFF;
        }
    }

    private record ResolvedFog(ClientRender render, double concentration, double baseAura) {
        private float strength() {
            double scale = Math.max(1.0D, this.baseAura);
            double concentrationFactor = Math.clamp(this.concentration / scale, 0.0D, 1.0D);
            return (float) Math.clamp(this.render.fogStrength() * concentrationFactor, 0.0D, 1.0D);
        }
    }
}
