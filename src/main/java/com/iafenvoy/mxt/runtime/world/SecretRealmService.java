package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.SecretRealmTravelAttachment;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.builtin.ExistingGeneration;
import com.iafenvoy.mxt.event.SecretRealmEvent.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.failure.SecretRealmFailure;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import com.iafenvoy.mxt.runtime.failure.Failure;
import com.iafenvoy.mxt.runtime.failure.Failures;

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

    public static Result enter(LivingEntity traveller, MinecraftServer server, Holder<SecretRealm> definition) {
        // A token can outlive the definition it names, so the current registry is the last word on whether this realm
        // still exists. Kept here rather than at the token, because every caller has to be held to it.
        if (definition.unwrapKey().flatMap(server.overworld().registryAccess()::get).isEmpty())
            return Result.rejected(Failures.DISABLED);
        SecretRealmTravelAttachment travel = traveller.getData(MxtAttachments.SECRET_REALM_TRAVEL);
        if (travel.active()) return Result.rejected(SecretRealmFailure.ALREADY_TRAVELLING);

        UUID member = traveller.getUUID();
        // One clock for the whole lifecycle: every level shares the primary level data's game time, and the expiry
        // sweep reads it from the overworld, so a record's expiry has to be written from the same clock.
        long gameTime = server.overworld().getGameTime();
        SecretRealm value = definition.value();
        FormulaContext context = FormulaContext.of(traveller);
        if (!value.enterCondition().test(traveller, context))
            return Result.rejected(SecretRealmFailure.CONDITION_NOT_MET, value.enterDeniedMessage());

        // A clock that ran out while nobody was watching must not keep an instance alive.
        for (SecretRealmRecord candidate : SecretRealmRegistry.of(definition))
            if (candidate.expired(gameTime)) expire(server, candidate, gameTime);

        SecretRealmRecord record;
        boolean created = false;
        Optional<SecretRealmRecord> joinable = SecretRealmRegistry.joinable(definition, member);
        if (joinable.isPresent()) {
            record = joinable.get();
            if (!record.holds(member) && record.full()) return Result.rejected(SecretRealmFailure.FULL);
            if (record.empty()) record = record.restarted(gameTime);
        } else {
            if (SecretRealmRegistry.of(definition).size() >= value.maxInstances())
                return Result.rejected(SecretRealmFailure.NO_FREE_INSTANCE);
            if (!SecretRealmStructurePlacer.resolvable(server.overworld().getStructureManager(), value))
                return Result.rejected(SecretRealmFailure.MISSING_STRUCTURE);
            int index = SecretRealmRegistry.nextIndex(definition);
            long seed = value.seed() != 0L ? value.seed() : RandomSource.create().nextLong();
            record = plan(definition, index, seed, gameTime);
            if (SecretRealmRegistry.at(record.dimension()).isPresent())
                return Result.rejected(SecretRealmFailure.NO_FREE_INSTANCE);
            created = true;
        }
        if (NeoForge.EVENT_BUS.post(new EnterPre(server, definition, record.dimension(), record.index(), record.owner(), member)).isCanceled())
            return Result.rejected(Failures.CANCELLED);
        if (value.owned() && record.owner().isEmpty()) record = record.withOwner(member);

        Optional<SecretRealmRecord> prepared = open(server, record);
        if (prepared.isEmpty()) return Result.rejected(SecretRealmFailure.GENERATION_FAILED);
        record = prepared.get();
        ServerLevel destination = server.getLevel(record.dimension());
        if (destination == null) {
            SecretRealmRegistry.remove(record.dimension());
            return Result.rejected(SecretRealmFailure.MISSING_DIMENSION);
        }
        if (created)
            NeoForge.EVENT_BUS.post(new Create(server, definition, record.dimension(), record.index(), record.owner()));

        // The teleport is the one step the platform can refuse, so it happens before anything is recorded: a failed
        // entrance leaves no member entry, no travel state and no enter events behind.
        List<UUID> members = new ArrayList<>(record.members());
        boolean joining = !members.contains(member);
        int slot = joining ? members.size() : members.indexOf(member);
        Arrival arrival = SecretRealmEntryLocator.arrival(destination, record, slot, traveller.getYRot(), traveller.getXRot());
        Identifier originDimension = traveller.level().dimension().identifier();
        double originX = traveller.getX();
        double originY = traveller.getY();
        double originZ = traveller.getZ();
        float originYaw = traveller.getYRot();
        float originPitch = traveller.getXRot();
        LivingEntity arrived = move(traveller, destination, arrival.position().x, arrival.position().y,
                arrival.position().z, arrival.yaw(), arrival.pitch());
        if (arrived == null)
            return Result.rejected(SecretRealmFailure.TELEPORT_FAILED);
        if (joining) {
            members.add(member);
            record = record.with(members);
            SecretRealmRegistry.replace(record);
        }
        // Written once the traveller is really there: the origin is what an exit and an orphan sweep come back to.
        // The state goes on the body that landed, which is not the one that left when the platform had to rebuild it.
        arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL)
                .begin(definition, originDimension, originX, originY, originZ, originYaw, originPitch);
        NeoForge.EVENT_BUS.post(new EnterPost(server, definition, record.dimension(), record.index(), record.owner(), member));
        value.enterAction().execute(arrived, FormulaContext.of(arrived));
        return Result.entered(arrived);
    }

    public static Result exit(LivingEntity traveller) {
        SecretRealmTravelAttachment travel = travel(traveller);
        Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Result.rejected(SecretRealmFailure.NOT_TRAVELLING);
        SecretRealm value = definition.value();
        if (!value.exitCondition().test(traveller, FormulaContext.of(traveller)))
            return Result.rejected(SecretRealmFailure.EXIT_DENIED, value.exitDeniedMessage());
        Moved moved = leave(traveller);
        return moved.failure() == null ? Result.exited(moved.traveller()) : Result.rejected(moved.failure());
    }

    // Also the exit used by an expiry and by an administrator, which is why it never consults the exit
    // condition: a definition must not be able to lock a traveller inside a secret realm forever. A null failure
    // means the traveller is out (or had nothing to undo); anything else is a refusal, and nothing is cleared for it,
    // so the record still says where they are and a later attempt can try again.
    private static Moved leave(LivingEntity traveller) {
        SecretRealmTravelAttachment travel = travel(traveller);
        Holder<SecretRealm> definition = travel == null ? null : travel.realm().orElse(null);
        if (definition == null || !travel.active()) return Moved.arrived(traveller);
        MinecraftServer server = traveller.level().getServer();
        ServerLevel origin = origin(server, travel).orElse(null);
        if (origin == null) return Moved.refused(SecretRealmFailure.MISSING_ORIGIN);
        Optional<SecretRealmRecord> held = SecretRealmRegistry.ofMember(traveller.getUUID());
        LivingEntity arrived = move(traveller, origin, travel.originX(), travel.originY(), travel.originZ(),
                travel.originYaw(), travel.originPitch());
        if (arrived == null) return Moved.refused(SecretRealmFailure.TELEPORT_FAILED);
        arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL).clear();
        if (held.isPresent()) {
            SecretRealmRecord record = held.get();
            List<UUID> members = new ArrayList<>(record.members());
            members.remove(traveller.getUUID());
            SecretRealmRecord updated = record.with(members);
            if (updated.empty()) retire(server, updated);
            else SecretRealmRegistry.replace(updated);
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
        TeleportTransition transition = new TeleportTransition(destination, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                Set.of(), TeleportTransition.DO_NOTHING);
        Entity moved = traveller.teleport(transition);
        return moved instanceof LivingEntity living ? living : null;
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
        boolean expelled = true;
        for (UUID id : List.copyOf(record.members())) {
            LivingEntity member = findMember(server, id);
            if (member != null) {
                // A member the platform would not move keeps the instance retired for later: retiring now would
                // drop the record of somebody still standing inside it.
                if (leave(member).failure() != null) expelled = false;
                continue;
            }
            SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
            if (current == null) return true;
            List<UUID> members = new ArrayList<>(current.members());
            members.remove(id);
            SecretRealmRegistry.replace(current.with(members));
        }
        if (!expelled) return false;
        SecretRealmRegistry.at(record.dimension()).ifPresent(current -> retire(server, current));
        return true;
    }

    // By force: the terrain is discarded even when the instance was claimed. A member that cannot be moved keeps the
    // record, so nothing is retired while somebody is still inside; the answer says whether the dimension really
    // went away, since the platform refuses to delete a level that still holds a player.
    public static boolean destroy(MinecraftServer server, SecretRealmRecord record) {
        for (UUID id : List.copyOf(record.members())) {
            LivingEntity member = findMember(server, id);
            if (member != null && leave(member).failure() != null) return false;
        }
        SecretRealmRecord current = SecretRealmRegistry.at(record.dimension()).orElse(null);
        if (current == null) return false;
        boolean deleted = current.instance().generation() instanceof ExistingGeneration
                || RuntimeDimensionService.delete(server, current.dimension());
        SecretRealmRegistry.remove(current.dimension());
        NeoForge.EVENT_BUS.post(new Destroy(server, current.definition(), current.dimension(), current.index(), current.owner()));
        return deleted;
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
        LivingEntity arrived = move(traveller, origin, travel.originX(), travel.originY(), travel.originZ(),
                travel.originYaw(), travel.originPitch());
        if (arrived == null) return false;
        arrived.getData(MxtAttachments.SECRET_REALM_TRAVEL).clear();
        return true;
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
        // The table is written before the level is built: the prepared copy replaces this entry at the end of the
        // method, and replace() only accepts a dimension the table already holds.
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
            if (!(record.instance().generation() instanceof ExistingGeneration))
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

    /**
     * A secret realm answer. {@code traveller} is the body that now stands where it was sent: the platform rebuilds a
     * non-player that crosses a dimension, so a caller that means to keep holding the traveller has to take this one.
     * Empty when nothing moved, which is every refusal.
     */
    public record Result(boolean changed, Failure failure, Optional<Component> message,
                         Optional<LivingEntity> traveller) {
        static Result entered(LivingEntity traveller) {
            return new Result(true, null, Optional.empty(), Optional.of(traveller));
        }

        static Result exited(LivingEntity traveller) {
            return new Result(true, null, Optional.empty(), Optional.of(traveller));
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure, Optional.empty(), Optional.empty());
        }

        static Result rejected(Failure failure, Optional<Component> message) {
            return new Result(false, failure, message, Optional.empty());
        }
    }

    // The answer to a move out: the refusal, and the body that came out - a non-player crossing a dimension is
    // rebuilt, so it is not always the entity handed in.
    private record Moved(@Nullable Failure failure, @Nullable LivingEntity traveller) {
        static Moved refused(Failure failure) {
            return new Moved(failure, null);
        }

        static Moved arrived(LivingEntity traveller) {
            return new Moved(null, traveller);
        }
    }
}
