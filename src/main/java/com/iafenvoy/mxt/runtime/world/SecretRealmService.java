package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Existing;
import com.iafenvoy.mxt.event.SecretRealmEvent.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator.Arrival;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator.Landing;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Authoritative secret realm membership and cross-dimension travel.
 * <p>
 * Entering resolves to an existing instance when it has room, otherwise to a new instance dimension until
 * {@code max_instances} is reached. The dimension is created before anybody arrives, so structures are placed
 * and the landing spot is chosen on finished terrain.
 */
public final class SecretRealmService {
    private SecretRealmService() {
    }

    public static Result enter(LivingEntity traveller, Holder<SecretRealm> definition) {
        Identifier id = HolderHelper.id(definition);
        if (MxtDatapackRegistries.holder(MxtResourceKeys.SECRET_REALM, id).isEmpty())
            return Result.rejected(Failure.DISABLED);
        SecretRealmTravelAttachment travel = traveller.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        if (travel.active()) return Result.rejected(Failure.ALREADY_TRAVELLING);

        MinecraftServer server = traveller.level().getServer();
        UUID member = traveller.getUUID();
        long gameTime = traveller.level().getGameTime();
        SecretRealm value = definition.value();
        FormulaContext context = FormulaContext.of(traveller);
        if (!value.enterCondition().test(traveller, context))
            return Result.rejected(Failure.CONDITION_NOT_MET, value.enterDeniedMessage());

        // A clock that ran out while nobody was watching must not keep an instance alive.
        for (SecretRealmRecord candidate : SecretRealmRegistry.of(definition))
            if (candidate.expired(gameTime)) expire(server, candidate, gameTime);

        SecretRealmRecord record;
        boolean created = false;
        Optional<SecretRealmRecord> joinable = SecretRealmRegistry.joinable(definition, member);
        if (joinable.isPresent()) {
            record = joinable.get();
            if (!record.holds(member) && record.full()) return Result.rejected(Failure.FULL);
            if (record.empty()) record = record.restarted(gameTime);
        } else {
            if (SecretRealmRegistry.of(definition).size() >= value.maxInstances())
                return Result.rejected(Failure.NO_FREE_INSTANCE);
            if (!SecretRealmStructurePlacer.resolvable(server.overworld().getStructureManager(), value))
                return Result.rejected(Failure.MISSING_STRUCTURE);
            int index = SecretRealmRegistry.nextIndex(definition);
            long seed = value.seed() != 0L ? value.seed() : RandomSource.create().nextLong();
            record = plan(definition, index, seed, gameTime);
            if (SecretRealmRegistry.at(record.dimension()).isPresent())
                return Result.rejected(Failure.NO_FREE_INSTANCE);
            created = true;
        }
        if (NeoForge.EVENT_BUS.post(new EnterPre(server, definition, record.dimension(), record.index(), record.owner(), member)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        if (value.owned() && record.owner().isEmpty()) record = record.withOwner(member);

        Optional<SecretRealmRecord> prepared = open(server, record);
        if (prepared.isEmpty()) return Result.rejected(Failure.GENERATION_FAILED);
        record = prepared.get();
        ServerLevel destination = server.getLevel(record.dimension());
        if (destination == null) {
            SecretRealmRegistry.remove(record.dimension());
            return Result.rejected(Failure.MISSING_DIMENSION);
        }
        if (created)
            NeoForge.EVENT_BUS.post(new Create(server, definition, record.dimension(), record.index(), record.owner()));

        if (!record.holds(member)) {
            List<UUID> members = new ArrayList<>(record.members());
            members.add(member);
            record = record.with(members);
            SecretRealmRegistry.replace(record);
        }
        int slot = Math.max(0, record.members().indexOf(member));
        Arrival arrival = SecretRealmEntryLocator.arrival(destination, record, slot, traveller.getYRot(), traveller.getXRot());
        travel.begin(definition, traveller.level().dimension().identifier(), traveller.getX(), traveller.getY(), traveller.getZ(), traveller.getYRot(), traveller.getXRot());
        traveller.teleportTo(destination, arrival.position().x, arrival.position().y, arrival.position().z, Set.of(), arrival.yaw(), arrival.pitch(), false);
        NeoForge.EVENT_BUS.post(new EnterPost(server, definition, record.dimension(), record.index(), record.owner(), member));
        value.enterAction().execute(traveller, context);
        return Result.entered();
    }

    public static Result exit(LivingEntity traveller) {
        SecretRealmTravelAttachment travel = travel(traveller);
        Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Result.rejected(Failure.NOT_TRAVELLING);
        SecretRealm value = definition.value();
        if (!value.exitCondition().test(traveller, FormulaContext.of(traveller)))
            return Result.rejected(Failure.EXIT_DENIED, value.exitDeniedMessage());
        return leave(traveller) ? Result.exited() : Result.rejected(Failure.MISSING_ORIGIN);
    }

    // Also the exit used by an expiry and by an administrator, which is why it never consults the exit
    // condition: a definition must not be able to lock a traveller inside a secret realm forever.
    private static boolean leave(LivingEntity traveller) {
        SecretRealmTravelAttachment travel = travel(traveller);
        Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
        if (definition == null || !travel.active()) return false;
        MinecraftServer server = traveller.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        Optional<SecretRealmRecord> held = SecretRealmRegistry.ofMember(traveller.getUUID());
        traveller.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        if (held.isPresent()) {
            SecretRealmRecord record = held.get();
            List<UUID> members = new ArrayList<>(record.members());
            members.remove(traveller.getUUID());
            SecretRealmRecord updated = record.with(members);
            if (updated.empty()) retire(server, updated);
            else SecretRealmRegistry.replace(updated);
            NeoForge.EVENT_BUS.post(new Exit(server, definition, record.dimension(), record.index(), record.owner(), traveller.getUUID()));
            definition.value().exitAction().execute(traveller, FormulaContext.of(traveller));
        }
        return true;
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
        if (!record.expired(gameTime)) return false;
        for (UUID id : List.copyOf(record.members())) {
            LivingEntity member = findMember(server, id);
            if (member != null) {
                leave(member);
                continue;
            }
            SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
            if (current == null) return true;
            List<UUID> members = new ArrayList<>(current.members());
            members.remove(id);
            SecretRealmRegistry.replace(current.with(members));
        }
        SecretRealmRegistry.at(record.dimension()).ifPresent(current -> retire(server, current));
        return true;
    }

    // By force: the terrain is discarded even when the instance was claimed.
    public static boolean destroy(MinecraftServer server, SecretRealmRecord record) {
        for (UUID id : List.copyOf(record.members())) {
            LivingEntity member = findMember(server, id);
            if (member != null) leave(member);
        }
        SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
        if (current == null) return false;
        SecretRealmRegistry.remove(current.dimension());
        if (!(current.instance().generation() instanceof Existing))
            RuntimeDimensionService.delete(server, current.dimension());
        NeoForge.EVENT_BUS.post(new Destroy(server, current.definition(), current.dimension(), current.index(), current.owner()));
        return true;
    }

    // For a traveller whose instance no longer holds them: what a destroyed or expired secret realm leaves behind
    // for one that was not loaded at the time (an offline player, or any other kind of traveller).
    public static boolean returnIfOrphaned(LivingEntity traveller) {
        SecretRealmTravelAttachment travel = travel(traveller);
        if (travel == null || !travel.active()) return false;
        if (SecretRealmRegistry.ofMember(traveller.getUUID()).isPresent()) return false;
        MinecraftServer server = traveller.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        traveller.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        return true;
    }

    // Identity, seed and clock only: the part of an entry that needs no player, so a script can create a secret realm
    // and decide separately who goes in.
    public static SecretRealmRecord plan(Holder<SecretRealm> definition, int index, long seed, long gameTime) {
        SecretRealm value = definition.value();
        ResourceKey<Level> dimension = SecretRealmGenerationService.dimensionKey(HolderHelper.id(definition), value.generation(), index);
        long expiresAt = value.durationTicks() <= 0L ? -1L : gameTime + value.durationTicks();
        return new SecretRealmRecord(definition, index, dimension, seed, Optional.empty(), gameTime, expiresAt,
                Optional.empty(), false, List.of());
    }

    // The half of an entry that does not need a player, so scripts and diagnostics can open a secret realm without
    // sending anybody into it.
    public static Optional<SecretRealmRecord> open(MinecraftServer server, SecretRealmRecord record) {
        // The table is written before the level is built: the seed mixin and the registry both answer for this
        // dimension while it is being constructed.
        SecretRealmRegistry.put(record);
        Optional<ServerLevel> opened = SecretRealmGenerationService.open(server, record);
        if (opened.isEmpty()) {
            SecretRealmRegistry.remove(record.dimension());
            return Optional.empty();
        }
        ServerLevel level = opened.get();
        SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(record);
        if (current.prepared()) return Optional.of(current);
        Landing landing = SecretRealmEntryLocator.plan(level, current);
        SecretRealmStructurePlacer.place(level, current, landing.position());
        current = current.withAnchor(SecretRealmEntryLocator.finish(level, landing)).asPrepared();
        return SecretRealmRegistry.replace(current);
    }

    // When the last member leaves: a claimable secret realm and one built on an existing dimension keep their terrain
    // and are merely unloaded, while a plain instance is destroyed with its region files.
    private static void retire(MinecraftServer server, SecretRealmRecord record) {
        boolean keep = record.persists();
        if (keep) {
            SecretRealmRegistry.replace(record.idle());
            if (!(record.instance().generation() instanceof Existing))
                RuntimeDimensionService.unload(server, record.dimension());
        } else {
            SecretRealmRegistry.remove(record.dimension());
            RuntimeDimensionService.delete(server, record.dimension());
        }
        NeoForge.EVENT_BUS.post(new Destroy(server, record.definition(), record.dimension(), record.index(), record.owner()));
    }

    private static Optional<ServerLevel> origin(MinecraftServer server, SecretRealmTravelAttachment travel) {
        return travel.originDimension().map(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    public enum Failure {
        DISABLED, FULL, CANCELLED, MISSING_DIMENSION, ALREADY_TRAVELLING,
        NOT_TRAVELLING, MISSING_ORIGIN, GENERATION_FAILED, MISSING_STRUCTURE, NO_FREE_INSTANCE,
        CONDITION_NOT_MET, EXIT_DENIED
    }

    public record Result(boolean changed, Failure failure, Optional<Component> message) {
        static Result entered() {
            return new Result(true, null, Optional.empty());
        }

        static Result exited() {
            return new Result(true, null, Optional.empty());
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure, Optional.empty());
        }

        static Result rejected(Failure failure, Optional<Component> message) {
            return new Result(false, failure, message);
        }
    }
}
