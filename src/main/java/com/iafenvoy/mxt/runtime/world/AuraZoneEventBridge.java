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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks aura source transitions without retaining unloaded entities.
 * <p>
 * An entity is only re-resolved when its answer can actually have changed: a new entity, a moved
 * entity, or an entity whose refresh interval elapsed. Resolving every entity every tick is what made
 * this listener dominate the server thread, because one resolution reads the biome of every block
 * emitter in a 7x7 chunk neighbourhood.
 */
@EventBusSubscriber
public final class AuraZoneEventBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
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
        AuraQueryCache.AuraLocation position = AuraQueryCache.location(level, entity.blockPosition());
        if (!AuraQueryCache.needsQuery(level, entity.getUUID(), position, MxtServerConfig.auraEntityRefreshInterval()))
            return;
        AuraResult current = AuraService.getPositionAura(level, entity.blockPosition());
        AuraQueryCache.recordQuery(level, entity.getUUID(), position);
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
        AuraQueryCache.forget(level, event.getEntity().getUUID());
    }

    /**
     * Opens the next resolution window for this level and closes the previous one. It runs at the
     * very end of the level tick, after {@link AuraChunkTicker} has regenerated the chunk stock for
     * this tick, so every query made during the next tick sees one consistent snapshot and the
     * resolver memo cannot leak a value computed from an older one.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        AuraQueryCache.advance(level, gameTime);
        AuraQueryCache.setTiming(MxtServerConfig.auraQueryStats());
        if (MxtServerConfig.auraQueryStats() && gameTime % 200L == 0L) {
            reportQueryStats(gameTime);
            AuraQueryCache.reportDiagnostics();
            AuraQueryCache.reportStageCosts();
        }
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

    /**
     * Reports the measured cost of {@link AuraService#getPositionAura} and starts a new ten second
     * window. The average per query is the number that matters: a memoised tick issues one query per
     * ticked entity plus one per distinct query position, while an unmemoised tick issues one per
     * entity and one more per contributing block-emitter source.
     */
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
