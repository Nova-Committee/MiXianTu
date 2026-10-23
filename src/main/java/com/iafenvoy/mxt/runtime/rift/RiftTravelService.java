package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.rift.RiftDestination;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RiftTravelService {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private RiftTravelService() {
    }

    public static boolean waiting(UUID entity) {
        return PENDING.containsKey(entity);
    }

    public static void tryEnter(ServerLevel level, RiftRecord record, Entity entity) {
        if (PENDING.containsKey(entity.getUUID()) || PENDING.size() >= 128 || entity.isOnPortalCooldown()
                || !entity.canUsePortal(false) || entity.isVehicle() || entity.isPassenger() || !record.data().enabled()
                || record.expired(RiftService.now(level))) return;
        RiftDestination destination = record.data().destination();
        if (destination instanceof RiftDestination.Realm(Identifier realm1)) {
            if (!(entity instanceof LivingEntity living)) {
                fail(entity, "living_only");
                return;
            }
            SecretRealmService.TravelResult result;
            if (record.boundDimension().isPresent()) {
                SecretRealmRecord instance = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, record.boundDimension().get())).orElse(null);
                if (instance == null || instance.index() != record.boundIndex() || record.boundInstance().filter(instance.instanceId()::equals).isEmpty()
                        || !instance.definition().unwrapKey().orElseThrow().identifier().equals(realm1)) {
                    fail(entity, "expired_instance");
                    return;
                }
                Vec3 origin = returnPoint(level, record, entity);
                if (origin == null) {
                    fail(entity, "unsafe_return");
                    return;
                }
                result = SecretRealmService.enterInstance(living, instance, null, origin);
            } else {
                Optional<Holder<SecretRealm>> definition = MxtDatapackRegistries.holder(level.registryAccess(), MxtResourceKeys.SECRET_REALM, realm1);
                if (definition.isEmpty()) {
                    fail(entity, "missing_realm");
                    return;
                }
                Vec3 origin = returnPoint(level, record, entity);
                if (origin == null) {
                    fail(entity, "unsafe_return");
                    return;
                }
                result = SecretRealmService.enterFromPortal(living, definition.get(), origin);
                if (result.isSuccess()) SecretRealmRegistry.ofMember(entity.getUUID()).ifPresent(instance ->
                        RiftService.bind(level, record, instance.dimension().identifier(), instance.index(), instance.instanceId()));
            }
            if (result.isSuccess()) arrived(result.traveller().orElseThrow());
            else if (entity instanceof ServerPlayer player)
                player.sendSystemMessage(result.message().orElseGet(() -> result.failure().message()), true);
            return;
        }
        if (destination instanceof RiftDestination.Return) {
            if (!(entity instanceof LivingEntity living)) {
                fail(entity, "living_only");
                return;
            }
            SecretRealmTravelAttachment travel = living.getExistingData(MxtAttachments.SECRET_REALM_TRAVEL).orElse(null);
            if (travel == null || !travel.active()) {
                fail(entity, "not_travelling");
                return;
            }
            destination = new RiftDestination.Position(travel.originDimension(), new Vec3(travel.originX(), travel.originY(), travel.originZ()),
                    Optional.of(travel.originYaw()), Optional.of(travel.originPitch()), true);
        }
        RiftDestination.Position position = (RiftDestination.Position) destination;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, position.dimension().orElseThrow());
        ServerLevel target = level.getServer().getLevel(key);
        if (target == null) {
            SecretRealmRegistry.at(key).ifPresent(instance -> SecretRealmService.open(level.getServer(), instance));
            target = level.getServer().getLevel(key);
        }
        if (target == null || !eligible(level, target, entity)) {
            fail(entity, "missing_dimension");
            return;
        }
        if (entity.getBoundingBox().getXsize() > 16 || entity.getBoundingBox().getZsize() > 16
                || entity.getBoundingBox().getYsize() > 32) {
            fail(entity, "entity_size");
            return;
        }
        ChunkPos chunk = new ChunkPos((int) Math.floor(position.pos().x / 16), (int) Math.floor(position.pos().z / 16));
        // A swept contact remains valid for this tick even after the body has crossed the plane.
        if (loaded(target, entity, position)) {
            execute(level, record, entity, target, position);
            return;
        }
        try {
            RiftChunkTicket ticket = RiftChunkTicket.acquire(target, chunk);
            try {
                PENDING.put(entity.getUUID(), new Pending(level, record.id(), record.revision(), entity, position, target, ticket, RiftService.now(level)));
            } catch (RuntimeException | Error failure) {
                ticket.close();
                throw failure;
            }
        } catch (RuntimeException failure) {
            fail(entity, "loading_failed");
        }
    }

    public static void tick(ServerLevel level) {
        for (Map.Entry<UUID, Pending> entry : List.copyOf(PENDING.entrySet())) {
            Pending pending = entry.getValue();
            if (pending.source != level) continue;
            boolean keepWaiting = false;
            try {
                Entity entity = pending.entity;
                RiftRecord record = RiftService.get(level, pending.rift);
                RiftGeometry.Mesh mesh = RiftService.world(level).runtime().mesh(pending.rift);
                if (!entity.isAlive() || entity.isRemoved() || entity.level() != level || record == null || record.revision() != pending.revision
                        || !record.data().enabled() || record.expired(RiftService.now(level)) || mesh == null
                        || !Double.isFinite(mesh.contact(entity.getBoundingBox(), Vec3.ZERO))) continue;
                if (RiftService.now(level) - pending.started > 200) {
                    fail(entity, "loading_timeout");
                    continue;
                }
                if (!eligible(level, pending.target, entity)) {
                    fail(entity, "loading_failed");
                    continue;
                }
                if (!loaded(pending.target, entity, pending.position)) {
                    keepWaiting = true;
                    continue;
                }
                execute(level, record, entity, pending.target, pending.position);
            } finally {
                if (!keepWaiting) finish(entry.getKey());
            }
        }
    }

    private static boolean loaded(ServerLevel level, Entity entity, RiftDestination.Position destination) {
        AABB box = RiftLanding.area(entity, destination.pos(), destination.nearby());
        for (int x = (int) Math.floor(box.minX / 16); x <= (int) Math.floor(box.maxX / 16); x++)
            for (int z = (int) Math.floor(box.minZ / 16); z <= (int) Math.floor(box.maxZ / 16); z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private static void execute(ServerLevel source, RiftRecord record, Entity entity, ServerLevel target, RiftDestination.Position destination) {
        Vec3 pos = destination.pos();
        float yaw = destination.yaw().orElse(entity.getYRot()), pitch = destination.pitch().orElse(entity.getXRot());
        Entity moved;
        if (entity instanceof LivingEntity living) {
            SecretRealmRecord instance = SecretRealmRegistry.at(target.dimension()).orElse(null);
            Vec3 origin = instance != null && SecretRealmRegistry.ofMember(entity.getUUID()).isEmpty() ? returnPoint(source, record, entity) : null;
            if (instance != null && SecretRealmRegistry.ofMember(entity.getUUID()).isEmpty() && origin == null) {
                fail(entity, "unsafe_return");
                return;
            }
            SecretRealmService.TravelResult result = SecretRealmService.moveToPosition(living, target,
                    new SecretRealmEntryLocator.Arrival(pos, yaw, pitch), origin, destination.nearby());
            if (result.isFailure()) {
                if (entity instanceof ServerPlayer player)
                    player.sendSystemMessage(result.message().orElseGet(() -> result.failure().message()), true);
                return;
            }
            moved = result.traveller().orElseThrow();
        } else {
            if (SecretRealmRegistry.isRealm(target.dimension())) {
                fail(entity, "living_only");
                return;
            }
            RiftLanding.Inspection landing = RiftLanding.resolve(target, entity, pos, destination.nearby());
            if (landing.isFailure()) {
                TravelDiagnostics.rejected(entity, target, pos, "rift_destination", landing.failure().getSerializedName(),
                        "rift=" + record.id() + " revision=" + record.revision() + " nearby=" + destination.nearby() + " primary=" + landing);
                return;
            }
            pos = landing.position();
            moved = entity.teleport(new TeleportTransition(target, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
        }
        if (moved != null) arrived(moved);
        else {
            TravelDiagnostics.rejected(entity, target, pos, "rift_teleport", "platform_refused", "rift=" + record.id());
            fail(entity, "travel_denied");
        }
    }

    private static @Nullable Vec3 returnPoint(ServerLevel level, RiftRecord record, Entity entity) {
        Vec3 normal = record.data().placement().normal();
        double side = normal.dot(entity.position().subtract(record.data().placement().origin())) < 0 ? -1 : 1;
        Vec3 first = entity.position().add(normal.scale(side * (entity.getBbWidth() + 0.5)));
        RiftGeometry.Mesh mesh = RiftService.world(level).runtime().mesh(record.id());
        if (mesh == null) {
            TravelDiagnostics.rejected(entity, level, first, "rift_return", "missing_mesh", "rift=" + record.id());
            return null;
        }
        RiftLanding.Inspection landing = RiftLanding.resolve(level, entity, first, true,
                pos -> !Double.isFinite(mesh.contact(entity.getBoundingBox().move(pos.subtract(entity.position())), Vec3.ZERO)));
        if (landing.isSuccess()) return landing.position();
        TravelDiagnostics.rejected(entity, level, first, "rift_return", landing.failure().getSerializedName(),
                "rift=" + record.id() + " revision=" + record.revision() + " primary=" + landing);
        return null;
    }

    private static boolean eligible(ServerLevel source, ServerLevel target, Entity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isPassenger() && !entity.isVehicle()
                && entity.canUsePortal(false) && source.isAllowedToEnterPortal(target) && entity.canTeleport(source, target);
    }

    private static void arrived(Entity entity) {
        entity.setPortalCooldown();
        entity.setDeltaMovement(Vec3.ZERO);
        RiftContactCollector.disarm(entity);
        TeleportTransition.PLAY_PORTAL_SOUND.onTransition(entity);
    }

    private static void fail(Entity entity, String reason) {
        if (entity instanceof ServerPlayer player)
            player.sendSystemMessage(Component.translatable("rift.mxt.failure." + reason), true);
    }

    public static void cancel(ServerLevel level, UUID id) {
        for (Map.Entry<UUID, Pending> entry : List.copyOf(PENDING.entrySet()))
            if (entry.getValue().source == level && entry.getValue().rift.equals(id)) finish(entry.getKey());
    }

    public static void unload(ServerLevel level) {
        for (Map.Entry<UUID, Pending> entry : List.copyOf(PENDING.entrySet()))
            if (entry.getValue().source == level || entry.getValue().target == level) finish(entry.getKey());
        RiftChunkTicket.unload(level);
    }

    public static void clear() {
        for (UUID id : List.copyOf(PENDING.keySet())) finish(id);
        RiftChunkTicket.clear();
    }

    private static void finish(UUID id) {
        Pending pending = PENDING.remove(id);
        if (pending != null) pending.close();
    }

    private record Pending(ServerLevel source, UUID rift, long revision, Entity entity,
                           RiftDestination.Position position, ServerLevel target, RiftChunkTicket ticket,
                           long started) implements AutoCloseable {
        @Override
        public void close() {
            this.ticket.close();
        }
    }
}
