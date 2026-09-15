package com.iafenvoy.mxt.runtime.friend;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every player's friend lists, mirrored in memory so that an owner who is offline can still be answered for.
 *
 * <p>The lists themselves live on the player entity, which is exactly what is missing once that player logs
 * out. A mirror kept here lets the built-in fallback keep answering during the gap, which is the case a
 * formation standing over a logged-out owner's base runs into: without it, nobody could be identified and
 * the formation would stand down.</p>
 *
 * <p><b>Refreshed at both ends of a session</b> — when the player logs in, after the session list has been
 * cleared, and again when they log out. That pair is what makes the mirror correct without every writer
 * having to remember it: while the player is online the live attachment is what gets read, so the mirror's
 * contents do not matter, and by the time it starts mattering the logout refresh has brought it up to date.
 * A writer added later therefore needs no knowledge of this class at all.</p>
 *
 * <p>In memory only, deliberately. A restart empties it, and the next login refills it — which is the safe
 * direction: "nobody can answer" makes a hostile formation stand down, while a mirror restored from a
 * stale on-disk copy would answer wrongly. Nothing here is evicted, because the map holds one small set of
 * ids per player who has logged in, and a player who never returns costs one entry.</p>
 */
public final class FriendCache {
    private static final Map<UUID, Set<UUID>> FRIENDS = new ConcurrentHashMap<>();

    private FriendCache() {
    }

    /**
     * Re-reads one player's lists into the mirror.
     *
     * <p>A player with no friend attachment is recorded as having an empty list rather than being skipped:
     * "this player has no friends" and "this player is unknown" are different answers, and only the second
     * one leaves a question unanswered.</p>
     */
    public static void refresh(ServerPlayer player) {
        Set<UUID> ids = new HashSet<>();
        player.getExistingData(MxtAttachments.FRIEND).ifPresent(friends -> {
            friends.permanent().forEach(friend -> ids.add(friend.id()));
            friends.temporary().forEach(friend -> ids.add(friend.id()));
        });
        FRIENDS.put(player.getUUID(), Set.copyOf(ids));
    }

    /**
     * The mirrored answer for a named owner, or {@code DEFAULT} when that player has never been seen.
     */
    public static TriState lookup(UUID owner, UUID candidate) {
        Set<UUID> friends = FRIENDS.get(owner);
        return friends == null ? TriState.DEFAULT : TriState.from(friends.contains(candidate));
    }
}
