package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.event.AuraZoneEvent.Enter;
import com.iafenvoy.mxt.event.AuraZoneEvent.Leave;
import com.iafenvoy.mxt.event.AuraZoneEvent.Tick;
import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraQueryCache.AuraLocation;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks aura source transitions without retaining unloaded entities; an entity is only re-resolved when its
 * answer can actually have changed, because one resolution reads the biome of every block emitter nearby.
 */
@EventBusSubscriber
public final class AuraZoneEventBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Keyed by level and UUID, so a dimension change never compares a player against another level's snapshot.
    private static final Map<Key, AuraResult> LAST = new ConcurrentHashMap<>();

    private AuraZoneEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        AuraLocation position = AuraQueryCache.location(level, entity.blockPosition());
        if (!AuraQueryCache.needsQuery(level, entity.getUUID(), position, MxtServerConfig.INSTANCE.aura.entityRefreshInterval.getValue()))
            return;
        AuraResult current = AuraService.getPositionAura(level, entity.blockPosition());
        AuraQueryCache.recordQuery(level, entity.getUUID(), position);
        AuraResult previous = LAST.put(new Key(level.dimension().identifier(), entity.getUUID()), current);
        if (previous == null || !previous.source().equals(current.source()) || previous.sourceKind() != current.sourceKind()) {
            if (previous != null) NeoForge.EVENT_BUS.post(new Leave(entity, previous));
            NeoForge.EVENT_BUS.post(new Enter(entity, current));
        }
    }

    // Forgets the tracked aura, so the map cannot grow with entities that are unloaded, despawned or dead.
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LAST.remove(new Key(level.dimension().identifier(), event.getEntity().getUUID()));
        AuraQueryCache.forget(level, event.getEntity().getUUID());
    }

    // Runs at the very end of the level tick, after AuraChunkTicker has regenerated this tick's chunk stock, so
    // every query made during the next tick sees one consistent snapshot.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        AuraQueryCache.advance(level, gameTime);
        AuraQueryCache.setTiming(MxtServerConfig.INSTANCE.aura.queryStats.getValue());
        if (MxtServerConfig.INSTANCE.aura.queryStats.getValue() && gameTime % 200L == 0L) {
            reportQueryStats(gameTime);
            AuraQueryCache.reportDiagnostics();
            AuraQueryCache.reportStageCosts();
        }
        Registry<AuraZone> zones = level.registryAccess().lookupOrThrow(MxtResourceKeys.AURA_ZONE);
        level.players().forEach(player -> {
            AuraResult aura = AuraService.getPositionAura(level, player.blockPosition());
            long syncInterval = MxtServerConfig.INSTANCE.aura.auraSyncInterval.getValue();
            if (level.getGameTime() % syncInterval == 0L) {
                NeoForge.EVENT_BUS.post(new Tick(level, player.blockPosition(), aura));
                AuraResult sensed = AuraService.getSensedAura(level, player.blockPosition());
                PacketDistributor.sendToPlayer(player, new AuraStateS2CPayload(aura.source(), aura.aura(), sensed.aura()));
            }
            boolean cultivating = player.getData(MxtAttachments.CULTIVATION).cultivating();
            boolean emitParticle = cultivating ? level.getGameTime() % 5L < 3L : level.getGameTime() % 5L == 0L;
            if (emitParticle) zones.getOptional(aura.source()).flatMap(AuraZone::particle)
                    .ifPresent(effect -> effect.sendTo(level, player, player.position()));
        });
    }

    // The average per query is the number that matters, because a memoised tick issues far fewer queries.
    private static void reportQueryStats(long gameTime) {
        long queries = AuraQueryCache.queries();
        double millis = AuraQueryCache.nanos() / 1_000_000.0D;
        AuraQueryCache.resetStats();
        LOGGER.info("MiXianTu aura resolution: {} queries, {} skipped entity ticks, {} ms total, {} us per query at tick {}",
                queries, AuraQueryCache.skipped(), String.format(Locale.ROOT, "%.1f", millis),
                String.format(Locale.ROOT, "%.2f", queries == 0L ? 0.0D : millis * 1000.0D / queries), gameTime);
    }

    private record Key(Identifier level, UUID entity) {
    }
}
