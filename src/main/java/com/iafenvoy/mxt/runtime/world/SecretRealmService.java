package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.runtime.world.SecretRealmRecord.Cleanup;
import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.builtin.ExistingGeneration;
import com.iafenvoy.mxt.event.SecretRealmEvent.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.resulting.MessageFailure;
import com.iafenvoy.mxt.runtime.resulting.failures.SecretRealmFailure;
import com.iafenvoy.mxt.runtime.rift.RiftLanding;
import com.iafenvoy.mxt.runtime.rift.RiftService;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator.Arrival;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator.Landing;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * Authoritative secret realm membership and cross-dimension travel.
 * <p>
 * Entering resolves to an existing instance when it has room, otherwise to a new instance dimension until
 * {@code max_instances} is reached. The dimension is created before anybody arrives, so structures are placed
 * and the landing spot is chosen on finished terrain.
 */
public final class SecretRealmService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Set<UUID>> CLEANING = ThreadLocal.withInitial(HashSet::new);
    private SecretRealmService() {
    }

    public static Result<LivingEntity> enter(LivingEntity traveller, MinecraftServer server, Holder<SecretRealm> definition) {
        return enter(traveller, server, definition, null, null, null, true);
    }

    public static Result<LivingEntity> enterFromPortal(LivingEntity traveller, Holder<SecretRealm> definition, Vec3 origin) {
        if (!(traveller.level() instanceof ServerLevel level)) return refused(Failures.SERVER_ONLY);
        return enter(traveller, level.getServer(), definition, null, null, origin, true);
    }

    public static Result<LivingEntity> enterInstance(LivingEntity traveller, SecretRealmRecord selected, @Nullable Arrival arrival, @Nullable Vec3 origin) {
        return enterInstance(traveller, selected, arrival, origin, arrival == null);
    }

    private static Result<LivingEntity> enterInstance(LivingEntity traveller, SecretRealmRecord selected, @Nullable Arrival arrival, @Nullable Vec3 origin, boolean nearby) {
        if (!(traveller.level() instanceof ServerLevel level)) return refused(Failures.SERVER_ONLY);
        SecretRealmRecord current = SecretRealmRegistry.at(selected.dimension()).orElse(null);
        if (current == null || current.index() != selected.index() || !current.instanceId().equals(selected.instanceId()) || !current.definition().equals(selected.definition())
                || current.cleanup() != Cleanup.NONE || current.expired(level.getServer().overworld().getGameTime()))
            return refused(SecretRealmFailure.MISSING_DIMENSION);
        return enter(traveller, level.getServer(), current.definition(), current, arrival, origin, nearby);
    }

    public static Result<LivingEntity> moveToPosition(LivingEntity traveller, ServerLevel destination, Arrival arrival) {
        return moveToPosition(traveller, destination, arrival, null);
    }

    public static Result<LivingEntity> moveToPosition(LivingEntity traveller, ServerLevel destination, Arrival arrival, @Nullable Vec3 origin) {
        return moveToPosition(traveller, destination, arrival, origin, false);
    }

    public static Result<LivingEntity> moveToPosition(LivingEntity traveller, ServerLevel destination, Arrival arrival, @Nullable Vec3 origin, boolean nearby) {
        if (!(traveller.level() instanceof ServerLevel)) return refused(Failures.SERVER_ONLY);
        SecretRealmRecord target = SecretRealmRegistry.at(destination.dimension()).orElse(null);
        SecretRealmRecord held = SecretRealmRegistry.ofMember(traveller.getUUID()).orElse(null);
        if (target != null && target.cleanup() != Cleanup.NONE) return refused(SecretRealmFailure.MISSING_DIMENSION);
        if (target != null && !(target.instance().generation() instanceof ExistingGeneration)
                && (held == null || !held.dimension().equals(target.dimension())))
            return enterInstance(traveller, target, arrival, origin, nearby);
        if (held != null && !held.dimension().equals(destination.dimension())) {
            SecretRealmTravelAttachment travel = travel(traveller);
            if (travel == null || !travel.active()) return refused(SecretRealmFailure.NOT_TRAVELLING);
            if (!held.instance().exitCondition().test(traveller, FormulaContext.of(traveller)))
                return refused(SecretRealmFailure.EXIT_DENIED, held.instance().exitDeniedMessage());
            Moved moved = leave(traveller, true, destination, arrival, nearby);
            return moved.result();
        }
        Result<RiftLanding.Inspection> landing = prepareArrival(destination, traveller, arrival.position(), nearby);
        if (landing instanceof Result.FailureResult<RiftLanding.Inspection>(Failure failure1)) {
            TravelDiagnostics.rejected(traveller, destination, arrival.position(), "position_landing", failure1.getSerializedName(),
                    "nearby=" + nearby + " primary=" + landing);
            return refused(failure1);
        }
        Vec3 landingPos = landing.getData().orElseThrow().position();
        LivingEntity moved = move(traveller, destination, landingPos.x, landingPos.y, landingPos.z, arrival.yaw(), arrival.pitch());
        return moved == null ? refused(SecretRealmFailure.TELEPORT_FAILED) : Result.success(moved);
    }

    private static Result<LivingEntity> enter(LivingEntity traveller, MinecraftServer server, Holder<SecretRealm> definition,
                                              @Nullable SecretRealmRecord selected, @Nullable Arrival fixedArrival, @Nullable Vec3 fixedOrigin, boolean nearby) {
        if (!(traveller.level() instanceof ServerLevel)) return refused(Failures.SERVER_ONLY);
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            // A token can outlive the definition it names, so the current registry is the last word on whether this realm
            // still exists. Kept here rather than at the token, because every caller has to be held to it.
            if (definition.unwrapKey().flatMap(server.overworld().registryAccess()::get).isEmpty())
                return refused(Failures.DISABLED);
            SecretRealmTravelAttachment travel = traveller.getData(MxtAttachments.SECRET_REALM_TRAVEL);
            if (travel.active()) return refused(SecretRealmFailure.ALREADY_TRAVELLING);

            UUID member = traveller.getUUID();
            // One clock for the whole lifecycle: every level shares the primary level data's game time, and the expiry
            // sweep reads it from the overworld, so a record's expiry has to be written from the same clock.
            long gameTime = server.overworld().getGameTime();
            SecretRealm value = definition.value();
            FormulaContext context = FormulaContext.of(traveller);
            if (!value.enterCondition().test(traveller, context))
                return refused(SecretRealmFailure.CONDITION_NOT_MET, value.enterDeniedMessage());

            // A clock that ran out while nobody was watching must not keep an instance alive.
            for (SecretRealmRecord candidate : SecretRealmRegistry.of(definition))
                if (candidate.expired(gameTime)) expire(server, candidate, gameTime);

            SecretRealmRecord record;
            boolean created = false;
            Optional<SecretRealmRecord> joinable = selected == null ? SecretRealmRegistry.joinable(definition, member) : Optional.of(selected);
            if (joinable.isPresent()) {
                record = joinable.get();
                if (!record.holds(member) && record.full()) return refused(SecretRealmFailure.FULL);
                if (record.empty()) record = record.restarted(gameTime);
            } else {
                if (SecretRealmRegistry.of(definition).size() >= value.maxInstances())
                    return refused(SecretRealmFailure.NO_FREE_INSTANCE);
                if (!SecretRealmStructurePlacer.resolvable(server.overworld().getStructureManager(), value))
                    return refused(SecretRealmFailure.MISSING_STRUCTURE);
                int index = SecretRealmRegistry.nextIndex(definition);
                long seed = value.seed() != 0L ? value.seed() : RandomSource.create().nextLong();
                record = plan(definition, index, seed, gameTime);
                if (SecretRealmRegistry.at(record.dimension()).isPresent())
                    return refused(SecretRealmFailure.NO_FREE_INSTANCE);
                created = true;
            }
            if (NeoForge.EVENT_BUS.post(new EnterPre(server, definition, record.dimension(), record.index(), record.owner(), member)).isCanceled())
                return refused(Failures.CANCELLED);
            if (value.owned() && record.owner().isEmpty()) record = record.withOwner(member);

            Optional<SecretRealmRecord> prepared = open(server, record);
            if (prepared.isEmpty()) return refused(SecretRealmFailure.GENERATION_FAILED);
            record = prepared.get();
            ServerLevel destination = server.getLevel(record.dimension());
            if (destination == null) {
                if (created) finishCleanup(server, record, Cleanup.ABORT);
                return refused(SecretRealmFailure.MISSING_DIMENSION);
            }
            // The teleport is the one step the platform can refuse, so it happens before anything is recorded: a failed
            // entrance leaves no member entry, no travel state and no enter events behind.
            List<UUID> members = new ArrayList<>(record.members());
            boolean joining = !members.contains(member);
            int slot = joining ? members.size() : members.indexOf(member);
            Arrival arrival = fixedArrival != null ? fixedArrival : SecretRealmEntryLocator.arrival(destination, record, slot, traveller.getYRot(), traveller.getXRot());
            {
                Result<RiftLanding.Inspection> landing = prepareArrival(destination, traveller, arrival.position(), nearby);
                if (landing instanceof Result.FailureResult<RiftLanding.Inspection>(Failure failure1)) {
                    TravelDiagnostics.rejected(traveller, destination, arrival.position(), "realm_entry_landing",
                            failure1.getSerializedName(), "realm=" + definition.unwrapKey().orElseThrow().identifier()
                                    + " instance=" + record.instanceId() + " nearby=" + nearby + " primary=" + landing);
                    if (created && record.empty()) finishCleanup(server, record, Cleanup.ABORT);
                    return refused(failure1);
                }
                arrival = new Arrival(landing.getData().orElseThrow().position(), arrival.yaw(), arrival.pitch());
            }
            Identifier originDimension = traveller.level().dimension().identifier();
            double originX = fixedOrigin == null ? traveller.getX() : fixedOrigin.x;
            double originY = fixedOrigin == null ? traveller.getY() : fixedOrigin.y;
            double originZ = fixedOrigin == null ? traveller.getZ() : fixedOrigin.z;
            float originYaw = traveller.getYRot();
            float originPitch = traveller.getXRot();
            LivingEntity arrived = move(traveller, destination, arrival.position().x, arrival.position().y,
                    arrival.position().z, arrival.yaw(), arrival.pitch());
            if (arrived == null) {
                if (created && record.empty()) finishCleanup(server, record, Cleanup.ABORT);
                return refused(SecretRealmFailure.TELEPORT_FAILED);
            }
            if (joining) {
                members.add(member);
                record = record.with(members);
                SecretRealmRegistry.replace(record);
            }
            // Written once the traveller is really there: the origin is what an exit and an orphan sweep come back to.
            // The state goes on the body that landed, which is not the one that left when the platform had to rebuild it.
            arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL)
                    .begin(definition, originDimension, originX, originY, originZ, originYaw, originPitch);
            if (created) NeoForge.EVENT_BUS.post(new Create(server, definition, record.dimension(), record.index(), record.owner()));
            NeoForge.EVENT_BUS.post(new EnterPost(server, definition, record.dimension(), record.index(), record.owner(), member));
            value.enterAction().execute(arrived, FormulaContext.of(arrived));
            return Result.success(arrived);
        }
    }

    public static Result<LivingEntity> exit(LivingEntity traveller) {
        if (!(traveller.level() instanceof ServerLevel)) return refused(Failures.SERVER_ONLY);
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            SecretRealmTravelAttachment travel = travel(traveller);
            Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
            if (definition == null || !travel.active()) return refused(SecretRealmFailure.NOT_TRAVELLING);
            SecretRealm value = definition.value();
            if (!value.exitCondition().test(traveller, FormulaContext.of(traveller)))
                return refused(SecretRealmFailure.EXIT_DENIED, value.exitDeniedMessage());
            Moved moved = leave(traveller);
            return moved.result();
        }
    }

    // Also the exit used by an expiry and by an administrator, which is why it never consults the exit
    // condition: a definition must not be able to lock a traveller inside a secret realm forever. A null failure
    // means the traveller is out (or had nothing to undo); anything else is a refusal, and nothing is cleared for it,
    // so the record still says where they are and a later attempt can try again.
    private static Moved leave(LivingEntity traveller) {
        return leave(traveller, true);
    }

    // The exit itself, with the realm's fate decided by the caller or not. A forced destroy moves every member out and
    // then retires the record once on its own, so a member's exit on that path must neither retire it early nor post a
    // Destroy of its own - which is what "settle" switches off.
    private static Moved leave(LivingEntity traveller, boolean settle) {
        return leave(traveller, settle, null, null, true);
    }

    private static Moved leave(LivingEntity traveller, boolean settle, @Nullable ServerLevel destination, @Nullable Arrival arrival, boolean nearby) {
        if (!(traveller.level() instanceof ServerLevel level)) return Moved.refused(Failures.SERVER_ONLY);
        SecretRealmTravelAttachment travel = travel(traveller);
        Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Moved.arrived(traveller);
        MinecraftServer server = level.getServer();
        ServerLevel origin = destination == null ? origin(server, travel).orElse(null) : destination;
        if (origin == null) return Moved.refused(SecretRealmFailure.MISSING_ORIGIN);
        Optional<SecretRealmRecord> held = SecretRealmRegistry.ofMember(traveller.getUUID());
        Arrival requested = arrival == null ? new Arrival(new Vec3(travel.originX(), travel.originY(), travel.originZ()),
                travel.originYaw(), travel.originPitch()) : arrival;
        Result<RiftLanding.Inspection> landing = prepareArrival(origin, traveller, requested.position(), nearby);
        if (landing instanceof Result.FailureResult<RiftLanding.Inspection>(Failure failure1)) {
            TravelDiagnostics.rejected(traveller, origin, requested.position(), "realm_exit_landing", failure1.getSerializedName(),
                    "realm=" + definition.unwrapKey().orElseThrow().identifier() + " primary=" + landing);
            return Moved.refused(failure1);
        }
        Vec3 landingPos = landing.getData().orElseThrow().position();
        LivingEntity arrived = move(traveller, origin, landingPos.x, landingPos.y, landingPos.z, requested.yaw(), requested.pitch());
        if (arrived == null) return Moved.refused(SecretRealmFailure.TELEPORT_FAILED);
        arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL).clear();
        if (held.isPresent()) {
            SecretRealmRecord record = held.get();
            List<UUID> members = new ArrayList<>(record.members());
            members.remove(traveller.getUUID());
            SecretRealmRecord updated = record.with(members);
            // A forced destroy settles the record itself, once every member is out: retiring here would take the
            // record away before it had its say, and post the same Destroy the caller is about to post.
            SecretRealmRegistry.replace(updated);
            if (settle && updated.empty()) retire(server, updated);
            NeoForge.EVENT_BUS.post(new Exit(server, definition, record.dimension(), record.index(), record.owner(), traveller.getUUID()));
            definition.value().exitAction().execute(arrived, FormulaContext.of(arrived));
        }
        return Moved.arrived(arrived);
    }

    // A non-player crossing a dimension is rebuilt by the platform: the body that lands in the destination is a new
    // entity and the one handed in is removed, so everything after a move belongs to the returned body. The boolean
    // form of Entity#teleportTo discards that answer, which is why the transition is built here by hand. A player is
    // teleported in place and comes back as itself, and null means the platform refused the move.
    private static @Nullable LivingEntity move(LivingEntity traveller, ServerLevel destination, double x, double y,
                                               double z, float yaw, float pitch) {
        Vec3 position = new Vec3(x, y, z);
        String reason = !(traveller.level() instanceof ServerLevel) ? "client_side"
                : !((ServerLevel) traveller.level()).isAllowedToEnterPortal(destination) ? "portal_rule_denied"
                : !traveller.isAlive() || traveller.isRemoved() ? "entity_unavailable"
                : traveller.isPassenger() ? "passenger"
                : traveller.isVehicle() ? "carrying_passengers"
                : !traveller.canUsePortal(false) ? "entity_cannot_use_portal"
                : !traveller.canTeleport(traveller.level(), destination) ? "entity_cannot_change_dimension" : null;
        if (reason != null) {
            TravelDiagnostics.rejected(traveller, destination, position, "teleport_precheck", reason, "yaw=" + yaw + " pitch=" + pitch);
            return null;
        }
        TeleportTransition transition = new TeleportTransition(destination, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                Set.of(), TeleportTransition.DO_NOTHING);
        Entity moved = traveller.teleport(transition);
        if (moved instanceof LivingEntity living) return living;
        TravelDiagnostics.rejected(traveller, destination, position, "teleport", moved == null ? "platform_refused" : "unexpected_entity_type",
                "yaw=" + yaw + " pitch=" + pitch + " result=" + moved);
        return null;
    }

    public static Result<RiftLanding.Inspection> checkArrival(ServerLevel level, Entity traveller, Vec3 position, boolean nearby) {
        RiftLanding.loadArea(level, traveller, position, nearby);
        return RiftLanding.search(level, traveller, position, nearby);
    }

    private static Result<RiftLanding.Inspection> prepareArrival(ServerLevel level, Entity traveller, Vec3 position, boolean nearby) {
        RiftLanding.loadArea(level, traveller, position, nearby);
        return RiftLanding.resolve(level, traveller, position, nearby);
    }

    // Only a read: a traveller that never entered a secret realm should not be handed an attachment for asking.
    private static @Nullable SecretRealmTravelAttachment travel(LivingEntity traveller) {
        return traveller.getExistingData(MxtAttachments.SECRET_REALM_TRAVEL).orElse(null);
    }

    // A member can be a player or anything else living: a player is found through the player list even when their
    // chunk is unloaded, everything else through the level it is loaded in.
    private static @Nullable LivingEntity findMember(MinecraftServer server, UUID id) {
        LivingEntity player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(id) instanceof LivingEntity living) return living;
        return null;
    }

    public static boolean expire(MinecraftServer server, SecretRealmRecord record, long gameTime) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            SecretRealmRecord current = current(record);
            if (current == null) return false;
            if (current.cleanup() != Cleanup.NONE) return finishCleanup(server, current, current.cleanup());
            if (!current.expired(gameTime) || !evacuate(server, current)) return false;
            SecretRealmRecord remaining = current(record);
            return remaining != null && retire(server, remaining);
        }
    }

    public static boolean destroy(MinecraftServer server, SecretRealmRecord record) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            SecretRealmRecord current = current(record);
            return current != null && finishCleanup(server, current, Cleanup.DESTROY);
        }
    }

    private static boolean evacuate(MinecraftServer server, SecretRealmRecord record) {
        boolean evacuated = true;
        for (UUID id : record.members()) {
            LivingEntity member = findMember(server, id);
            if (member != null && leave(member, false).failure() != null) {
                evacuated = false;
                continue;
            }
            SecretRealmRecord current = current(record);
            if (current == null || !current.holds(id)) continue;
            List<UUID> remaining = new ArrayList<>(current.members());
            remaining.remove(id);
            SecretRealmRegistry.replace(current.with(remaining));
        }
        return evacuated;
    }

    private static @Nullable SecretRealmRecord current(SecretRealmRecord record) {
        return SecretRealmRegistry.at(record.dimension())
                .filter(value -> value.instanceId().equals(record.instanceId())).orElse(null);
    }

    // For a traveller whose instance no longer holds them: what a destroyed or expired secret realm leaves behind
    // for one that was not loaded at the time (an offline player, or any other kind of traveller).
    public static boolean returnIfOrphaned(LivingEntity traveller) {
        if (!(traveller.level() instanceof ServerLevel level)) return false;
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            SecretRealmTravelAttachment travel = travel(traveller);
            if (travel == null || !travel.active()) return false;
            if (SecretRealmRegistry.ofMember(traveller.getUUID()).isPresent()) return false;
            MinecraftServer server = level.getServer();
            ServerLevel origin = origin(server, travel).orElse(null);
            if (origin == null) return false;
            LivingEntity arrived = move(traveller, origin, travel.originX(), travel.originY(), travel.originZ(),
                    travel.originYaw(), travel.originPitch());
            if (arrived == null) return false;
            arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL).clear();
            return true;
        }
    }

    // Identity, seed and clock only: the part of an entry that needs no player, so a script can create a secret realm
    // and decide separately who goes in.
    public static SecretRealmRecord plan(Holder<SecretRealm> definition, int index, long seed, long gameTime) {
        SecretRealm value = definition.value();
        ResourceKey<Level> dimension = SecretRealmGenerationService.dimensionKey(HolderHelper.id(definition), value.generation(), index);
        long expiresAt = value.durationTicks() <= 0L ? -1L : gameTime + value.durationTicks();
        return new SecretRealmRecord(definition, index, dimension, OptionalLong.of(seed), Optional.empty(), gameTime, expiresAt, Optional.empty(), false, List.of());
    }

    // The half of an entry that does not need a player, so scripts and diagnostics can open a secret realm without
    // sending anybody into it.
    public static Optional<SecretRealmRecord> open(MinecraftServer server, SecretRealmRecord record) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            SecretRealmRecord previous = SecretRealmRegistry.at(record.dimension()).orElse(null);
            if (previous != null && (!previous.instanceId().equals(record.instanceId()) || previous.cleanup() != Cleanup.NONE))
                return Optional.empty();
            if (!(record.instance().generation() instanceof ExistingGeneration)) {
                if (!SecretRealmGenerationService.managed(server, record.dimension())) return Optional.empty();
                ServerLevel loaded = server.getLevel(record.dimension());
                if (loaded != null && !RuntimeDimensionService.owns(loaded)) return Optional.empty();
            }
            SecretRealmRegistry.put(record);
            boolean prepared = false;
            try {
                Optional<ServerLevel> opened = SecretRealmGenerationService.open(server, record);
                if (opened.isEmpty()) return Optional.empty();
                SecretRealmRecord result = record;
                if (!record.prepared()) {
                    ServerLevel level = opened.get();
                    Landing landing = SecretRealmEntryLocator.plan(level, record);
                    SecretRealmStructurePlacer.place(level, record, landing.position());
                    result = record.withAnchor(SecretRealmEntryLocator.finish(level, landing));
                    if (!RiftService.layout(level, record.instanceId().toString(),
                            result.anchor().orElseThrow(), result.instance().portals())) return Optional.empty();
                    result = result.asPrepared();
                }
                Optional<SecretRealmRecord> registered = SecretRealmRegistry.replace(result);
                prepared = registered.isPresent();
                return registered;
            } catch (RuntimeException failure) {
                LOGGER.warn("Failed to prepare secret realm {}", record.dimension().identifier(), failure);
                return Optional.empty();
            } finally {
                if (!prepared && current(record) != null) {
                    if (previous != null && previous.prepared()) SecretRealmRegistry.replace(previous);
                    else finishCleanup(server, record, Cleanup.ABORT);
                }
            }
        }
    }

    private static boolean retire(MinecraftServer server, SecretRealmRecord record) {
        Cleanup cleanup = record.cleanup() != Cleanup.NONE ? record.cleanup() : record.persists() ? Cleanup.IDLE : Cleanup.DESTROY;
        return finishCleanup(server, record, cleanup);
    }

    private static boolean finishCleanup(MinecraftServer server, SecretRealmRecord record, Cleanup cleanup) {
        Set<UUID> active = CLEANING.get();
        if (!active.add(record.instanceId())) return false;
        try {
            return cleanInstance(server, record, cleanup);
        } finally {
            active.remove(record.instanceId());
            if (active.isEmpty()) CLEANING.remove();
        }
    }

    private static boolean cleanInstance(MinecraftServer server, SecretRealmRecord record, Cleanup cleanup) {
        SecretRealmRecord current = current(record);
        if (current == null) return false;
        if (current.cleanup() != cleanup && SecretRealmRegistry.replace(current.withCleanup(cleanup)).isEmpty()) return false;
        if (!evacuate(server, current)) return false;
        current = current(record);
        if (current == null) return false;
        boolean existing = current.instance().generation() instanceof ExistingGeneration;
        boolean clean = existing || (cleanup == Cleanup.IDLE ? RuntimeDimensionService.unload(server, current.dimension())
                : RuntimeDimensionService.delete(server, current.dimension()));
        if (!clean) return false;
        ServerLevel existingLevel = existing ? server.getLevel(current.dimension()) : null;
        if (existingLevel != null && cleanup != Cleanup.IDLE) {
            try {
                RiftService.removeLayout(existingLevel, current.instanceId().toString(), current.instance().portals());
            } catch (RuntimeException failure) {
                LOGGER.warn("Failed to remove the portal layout of {}", current.dimension().identifier(), failure);
                return false;
            }
        }
        if (cleanup == Cleanup.IDLE) SecretRealmRegistry.replace(current.idle());
        else SecretRealmRegistry.remove(current.dimension());
        if (cleanup != Cleanup.ABORT)
            NeoForge.EVENT_BUS.post(new Destroy(server, current.definition(), current.dimension(), current.index(), current.owner()));
        return true;
    }

    private static Optional<ServerLevel> origin(MinecraftServer server, SecretRealmTravelAttachment travel) {
        return travel.originDimension().map(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    // The custom denial text a definition may name becomes the failure's own message.
    private static Result<LivingEntity> refused(Failure failure) {
        return Result.failure(failure);
    }

    private static Result<LivingEntity> refused(Failure failure, Optional<Component> message) {
        return Result.failure(message.map(text -> (Failure) new MessageFailure(failure, text)).orElse(failure));
    }

    // The answer to a move out: the refusal, and the body that came out - a non-player crossing a dimension is
    // rebuilt, so it is not always the entity handed in.
    private record Moved(@Nullable Failure failure, @Nullable LivingEntity traveller) {
        Result<LivingEntity> result() {
            if (this.failure != null) return SecretRealmService.refused(this.failure);
            assert this.traveller != null;
            return Result.success(this.traveller);
        }

        static Moved refused(Failure failure) {
            return new Moved(failure, null);
        }

        static Moved arrived(LivingEntity traveller) {
            return new Moved(null, traveller);
        }
    }
}
