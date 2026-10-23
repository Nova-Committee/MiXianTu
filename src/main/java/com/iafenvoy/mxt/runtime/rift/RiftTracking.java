package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.network.payload.RiftSyncS2CPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@EventBusSubscriber
public final class RiftTracking {
    private static final UUID ZERO = new UUID(0, 0);
    private static final Map<ServerLevel, UUID> GENERATIONS = new IdentityHashMap<>();
    private static final Map<UUID, Watcher> WATCHERS = new HashMap<>();

    private RiftTracking() {
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Sent event) {
        Watcher watcher = watcher(event.getPlayer(), event.getLevel());
        watcher.chunks.add(event.getPos().pack());
        refresh(watcher);
    }

    @SubscribeEvent
    public static void unwatch(ChunkWatchEvent.UnWatch event) {
        Watcher watcher = WATCHERS.get(event.getPlayer().getUUID());
        if (watcher != null && watcher.level == event.getLevel()) {
            watcher.chunks.remove(event.getPos().pack());
            refresh(watcher);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHERS.remove(event.getEntity().getUUID());
    }

    private static Watcher watcher(ServerPlayer player, ServerLevel level) {
        Watcher current = WATCHERS.get(player.getUUID());
        if (current == null || current.level != level || current.player != player) {
            current = new Watcher(player, level);
            WATCHERS.put(player.getUUID(), current);
            PacketDistributor.sendToPlayer(player, new RiftSyncS2CPayload(level.dimension().identifier(), generation(level), ZERO, 0, true, Optional.empty()));
        }
        return current;
    }

    private static UUID generation(ServerLevel level) {
        return GENERATIONS.computeIfAbsent(level, _ -> UUID.randomUUID());
    }

    public static void changed(ServerLevel level, @Nullable UUID id) {
        for (Watcher watcher : List.copyOf(WATCHERS.values())) if (watcher.level == level) refresh(watcher);
    }

    private static void refresh(Watcher watcher) {
        RiftWorldAttachment world = watcher.level.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
        Set<UUID> visible = new HashSet<>();
        Set<UUID> candidates = new HashSet<>();
        if (world != null) for (long chunk : watcher.chunks) candidates.addAll(world.runtime().inChunk(chunk));
        for (UUID id : candidates) {
            RiftRecord record = RiftService.get(watcher.level, id);
            if (record == null) continue;
            if (record.expired(RiftService.now(watcher.level))) continue;
            RiftGeometry.Mesh mesh = world.runtime().mesh(record.id());
            if (mesh == null) continue;
            visible.add(record.id());
            if (!Objects.equals(watcher.sent.get(record.id()), record.revision())) {
                PacketDistributor.sendToPlayer(watcher.player, new RiftSyncS2CPayload(watcher.level.dimension().identifier(),
                        generation(watcher.level), record.id(), record.revision(), false, Optional.of(RiftVisual.of(record))));
                watcher.sent.put(record.id(), record.revision());
            }
        }
        for (UUID id : Set.copyOf(watcher.sent.keySet()))
            if (!visible.contains(id)) {
                long revision = watcher.sent.remove(id);
                PacketDistributor.sendToPlayer(watcher.player, new RiftSyncS2CPayload(watcher.level.dimension().identifier(),
                        generation(watcher.level), id, revision, false, Optional.empty()));
            }
    }

    public static void unload(ServerLevel level) {
        GENERATIONS.remove(level);
        WATCHERS.values().removeIf(watcher -> watcher.level == level);
    }

    public static void clear() {
        GENERATIONS.clear();
        WATCHERS.clear();
    }

    private static final class Watcher {
        final ServerPlayer player;
        final ServerLevel level;
        final Set<Long> chunks = new HashSet<>();
        final Map<UUID, Long> sent = new HashMap<>();

        Watcher(ServerPlayer player, ServerLevel level) {
            this.player = player;
            this.level = level;
        }
    }
}
