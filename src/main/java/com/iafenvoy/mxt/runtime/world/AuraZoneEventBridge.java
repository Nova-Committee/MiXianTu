package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks aura source transitions without retaining unloaded entities.
 */
@EventBusSubscriber
public final class AuraZoneEventBridge {
    /**
     * Last resolved aura per entity, keyed by level and UUID. The level is part of the key so a
     * dimension change never compares a player against another level's snapshot.
     */
    private static final Map<Key, AuraResult> LAST = new ConcurrentHashMap<>();

    private AuraZoneEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        AuraResult current = AuraService.getPositionAura(level, entity.blockPosition());
        AuraResult previous = LAST.put(new Key(level.dimension().identifier(), entity.getUUID()), current);
        if (previous == null || !previous.source().equals(current.source()) || previous.sourceKind() != current.sourceKind()) {
            if (previous != null) NeoForge.EVENT_BUS.post(new Leave(entity, previous));
            NeoForge.EVENT_BUS.post(new Enter(entity, current));
        }
    }

    /**
     * Forgets the tracked aura when an entity leaves a level, so the map cannot grow with
     * entities that are unloaded, despawned or dead.
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LAST.remove(new Key(level.dimension().identifier(), event.getEntity().getUUID()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Registry<AuraZone> zones = level.registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE);
        level.players().forEach(player -> {
            AuraResult aura = AuraService.getPositionAura(level, player.blockPosition());
            long syncInterval = Math.max(1L, MxtServerConfig.auraSyncInterval());
            if (level.getGameTime() % syncInterval == 0L) {
                NeoForge.EVENT_BUS.post(new Tick(level, player.blockPosition(), aura));
                AuraResult sensed = AuraService.getSensedAura(level, player.blockPosition());
                Map<Identifier, AuraPool> actual = new LinkedHashMap<>();
                aura.aura().forEach((resource, pool) -> actual.put(HolderHelper.id(resource), pool));
                Map<Identifier, AuraPool> environment = new LinkedHashMap<>();
                sensed.aura().forEach((resource, pool) -> environment.put(HolderHelper.id(resource), pool));
                PacketDistributor.sendToPlayer(player, new AuraStateS2CPayload(aura.source(), actual, environment));
            }
            boolean cultivating = player.getData(MxtAttachments.CULTIVATION).cultivating();
            boolean emitParticle = cultivating ? level.getGameTime() % 5L < 3L : level.getGameTime() % 5L == 0L;
            if (emitParticle) zones.getOptional(aura.source()).flatMap(AuraZone::particle)
                    .ifPresent(effect -> effect.sendTo(level, player, player.position()));
        });
    }

    private record Key(Identifier level, UUID entity) {
    }
}
