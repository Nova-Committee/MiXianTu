package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.players.NameAndId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The players the holder accepts as its own. {@code temporary} is saved but emptied at login, so saving
 * it is what keeps a session friend through death. An entry is a {@link NameAndId} so a list stays
 * readable offline; the name is display-only and every match is made on the id. Not synced to the
 * client, whose copy would be stale the moment the next login clears it.
 */
public final class FriendAttachment {
    public static final MapCodec<FriendAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(NameAndId.CODEC).optionalFieldOf("permanent", List.of()).forGetter(FriendAttachment::permanent),
            CollectionCodecs.list(NameAndId.CODEC).optionalFieldOf("temporary", List.of()).forGetter(FriendAttachment::temporary)
    ).apply(i, FriendAttachment::new));

    private final Map<UUID, NameAndId> permanent = new LinkedHashMap<>();
    private final Map<UUID, NameAndId> temporary = new LinkedHashMap<>();

    public FriendAttachment() {
    }

    private FriendAttachment(List<NameAndId> permanent, List<NameAndId> temporary) {
        // A hand-edited file can name the same player on both lists. The saved entry is the stronger
        // claim, so it wins and the session copy is dropped.
        for (NameAndId friend : permanent) this.permanent.put(friend.id(), friend);
        for (NameAndId friend : temporary)
            if (!this.permanent.containsKey(friend.id())) this.temporary.put(friend.id(), friend);
    }

    /**
     * The saved friends, in the order they were added.
     */
    public List<NameAndId> permanent() {
        return List.copyOf(this.permanent.values());
    }

    /**
     * The friends added for the current session, in the order they were added.
     */
    public List<NameAndId> temporary() {
        return List.copyOf(this.temporary.values());
    }

    /**
     * Whether the holder recognises this player, by either list.
     */
    public boolean isFriend(UUID id) {
        return this.permanent.containsKey(id) || this.temporary.containsKey(id);
    }

    /**
     * Adds a friend for this session only; a permanent friend is reported as such rather than put on both
     * lists, where "will this still be here tomorrow" would have two answers.
     */
    public AddResult add(NameAndId friend) {
        if (this.permanent.containsKey(friend.id())) return AddResult.ALREADY_PERMANENT;
        return this.temporary.put(friend.id(), friend) == null ? AddResult.ADDED : AddResult.ALREADY_TEMPORARY;
    }

    /**
     * Adds a saved friend, promoting a temporary entry instead of ending up on both lists.
     */
    public AddResult addPermanent(NameAndId friend) {
        if (this.permanent.put(friend.id(), friend) != null) return AddResult.ALREADY_PERMANENT;
        this.temporary.remove(friend.id());
        return AddResult.ADDED;
    }

    /**
     * Removes a session friend. A permanent one is left alone and reported: forgetting it is a different
     * command, and succeeding here would look like the removal took effect.
     */
    public RemoveResult remove(UUID id) {
        if (this.permanent.containsKey(id)) return RemoveResult.PERMANENT;
        return this.temporary.remove(id) == null ? RemoveResult.NOT_A_FRIEND : RemoveResult.REMOVED;
    }

    /**
     * Removes a saved friend. A temporary entry is not one, so it is reported as missing.
     */
    public RemoveResult removePermanent(UUID id) {
        return this.permanent.remove(id) == null ? RemoveResult.NOT_A_FRIEND : RemoveResult.REMOVED;
    }

    /**
     * Ends the current session's list, which is what a login does.
     *
     * @return whether anything was dropped
     */
    public boolean clearTemporary() {
        if (this.temporary.isEmpty()) return false;
        this.temporary.clear();
        return true;
    }

    /**
     * What {@link #add} or {@link #addPermanent} did.
     */
    public enum AddResult {
        ADDED,
        ALREADY_TEMPORARY,
        ALREADY_PERMANENT
    }

    /**
     * What {@link #remove} or {@link #removePermanent} did.
     */
    public enum RemoveResult {
        REMOVED,
        NOT_A_FRIEND,
        PERMANENT
    }
}
