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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

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

    public static Result enter(ServerPlayer player, Holder<SecretRealm> definition) {
        Identifier id = HolderHelper.id(definition);
        if (MxtDatapackRegistries.holder(MxtResourceKeys.SECRET_REALM, id).isEmpty())
            return Result.rejected(Failure.DISABLED);
        SecretRealmTravelAttachment travel = player.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        if (travel.active()) return Result.rejected(Failure.ALREADY_TRAVELLING);

        MinecraftServer server = player.level().getServer();
        UUID member = player.getUUID();
        long gameTime = player.level().getGameTime();
        SecretRealm value = definition.value();
        FormulaContext context = FormulaContext.of(player);
        if (!value.enterCondition().test(player, context))
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
            if (SecretRealmRegistry.at(record.dimension()).isPresent()) return Result.rejected(Failure.NO_FREE_INSTANCE);
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
        Arrival arrival = SecretRealmEntryLocator.arrival(destination, record, slot, player.getYRot(), player.getXRot());
        travel.begin(definition, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.teleportTo(destination, arrival.position().x, arrival.position().y, arrival.position().z, Set.of(), arrival.yaw(), arrival.pitch(), false);
        NeoForge.EVENT_BUS.post(new EnterPost(server, definition, record.dimension(), record.index(), record.owner(), member));
        value.enterAction().execute(player, context);
        return Result.entered();
    }

    public static Result exit(ServerPlayer player) {
        SecretRealmTravelAttachment travel = player.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        Holder<SecretRealm> definition = travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Result.rejected(Failure.NOT_TRAVELLING);
        SecretRealm value = definition.value();
        if (!value.exitCondition().test(player, FormulaContext.of(player)))
            return Result.rejected(Failure.EXIT_DENIED, value.exitDeniedMessage());
        return leave(player) ? Result.exited() : Result.rejected(Failure.MISSING_ORIGIN);
    }

    // Also the exit used by an expiry and by an administrator, which is why it never consults the exit
    // condition: a definition must not be able to lock a player inside a secret realm forever.
    private static boolean leave(ServerPlayer player) {
        SecretRealmTravelAttachment travel = player.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        Holder<SecretRealm> definition = travel.realm().orElse(null);
        if (definition == null || !travel.active()) return false;
        MinecraftServer server = player.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        Optional<SecretRealmRecord> held = SecretRealmRegistry.ofMember(player.getUUID());
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        if (held.isPresent()) {
            SecretRealmRecord record = held.get();
            List<UUID> members = new ArrayList<>(record.members());
            members.remove(player.getUUID());
            SecretRealmRecord updated = record.with(members);
            if (updated.empty()) retire(server, updated);
            else SecretRealmRegistry.replace(updated);
            NeoForge.EVENT_BUS.post(new Exit(server, definition, record.dimension(), record.index(), record.owner(), player.getUUID()));
            definition.value().exitAction().execute(player, FormulaContext.of(player));
        }
        return true;
    }

    public static boolean expire(MinecraftServer server, SecretRealmRecord record, long gameTime) {
        if (!record.expired(gameTime)) return false;
        for (UUID member : List.copyOf(record.members())) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                leave(player);
                continue;
            }
            SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
            if (current == null) return true;
            List<UUID> members = new ArrayList<>(current.members());
            members.remove(member);
            SecretRealmRegistry.replace(current.with(members));
        }
        SecretRealmRegistry.at(record.dimension()).ifPresent(current -> retire(server, current));
        return true;
    }

    // By force: the terrain is discarded even when the instance was claimed.
    public static boolean destroy(MinecraftServer server, SecretRealmRecord record) {
        for (UUID member : List.copyOf(record.members())) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) leave(player);
        }
        SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
        if (current == null) return false;
        SecretRealmRegistry.remove(current.dimension());
        if (!(current.instance().generation() instanceof Existing))
            RuntimeDimensionService.delete(server, current.dimension());
        NeoForge.EVENT_BUS.post(new Destroy(server, current.definition(), current.dimension(), current.index(), current.owner()));
        return true;
    }

    // For a traveller whose instance no longer holds them: what a destroyed or expired secret realm leaves behind for
    // a player who was offline at the time.
    public static boolean returnIfOrphaned(ServerPlayer player) {
        SecretRealmTravelAttachment travel = player.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        if (!travel.active()) return false;
        if (SecretRealmRegistry.ofMember(player.getUUID()).isPresent()) return false;
        MinecraftServer server = player.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
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
        DISABLED, OTHER_INSTANCE, FULL, CANCELLED, NOT_MEMBER, MISSING_DIMENSION, ALREADY_TRAVELLING,
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
