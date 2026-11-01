package com.iafenvoy.mxt.runtime.rift;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.attachment.RiftWorldAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.rift.RiftDestination;
import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftTemplate;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resulting.MessageFailure;
import com.iafenvoy.mxt.runtime.resulting.failures.RiftFailure;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.dispatcher.TickDispatcher;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RiftService {
    private RiftService() {
    }

    public static RiftWorldAttachment world(ServerLevel level) {
        RiftWorldAttachment world = level.getData(MxtAttachments.RIFT_WORLD);
        RiftStorage.initialize(level, world);
        return world;
    }

    public static @Nullable RiftRecord get(ServerLevel level, UUID id) {
        return level.hasData(MxtAttachments.RIFT_WORLD) ? RiftStorage.read(level, id) : null;
    }

    // Explicit administrative enumeration may read unloaded owner chunks; hot paths use the runtime index.
    public static List<RiftRecord> records(ServerLevel level) {
        List<RiftRecord> result = new ArrayList<>();
        for (RiftLocation location : world(level).locations()) {
            RiftRecord record = get(level, location.id());
            if (record != null) result.add(record);
        }
        return List.copyOf(result);
    }

    public static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    public static Result<RiftRecord> publish(ServerLevel level, @Nullable UUID id, long revision, RiftTemplate template,
                                             @Nullable UUID owner, boolean resetDuration) {
        RiftWorldAttachment world = world(level);
        RiftRecord old = id == null ? null : get(level, id);
        if (id != null && (old == null || old.revision() != revision))
            return Result.failure(RiftFailure.REVISION_CONFLICT);
        if (old != null && old.expired(now(level))) return Result.failure(RiftFailure.EXPIRED);
        if (validate(level, template) instanceof Result.FailureResult<Void>(
                Failure failure1
        )) return Result.failure(failure1);
        RiftPlacement placement = template.placement().normalized().result().orElseThrow();
        RiftTemplate data = new RiftTemplate(template.key(), placement, false, template.shape(),
                template.destination().resolve(level.dimension().identifier()), template.appearance(), template.durationTicks(), template.enabled());
        RiftGeometry.Mesh mesh = RiftGeometry.mesh(data.shape(), placement, RiftGeometry.triangulate(data.shape()).result().orElseThrow());
        RiftRuntime runtime = world.runtime();
        if (old == null && world.locations().size() >= MxtServerConfig.INSTANCE.rifts.maxPerWorld.getValue()
                || !runtime.fits(id, mesh, MxtServerConfig.INSTANCE.rifts.maxPerSection.getValue()))
            return Result.failure(RiftFailure.BUDGET);
        long expires = old != null && !resetDuration ? old.expiresAt() : deadline(now(level), data.durationTicks());
        RiftRecord record = old == null
                ? new RiftRecord(UUID.randomUUID(), 1, data, expires, Optional.ofNullable(owner), Optional.empty(), -1, Optional.empty())
                : old.edit(data, expires, old.data().destination().equals(data.destination()));
        RiftStorage.write(level, record, mesh);
        schedule(level, record);
        RiftTracking.changed(level, record.id());
        return Result.success(record);
    }

    public static Result<Void> validate(ServerLevel level, RiftTemplate data) {
        if (validateTemplate(level, data) instanceof Result.FailureResult<Void>(
                Failure failure1
        )) return Result.failure(failure1);
        RiftPlacement placement = data.placement().normalized().result().orElseThrow();
        for (RiftVertex point : data.shape().vertices()) {
            Vec3 pos = placement.world(point);
            if (pos.y < level.getMinY() || pos.y >= level.getMaxY()
                    || !level.getWorldBorder().isWithinBounds(pos.x, pos.z))
                return Result.failure(RiftFailure.OUTSIDE_WORLD);
        }
        return Result.success();
    }

    // Item blueprints are portable; world containment is checked only when publishing a placed portal.
    public static Result<Void> validateTemplate(ServerLevel level, RiftTemplate data) {
        DataResult<JsonElement> encoded = RiftTemplate.CODEC.encodeStart(JsonOps.INSTANCE, data);
        if (encoded.error().isPresent())
            return Result.failure(new MessageFailure(RiftFailure.INVALID_TEMPLATE, Component.literal(encoded.error().get().message())));
        DataResult<RiftTemplate> decoded = RiftTemplate.CODEC.parse(JsonOps.INSTANCE, encoded.result().orElseThrow());
        if (decoded.error().isPresent())
            return Result.failure(new MessageFailure(RiftFailure.INVALID_TEMPLATE, Component.literal(decoded.error().get().message())));
        if (data.durationTicks() > Long.MAX_VALUE - now(level)) return Result.failure(RiftFailure.DURATION_OVERFLOW);
        if (data.shape().vertices().size() > MxtServerConfig.INSTANCE.rifts.maxVertices.getValue())
            return Result.failure(RiftFailure.BUDGET);
        if (data.destination() instanceof RiftDestination.Position p) {
            if (!RiftPlacement.finite(p.pos()) || Math.abs(p.pos().x) > 30_000_000 || Math.abs(p.pos().z) > 30_000_000
                    || Math.abs(p.pos().y) > 30_000_000 || p.yaw().filter(v -> !Float.isFinite(v)).isPresent()
                    || p.pitch().filter(v -> !Float.isFinite(v) || Math.abs(v) > 90).isPresent())
                return Result.failure(RiftFailure.INVALID_DESTINATION);
            if (p.dimension().isPresent() && level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, p.dimension().get())) == null
                    && SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, p.dimension().get())).isEmpty())
                return Result.failure(RiftFailure.MISSING_DIMENSION);
        } else if (data.destination() instanceof RiftDestination.Realm(Identifier realm1)
                && MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.SECRET_REALM, realm1).isEmpty())
            return Result.failure(RiftFailure.MISSING_REALM);
        return Result.success();
    }

    public static boolean remove(ServerLevel level, UUID id) {
        RiftWorldAttachment world = world(level);
        RiftLocation location = world.location(id);
        if (location == null) return false;
        RiftStorage.eraseLoaded(level, location.chunk(), id);
        world.remove(id);
        world.runtime().expiry.cancel(id);
        RiftTravelService.cancel(level, id);
        RiftTracking.changed(level, id);
        return true;
    }

    public static void bind(ServerLevel level, RiftRecord record, Identifier dimension, int index, UUID instanceId) {
        RiftRecord current = get(level, record.id());
        if (current == null || current.revision() != record.revision()) return;
        RiftStorage.write(level, record.bind(dimension, index, instanceId), world(level).runtime().mesh(record.id()));
        RiftTracking.changed(level, record.id());
    }

    public static void schedule(ServerLevel level, RiftRecord record) {
        RiftLocation location = world(level).location(record.id());
        if (location != null) schedule(level, location);
    }

    public static void schedule(ServerLevel level, RiftLocation location) {
        TickDispatcher<UUID> dispatcher = world(level).runtime().expiry;
        dispatcher.advanceTo(now(level));
        dispatcher.cancel(location.id());
        if (location.expiresAt() >= 0)
            dispatcher.scheduleOnce(location.id(), Math.max(0, location.expiresAt() - now(level)), () -> remove(level, location.id()));
    }

    public static boolean layout(ServerLevel level, String identity, Vec3 entry, List<RiftTemplate> templates) {
        RiftRuntime trial = new RiftRuntime(List.of());
        for (RiftLocation location : world(level).locations()) trial.index(location.id(), location.bounds());
        List<RiftRecord> additions = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        try (trial) {
            for (RiftTemplate source : templates) {
                if (!keys.add(source.key())) return false;
                UUID id = UUID.nameUUIDFromBytes((identity + "/" + source.key()).getBytes(StandardCharsets.UTF_8));
                if (world(level).location(id) != null) continue;
                RiftPlacement p = source.placement();
                RiftTemplate resolved = new RiftTemplate(source.key(), new RiftPlacement(
                        source.relativeToEntry() ? p.origin().add(entry) : p.origin(), p.normal(), p.tangent()), false,
                        source.shape(), source.destination().resolve(level.dimension().identifier()), source.appearance(), source.durationTicks(), source.enabled());
                if (validate(level, resolved) instanceof Result.FailureResult<Void>) return false;
                resolved = RiftEdits.placement(resolved, resolved.placement().normalized().result().orElseThrow());
                RiftGeometry.Mesh mesh = RiftGeometry.mesh(resolved.shape(), resolved.placement(), RiftGeometry.triangulate(resolved.shape()).result().orElseThrow());
                if (!trial.fits(id, mesh, MxtServerConfig.INSTANCE.rifts.maxPerSection.getValue())) return false;
                RiftRecord record = new RiftRecord(id, 1, resolved, deadline(now(level), resolved.durationTicks()), Optional.empty(), Optional.empty(), -1, Optional.empty());
                trial.put(record, mesh);
                additions.add(record);
            }
            if (world(level).locations().size() + additions.size() > MxtServerConfig.INSTANCE.rifts.maxPerWorld.getValue())
                return false;
            for (RiftRecord record : additions) {
                RiftStorage.write(level, record, trial.mesh(record.id()));
                schedule(level, record);
                RiftTracking.changed(level, record.id());
            }
            return true;
        }
    }

    private static long deadline(long now, long duration) {
        return duration == 0 ? -1 : now + duration;
    }
}
