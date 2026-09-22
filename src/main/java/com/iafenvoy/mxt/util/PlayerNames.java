package com.iafenvoy.mxt.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Optional;
import java.util.UUID;

/**
 * The name behind an owner's UUID, answered only from what this side can already see - a wrong name is worse than
 * an id nobody can read. Nothing here reaches the network, because a tooltip is drawn on the render thread; that is
 * why the name is written down when a stack is claimed instead.
 */
public final class PlayerNames {
    private PlayerNames() {
    }

    // A nameless entity is stored without a name rather than with a blank one.
    public static Optional<String> displayName(Entity entity) {
        Component name = entity.getName();
        String value = name.getString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    // The player list and the persisted name cache answer on a server; a client on a remote server has neither
    // and asks the server once (see {@link ClientPlayerNames}). Empty means nobody could answer.
    public static Optional<String> resolve(String ownerUuid) {
        UUID id = parse(ownerUuid);
        if (id == null) return Optional.empty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            Optional<String> known = knownToServer(server, id);
            if (known.isPresent()) return known;
        }
        return FMLEnvironment.getDist() == Dist.CLIENT ? ClientPlayerNames.lookup(id) : Optional.empty();
    }

    // The session service is deliberately not asked: it would answer for a player this server has never seen,
    // but that lookup is a web request and both callers run where waiting on one is not acceptable.
    public static Optional<String> knownToServer(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return Optional.of(online.getGameProfile().name());
        return server.services().nameToIdCache().get(id).map(NameAndId::name);
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
