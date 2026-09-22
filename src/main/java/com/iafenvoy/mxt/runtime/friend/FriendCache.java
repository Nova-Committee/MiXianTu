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
 * Every player's friend lists, mirrored in memory so an owner who is offline can still be answered for; the lists
 * themselves live on the player entity. Filled at login and again at logout, so no writer of friend data has to
 * remember it; while the owner is online the live attachment is read instead.
 */
public final class FriendCache {
    private static final Map<UUID, Set<UUID>> FRIENDS = new ConcurrentHashMap<>();

    private FriendCache() {
    }

    // A player with no friend attachment is recorded as having an empty list rather than skipped: "this player has
    // no friends" and "this player is unknown" are different answers.
    public static void refresh(ServerPlayer player) {
        Set<UUID> ids = new HashSet<>();
        player.getExistingData(MxtAttachments.FRIEND).ifPresent(friends -> {
            friends.permanent().forEach(friend -> ids.add(friend.id()));
            friends.temporary().forEach(friend -> ids.add(friend.id()));
        });
        FRIENDS.put(player.getUUID(), Set.copyOf(ids));
    }

    // The mirrored answer for a named owner, or DEFAULT when that player has never been seen.
    public static TriState lookup(UUID owner, UUID candidate) {
        Set<UUID> friends = FRIENDS.get(owner);
        return friends == null ? TriState.DEFAULT : TriState.from(friends.contains(candidate));
    }
}
