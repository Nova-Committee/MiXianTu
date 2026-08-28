package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.AuraZone.ClientRender;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraClientState.Snapshot;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;
import net.neoforged.neoforge.client.event.ViewportEvent.RenderFog;

import java.util.Map.Entry;
import java.util.Optional;

/**
 * Applies the client-only portion of an aura-zone definition around the camera entity.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AuraZoneRenderer {
    private AuraZoneRenderer() {
    }

    @SubscribeEvent
    public static void onFogColor(ComputeFogColor event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasFogStrength).filter(fog -> fog.render().fogColor() != 0xFFFFFF).ifPresent(fog -> {
            int color = fog.color();
            float strength = fog.strength();
            event.setRed(blend(event.getRed(), ((color >>> 16) & 0xFF) / 255.0F, strength));
            event.setGreen(blend(event.getGreen(), ((color >>> 8) & 0xFF) / 255.0F, strength));
            event.setBlue(blend(event.getBlue(), (color & 0xFF) / 255.0F, strength));
        });
    }

    @SubscribeEvent
    public static void onRenderFog(RenderFog event) {
        resolve(Minecraft.getInstance().getCameraEntity()).filter(AuraZoneRenderer::hasFogStrength)
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
        return zones.getOptional(snapshot.source()).map(zone -> new ResolvedFog(zone.clientRender(), snapshot.environmentConcentration(),
                zone.aura().values().stream().mapToDouble(AuraValue::amount).sum(), resolveColor(zone, snapshot)));
    }

    private static int resolveColor(AuraZone zone, Snapshot snapshot) {
        double weight = 0.0D;
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        boolean hasExplicitColor = false;
        for (Entry<Holder<Resource>, AuraValue> entry : zone.aura().entrySet()) {
            double amount = snapshot.environmentPool(HolderHelper.id(entry.getKey())).amount();
            if (!Double.isFinite(amount) || amount <= 0.0D) continue;
            int color = entry.getValue().color();
            if (color == 0xFFFFFF) {
                color = entry.getKey().value().auraType().map(type -> type.value().color()).orElse(0xFFFFFF);
            }
            hasExplicitColor |= color != 0xFFFFFF;
            red += ((color >>> 16) & 0xFF) * amount;
            green += ((color >>> 8) & 0xFF) * amount;
            blue += (color & 0xFF) * amount;
            weight += amount;
        }
        if (weight <= 0.0D || !hasExplicitColor) return zone.clientRender().fogColor();
        return ((int) Math.round(red / weight) << 16)
                | ((int) Math.round(green / weight) << 8)
                | (int) Math.round(blue / weight);
    }

    private static boolean hasFogStrength(ResolvedFog fog) {
        return fog.strength() > 0.0F;
    }

    private static float blend(float current, float target, float strength) {
        return current + (target - current) * strength;
    }

    private record ResolvedFog(ClientRender render, double concentration, double baseAura, int color) {
        private float strength() {
            double scale = Math.max(1.0D, this.baseAura);
            double concentrationFactor = Math.clamp(this.concentration / scale, 0.0D, 1.0D);
            return (float) Math.clamp(this.render.fogStrength() * concentrationFactor, 0.0D, 1.0D);
        }
    }
}
