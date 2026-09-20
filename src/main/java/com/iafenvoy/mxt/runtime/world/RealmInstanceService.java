package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.RealmTravelAttachment;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Existing;
import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.event.RealmInstanceEvent.Create;
import com.iafenvoy.mxt.event.RealmInstanceEvent.Destroy;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPost;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPre;
import com.iafenvoy.mxt.event.RealmInstanceEvent.Exit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.RealmEntryLocator.Arrival;
import com.iafenvoy.mxt.runtime.world.RealmEntryLocator.Landing;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative realm membership and cross-dimension travel.
 *
 * <p>Entering resolves to an instance first and to a dimension second: an existing instance is joined when it
 * has room, otherwise a new instance dimension is created until the definition's {@code max_instances} is
 * reached. The dimension is created before anybody arrives so that structures are placed and the landing spot
 * is chosen on finished terrain.
 */
public final class RealmInstanceService {
    private RealmInstanceService() {
    }

    public static Result enter(ServerPlayer player, Holder<RealmInstance> definition) {
        Identifier id = HolderHelper.id(definition);
        if (MxtDatapackRegistries.holder(MxtResourceKeys.REALM_INSTANCE, id).isEmpty())
            return Result.rejected(Failure.DISABLED);
        RealmTravelAttachment travel = player.getData(MxtAttachments.REALM_TRAVEL);
        if (travel.active()) return Result.rejected(Failure.ALREADY_TRAVELLING);

        MinecraftServer server = player.level().getServer();
        UUID member = player.getUUID();
        long gameTime = player.level().getGameTime();
        RealmInstance value = definition.value();
        FormulaContext context = FormulaContext.of(player);
        if (!value.enterCondition().test(player, context))
            return Result.rejected(Failure.CONDITION_NOT_MET, value.enterDeniedMessage());

        // A clock that ran out while nobody was watching must not keep an instance alive.
        for (RealmRecord candidate : RealmInstanceRegistry.of(definition))
            if (candidate.expired(gameTime)) expire(server, candidate, gameTime);

        RealmRecord record;
        boolean created = false;
        Optional<RealmRecord> joinable = RealmInstanceRegistry.joinable(definition, member);
        if (joinable.isPresent()) {
            record = joinable.get();
            if (!record.holds(member) && record.full()) return Result.rejected(Failure.FULL);
            if (record.empty()) record = record.restarted(gameTime);
        } else {
            if (RealmInstanceRegistry.of(definition).size() >= value.maxInstances())
                return Result.rejected(Failure.NO_FREE_INSTANCE);
            if (!RealmStructurePlacer.resolvable(server.overworld().getStructureManager(), value))
                return Result.rejected(Failure.MISSING_STRUCTURE);
            int index = RealmInstanceRegistry.nextIndex(definition);
            long seed = value.seed() != 0L ? value.seed() : RandomSource.create().nextLong();
            record = plan(definition, index, seed, gameTime);
            if (RealmInstanceRegistry.at(record.dimension()).isPresent()) return Result.rejected(Failure.NO_FREE_INSTANCE);
            created = true;
        }
        if (NeoForge.EVENT_BUS.post(new EnterPre(server, definition, record.dimension(), record.index(), record.owner(), member)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        if (value.owned() && record.owner().isEmpty()) record = record.withOwner(member);

        Optional<RealmRecord> prepared = open(server, record);
        if (prepared.isEmpty()) return Result.rejected(Failure.GENERATION_FAILED);
        record = prepared.get();
        ServerLevel destination = server.getLevel(record.dimension());
        if (destination == null) {
            RealmInstanceRegistry.remove(record.dimension());
            return Result.rejected(Failure.MISSING_DIMENSION);
        }
        if (created)
            NeoForge.EVENT_BUS.post(new Create(server, definition, record.dimension(), record.index(), record.owner()));

        if (!record.holds(member)) {
            List<UUID> members = new ArrayList<>(record.members());
            members.add(member);
            record = record.with(members);
            RealmInstanceRegistry.replace(record);
        }
        int slot = Math.max(0, record.members().indexOf(member));
        Arrival arrival = RealmEntryLocator.arrival(destination, record, slot, player.getYRot(), player.getXRot());
        travel.begin(definition, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.teleportTo(destination, arrival.position().x, arrival.position().y, arrival.position().z, Set.of(), arrival.yaw(), arrival.pitch(), false);
        NeoForge.EVENT_BUS.post(new EnterPost(server, definition, record.dimension(), record.index(), record.owner(), member));
        value.enterAction().execute(player, context);
        return Result.entered();
    }

    /**
     * Leaves the realm the player is inside, honouring the definition's exit condition.
     */
    public static Result exit(ServerPlayer player) {
        RealmTravelAttachment travel = player.getData(MxtAttachments.REALM_TRAVEL);
        Holder<RealmInstance> definition = travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Result.rejected(Failure.NOT_TRAVELLING);
        RealmInstance value = definition.value();
        if (!value.exitCondition().test(player, FormulaContext.of(player)))
            return Result.rejected(Failure.EXIT_DENIED, value.exitDeniedMessage());
        return leave(player) ? Result.exited() : Result.rejected(Failure.MISSING_ORIGIN);
    }

    /**
     * Returns a player to the exact origin captured when they entered. This is also the exit used by an expiry
     * and by an administrator, which is why it never consults the exit condition: a definition must not be able
     * to lock a player inside a realm forever.
     */
    private static boolean leave(ServerPlayer player) {
        RealmTravelAttachment travel = player.getData(MxtAttachments.REALM_TRAVEL);
        Holder<RealmInstance> definition = travel.realm().orElse(null);
        if (definition == null || !travel.active()) return false;
        MinecraftServer server = player.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        Optional<RealmRecord> held = RealmInstanceRegistry.ofMember(player.getUUID());
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        if (held.isPresent()) {
            RealmRecord record = held.get();
            List<UUID> members = new ArrayList<>(record.members());
            members.remove(player.getUUID());
            RealmRecord updated = record.with(members);
            if (updated.empty()) retire(server, updated);
            else RealmInstanceRegistry.replace(updated);
            NeoForge.EVENT_BUS.post(new Exit(server, definition, record.dimension(), record.index(), record.owner(), player.getUUID()));
            definition.value().exitAction().execute(player, FormulaContext.of(player));
        }
        return true;
    }

    /**
     * Ends an instance whose clock ran out, sending everyone home.
     */
    public static boolean expire(MinecraftServer server, RealmRecord record, long gameTime) {
        if (!record.expired(gameTime)) return false;
        for (UUID member : List.copyOf(record.members())) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                leave(player);
                continue;
            }
            RealmRecord current = RealmInstanceRegistry.at(record.dimension()).orElse(null);
            if (current == null) return true;
            List<UUID> members = new ArrayList<>(current.members());
            members.remove(member);
            RealmInstanceRegistry.replace(current.with(members));
        }
        RealmInstanceRegistry.at(record.dimension()).ifPresent(current -> retire(server, current));
        return true;
    }

    /**
     * Ends an instance by force, discarding its terrain even when it was claimed.
     */
    public static boolean destroy(MinecraftServer server, RealmRecord record) {
        for (UUID member : List.copyOf(record.members())) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) leave(player);
        }
        RealmRecord current = RealmInstanceRegistry.at(record.dimension()).orElse(null);
        if (current == null) return false;
        RealmInstanceRegistry.remove(current.dimension());
        if (!(current.instance().generation() instanceof Existing))
            RuntimeDimensionService.delete(server, current.dimension());
        NeoForge.EVENT_BUS.post(new Destroy(server, current.definition(), current.dimension(), current.index(), current.owner()));
        return true;
    }

    /**
     * Restores a traveller whose instance no longer holds them, which is what a destroyed or expired realm
     * leaves behind for a player who was offline at the time.
     */
    public static boolean returnIfOrphaned(ServerPlayer player) {
        RealmTravelAttachment travel = player.getData(MxtAttachments.REALM_TRAVEL);
        if (!travel.active()) return false;
        if (RealmInstanceRegistry.ofMember(player.getUUID()).isPresent()) return false;
        MinecraftServer server = player.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return false;
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        return true;
    }

    /**
     * Plans an instance of a definition without opening it: identity, seed and clock only. This is the part of
     * an entry that needs no player, so a script can create a realm and decide separately who goes in.
     */
    public static RealmRecord plan(Holder<RealmInstance> definition, int index, long seed, long gameTime) {
        RealmInstance value = definition.value();
        ResourceKey<Level> dimension = RealmGenerationService.dimensionKey(HolderHelper.id(definition), value.generation(), index);
        long expiresAt = value.durationTicks() <= 0L ? -1L : gameTime + value.durationTicks();
        return new RealmRecord(definition, index, dimension, seed, Optional.empty(), gameTime, expiresAt,
                Optional.empty(), false, List.of());
    }

    /**
     * Registers a freshly resolved instance and makes sure its dimension exists and is furnished. This is the
     * half of an entry that does not need a player, so scripts and diagnostics can open a realm without
     * sending anybody into it.
     */
    public static Optional<RealmRecord> open(MinecraftServer server, RealmRecord record) {
        // The table is written before the level is built: the seed mixin and the registry both answer for this
        // dimension while it is being constructed.
        RealmInstanceRegistry.put(record);
        Optional<ServerLevel> opened = RealmGenerationService.open(server, record);
        if (opened.isEmpty()) {
            RealmInstanceRegistry.remove(record.dimension());
            return Optional.empty();
        }
        ServerLevel level = opened.get();
        RealmRecord current = RealmInstanceRegistry.at(record.dimension()).orElse(record);
        if (current.prepared()) return Optional.of(current);
        Landing landing = RealmEntryLocator.plan(level, current);
        RealmStructurePlacer.place(level, current, landing.position());
        current = current.withAnchor(RealmEntryLocator.finish(level, landing)).asPrepared();
        return RealmInstanceRegistry.replace(current);
    }

    /**
     * What happens when the last member leaves: a claimable realm and one built on an existing dimension keep
     * their terrain and are merely unloaded, while a plain instance is destroyed with its region files.
     */
    private static void retire(MinecraftServer server, RealmRecord record) {
        boolean keep = record.persists();
        if (keep) {
            RealmInstanceRegistry.replace(record.idle());
            if (!(record.instance().generation() instanceof Existing))
                RuntimeDimensionService.unload(server, record.dimension());
        } else {
            RealmInstanceRegistry.remove(record.dimension());
            RuntimeDimensionService.delete(server, record.dimension());
        }
        NeoForge.EVENT_BUS.post(new Destroy(server, record.definition(), record.dimension(), record.index(), record.owner()));
    }

    private static Optional<ServerLevel> origin(MinecraftServer server, RealmTravelAttachment travel) {
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
