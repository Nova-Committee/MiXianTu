package com.iafenvoy.mxt.runtime.friend;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.TriState;

import java.util.Set;
import java.util.UUID;

/**
 * Every player's friend lists, mirrored in memory so an owner who is offline can still be answered for; the lists
 * themselves live on the player entity. Filled at login and again at logout, so no writer of friend data has to
 * remember it; while the owner is online the live attachment is read instead.
 *
 * <p>The mirror belongs to one server session: it is dropped when that server stops, so a process that opens a second
 * world cannot answer for an owner from the first one. Unknown is a real answer, and it is the right one until an owner
 * has logged in to the world being played.
 */
public final class FriendCache {
    private static final SetMultimap<UUID, UUID> FRIENDS = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    private FriendCache() {
    }

    // A player with no friend attachment is recorded as having an empty list rather than skipped: "this player has
    // no friends" and "this player is unknown" are different answers.
    public static void refresh(ServerPlayer player) {
        player.getExistingData(MxtAttachments.FRIEND).ifPresent(friends -> {
            FRIENDS.putAll(player.getUUID(), friends.permanent().stream().map(NameAndId::id).toList());
            FRIENDS.putAll(player.getUUID(), friends.temporary().stream().map(NameAndId::id).toList());
        });
    }

    // The mirrored answer for a named owner, or DEFAULT when that player has never been seen.
    public static TriState lookup(UUID owner, UUID candidate) {
        Set<UUID> friends = FRIENDS.get(owner);
        return TriState.from(friends.contains(candidate));
    }

    public static void clear() {
        FRIENDS.clear();
    }
}
