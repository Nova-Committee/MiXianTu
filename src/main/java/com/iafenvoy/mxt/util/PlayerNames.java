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
 * The name behind an owner's UUID, for the places that can only show what a reader can read.
 *
 * <p>Ownership is stored as both halves - see {@link com.iafenvoy.mxt.data.artifact.ArtifactStateComponent} - so
 * this is only ever the answer to "this stack was claimed before names were recorded, and the owner is somebody
 * this side can still see". The running server knows its player list, and a client knows the players its
 * connection lists; anything else is deliberately left unanswered rather than guessed at, because a wrong name
 * is worse than an id nobody can read.</p>
 *
 * <p>Nothing here reaches the network. A profile service lookup would answer for players who are offline, but it
 * is a blocking request, and a tooltip is drawn on the render thread - which is exactly why the name is written
 * down when the stack is claimed instead.</p>
 */
public final class PlayerNames {
    private PlayerNames() {
    }

    /**
     * What to call an entity that is claiming something: its own display name, or nothing when it has none -
     * a nameless entity is stored without a name rather than with a blank one.
     */
    public static Optional<String> displayName(Entity entity) {
        Component name = entity.getName();
        String value = name.getString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * A name for an owner UUID, from whichever side can answer.
     *
     * <p>On a server - including the integrated one - the player list answers for anyone online and the persisted
     * name cache answers for anyone who has ever joined, both of them as a map lookup. A client on a remote
     * server has neither, so it asks the server once and shows what it has until the answer lands; see
     * {@link ClientPlayerNames}. Empty means nobody could answer, which is the caller's cue to show the id.</p>
     */
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

    /**
     * The name a running server can answer with, without asking anybody: the player if they are online, else the
     * name its profile cache kept from a previous login.
     *
     * <p>The session service is deliberately not asked even though it would answer for a player this server has
     * never seen: that lookup is a web request, and both callers (a tooltip and a packet handler) run where
     * waiting on one is not acceptable.</p>
     */
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
