package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.RealmInstanceData;
import com.iafenvoy.mxt.attachment.RealmTravelData;
import com.iafenvoy.mxt.data.world.RealmInstanceDefinition;
import com.iafenvoy.mxt.event.RealmInstanceEvent;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPost;
import com.iafenvoy.mxt.event.RealmInstanceEvent.EnterPre;
import com.iafenvoy.mxt.event.RealmInstanceEvent.Exit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative temporary-realm membership and cross-dimension travel.
 */
public final class RealmInstanceService {
    private RealmInstanceService() {
    }

    /**
     * Enters a realm only after its target dimension is available, then retains an exact return location.
     */
    public static Result enter(ServerPlayer player, RealmInstanceData data, Identifier id, RealmInstanceDefinition definition) {
        if (player.getData(MxtAttachments.REALM_TRAVEL).active())
            return Result.rejected(Failure.ALREADY_TRAVELLING);
        ServerLevel destination = destination(player.level().getServer(), definition).orElse(null);
        if (destination == null) return Result.rejected(Failure.MISSING_DIMENSION);
        Result membership = enter(player.level(), data, id, definition, player.getUUID(), player.level().getGameTime());
        if (!membership.changed()) return membership;

        RealmTravelData travel = player.getData(MxtAttachments.REALM_TRAVEL);
        travel.begin(id, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        double entryX = 0.5D;
        double entryZ = 0.5D;
        double entryY = destination.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        player.teleportTo(destination, entryX, entryY, entryZ, Set.of(), player.getYRot(), player.getXRot(), false);
        return membership;
    }

    public static Result enter(ServerLevel level, RealmInstanceData data, Identifier id, RealmInstanceDefinition definition, UUID member, long gameTime) {
        if (data.active() && data.definition().filter(id::equals).isEmpty())
            return Result.rejected(Failure.OTHER_INSTANCE);
        if (NeoForge.EVENT_BUS.post(new EnterPre(level, id, member)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        if (!data.active()) data.start(id, gameTime, definition.durationTicks());
        if (!data.add(member, definition.maxMembers())) return Result.rejected(Failure.FULL);
        NeoForge.EVENT_BUS.post(new EnterPost(level, id, member));
        DomainBehaviorService.execute(MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR, definition.enterBehavior(), BehaviorContext.of(
                Kind.REALM_ENTER, id, level.getServer().getPlayerList().getPlayer(member), FormulaContext.EMPTY, true));
        return Result.entered();
    }

    /**
     * Returns a player to the exact origin captured when entering.
     */
    public static Result exit(ServerPlayer player, RealmInstanceData data) {
        RealmTravelData travel = player.getData(MxtAttachments.REALM_TRAVEL);
        Identifier id = travel.realm().orElse(null);
        if (id == null || !travel.active()) return Result.rejected(Failure.NOT_TRAVELLING);
        ServerLevel origin = origin(sourceServer(player), travel).orElse(null);
        if (origin == null) return Result.rejected(Failure.MISSING_ORIGIN);

        ServerLevel source = player.level();
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        if (data.definition().filter(id::equals).isPresent()) {
            data.remove(player.getUUID());
            NeoForge.EVENT_BUS.post(new Exit(source, id, player.getUUID()));
            MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_INSTANCE, id).ifPresent(definition -> DomainBehaviorService.execute(
                    MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR, definition.exitBehavior(), BehaviorContext.of(
                            Kind.REALM_EXIT, id, player, FormulaContext.EMPTY, true)));
            if (data.members().isEmpty()) data.clear();
        }
        return Result.exited();
    }

    public static Result exit(ServerLevel level, RealmInstanceData data, UUID member) {
        Identifier id = data.definition().orElse(null);
        if (id == null || !data.members().contains(member)) return Result.rejected(Failure.NOT_MEMBER);
        data.remove(member);
        NeoForge.EVENT_BUS.post(new Exit(level, id, member));
        MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_INSTANCE, id).ifPresent(definition -> DomainBehaviorService.execute(
                MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR, definition.exitBehavior(), BehaviorContext.of(
                        Kind.REALM_EXIT, id, level.getServer().getPlayerList().getPlayer(member), FormulaContext.EMPTY, true)));
        if (data.members().isEmpty()) data.clear();
        return Result.exited();
    }

    public static boolean expire(ServerLevel level, RealmInstanceData data, long gameTime) {
        if (!data.active() || !data.expired(gameTime)) return false;
        for (UUID member : new ArrayList<>(data.members())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) exit(player, data);
            else data.remove(member);
        }
        data.clear();
        return true;
    }

    /**
     * Restores an expired realm traveller after login, without requiring the old realm instance to remain loaded.
     */
    public static boolean returnIfOrphaned(ServerPlayer player) {
        RealmTravelData travel = player.getData(MxtAttachments.REALM_TRAVEL);
        Identifier realm = travel.realm().orElse(null);
        if (realm == null || !travel.active()) return false;
        for (ServerLevel level : sourceServer(player).getAllLevels()) {
            RealmInstanceData data = level.getData(MxtAttachments.REALM_INSTANCE);
            if (data.active() && data.definition().filter(realm::equals).isPresent() && data.members().contains(player.getUUID()))
                return false;
        }
        ServerLevel origin = origin(sourceServer(player), travel).orElse(null);
        if (origin == null) return false;
        player.teleportTo(origin, travel.originX(), travel.originY(), travel.originZ(), Set.of(), travel.originYaw(), travel.originPitch(), false);
        travel.clear();
        return true;
    }

    private static Optional<ServerLevel> destination(MinecraftServer server, RealmInstanceDefinition definition) {
        return definition.dimension().map(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    private static Optional<ServerLevel> origin(MinecraftServer server, RealmTravelData travel) {
        return travel.originDimension().map(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    private static MinecraftServer sourceServer(ServerPlayer player) {
        return player.level().getServer();
    }

    public enum Failure {DISABLED, OTHER_INSTANCE, FULL, CANCELLED, NOT_MEMBER, MISSING_DIMENSION, ALREADY_TRAVELLING, NOT_TRAVELLING, MISSING_ORIGIN}

    public record Result(boolean changed, Failure failure) {
        static Result entered() {
            return new Result(true, null);
        }

        static Result exited() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
