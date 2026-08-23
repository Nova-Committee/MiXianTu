package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Registry;
import com.iafenvoy.mxt.data.aura.AuraZone;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks aura source transitions without retaining unloaded entities.
 */
@EventBusSubscriber
public final class AuraZoneEventBridge {
    private static final Map<UUID, AuraResult> LAST = new ConcurrentHashMap<>();

    private AuraZoneEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        AuraResult current = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        AuraResult previous = LAST.put(entity.getUUID(), current);
        if (previous == null || !previous.source().equals(current.source()) || previous.sourceKind() != current.sourceKind()) {
            if (previous != null) NeoForge.EVENT_BUS.post(new Leave(entity, previous));
            NeoForge.EVENT_BUS.post(new Enter(entity, current));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 5L != 0L) return;
        Registry<AuraZone> zones = level.registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE);
        level.players().forEach(player -> {
            AuraResult aura = AuraService.getPositionAura(level, player.blockPosition());
            if (level.getGameTime() % 20L == 0L) {
                NeoForge.EVENT_BUS.post(new Tick(level, player.blockPosition(), aura));
                Map<Identifier, AuraPool> values = new LinkedHashMap<>();
                aura.aura().forEach((resource, pool) -> values.put(HolderHelper.id(resource), pool));
                PacketDistributor.sendToPlayer(player, new AuraStateS2CPayload(aura.source(), values));
            }
            zones.getOptional(aura.source()).flatMap(AuraZone::particle)
                    .ifPresent(effect -> effect.sendTo(level, player, player.position()));
        });
    }
}
