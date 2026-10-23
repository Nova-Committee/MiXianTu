package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.event.dispatcher.CollectTickStagesEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntityTickStages;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

@EventBusSubscriber
public final class RiftContactCollector {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<ServerLevel, Map<UUID, Request>> REQUESTS = new IdentityHashMap<>();

    private RiftContactCollector() {
    }

    @SubscribeEvent
    public static void stages(CollectTickStagesEvent event) {
        event.add("rift", EntityTickStages.RIFTS, RiftContactCollector::collect);
    }

    @SubscribeEvent
    public static void nonLiving(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity)) collect(event.getEntity());
    }

    public static void collect(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.RIFT)) {
            collect(level, entity);
        }
    }

    private static void collect(ServerLevel level, Entity entity) {
        RiftWorldAttachment world = level.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
        if (world == null || world.runtime().empty()) {
            STATES.remove(entity.getUUID());
            return;
        }
        long now = RiftService.now(level);
        AABB current = entity.getBoundingBox();
        if (current.getXsize() > 16 || current.getYsize() > 32 || current.getZsize() > 16
                || !Double.isFinite(current.getSize())) {
            STATES.remove(entity.getUUID());
            return;
        }
        State old = STATES.get(entity.getUUID());
        boolean continuous = old != null && old.entity == entity && old.level == level && now - old.tick == 1
                && old.box.getCenter().distanceToSqr(current.getCenter()) <= 64 * 64
                && Math.abs(old.box.getXsize() - current.getXsize()) < 1e-6
                && Math.abs(old.box.getYsize() - current.getYsize()) < 1e-6
                && Math.abs(old.box.getZsize() - current.getZsize()) < 1e-6;
        AABB previous = continuous ? old.box : current;
        Vec3 movement = current.getCenter().subtract(previous.getCenter());
        boolean touching = false;
        Request best = null;
        for (UUID id : world.runtime().query(previous.minmax(current))) {
            RiftRecord record = world.get(id);
            if (record == null || record.expired(now) || !record.data().enabled()) continue;
            RiftGeometry.Mesh mesh = world.runtime().mesh(id);
            if (Double.isFinite(mesh.contact(current, Vec3.ZERO))) touching = true;
            double at = mesh.contact(previous, movement);
            if (!Double.isFinite(at)) continue;
            double distance = mesh.bounds().getCenter().distanceToSqr(current.getCenter());
            Request request = new Request(entity, record, at, distance);
            if (best == null || request.compareTo(best) < 0) best = request;
        }
        boolean armed = old == null || old.level != level || old.armed;
        if (!touching && best == null) armed = true;
        if (best != null && armed && !entity.isOnPortalCooldown()) {
            REQUESTS.computeIfAbsent(level, _ -> new LinkedHashMap<>()).put(entity.getUUID(), best);
            armed = false;
        }
        STATES.put(entity.getUUID(), new State(entity, level, current, now, armed));
    }

    public static void disarm(Entity entity) {
        if (entity.level() instanceof ServerLevel level)
            STATES.put(entity.getUUID(), new State(entity, level, entity.getBoundingBox(), RiftService.now(level), false));
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RiftWorldAttachment world = level.getExistingData(MxtAttachments.RIFT_WORLD).orElse(null);
        if (world == null) return;
        RiftStorage.initialize(level, world);
        world.runtime().expiry.tick(RiftService.now(level));
        Map<UUID, Request> requests = REQUESTS.remove(level);
        if (requests != null) for (Request request : requests.values()) {
            RiftRecord current = world.get(request.record.id());
            if (request.entity.level() == level && current != null && current.revision() == request.record.revision())
                RiftTravelService.tryEnter(level, current, request.entity);
        }
        RiftTravelService.tick(level);
        RiftEditorService.tick(level);
        if (RiftService.now(level) % 100 == 0)
            STATES.entrySet().removeIf(entry -> entry.getValue().entity.isRemoved()
                    || RiftService.now(level) - entry.getValue().tick > 100);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RiftTravelService.unload(level);
        level.getExistingData(MxtAttachments.RIFT_WORLD).ifPresent(RiftWorldAttachment::release);
        STATES.values().removeIf(state -> state.level == level);
        REQUESTS.remove(level);
        RiftStorage.release(level);
        RiftTracking.unload(level);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        RiftTravelService.clear();
        STATES.clear();
        REQUESTS.clear();
        RiftStorage.clear();
        RiftTracking.clear();
        RiftEditorService.clear();
    }

    private record State(Entity entity, ServerLevel level, AABB box, long tick, boolean armed) {
    }

    private record Request(Entity entity, RiftRecord record, double at,
                           double distance) implements Comparable<Request> {
        @Override
        public int compareTo(Request other) {
            int order = Double.compare(this.at, other.at);
            if (order == 0) order = Double.compare(this.distance, other.distance);
            return order == 0 ? this.record.id().compareTo(other.record.id()) : order;
        }
    }
}
