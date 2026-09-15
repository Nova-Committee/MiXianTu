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
 * The players the holder accepts as its own.
 *
 * <p>Two lists. {@code permanent} is meant to be saved. {@code temporary} is saved as well, but it is not
 * meant to last: {@code FriendSessionBridge} empties it when its owner logs in, so a relog or a server
 * restart clears it. Saving it first is what keeps a session friend through a death — NeoForge's
 * copy-on-death is a serialize/deserialize pair, so a field left out of the codec would be lost when the
 * player dies and comes back, which is not what "added for now" should mean. The two mechanisms together
 * are the whole definition: the codec says the list is real state, the login hook says when a session
 * ends.</p>
 *
 * <p>Nothing drops a session entry at logout, so a player who never comes back leaves one in the save.
 * That is the trade for not losing the list to a crash mid-session, and it is only visible to a file
 * reader: the list is meaningless without the owner entity, and it is gone the moment they log in.</p>
 *
 * <p>Not synced to the client. Nothing on the client reads it, and a synced copy would be a snapshot that
 * is stale the moment the next login clears it. With no sync there is nothing for
 * {@code ShouldSyncAttachment}'s dirty flag to do either, so this class does not extend it; see
 * {@code MxtAttachments#entityServerOnly}.</p>
 *
 * <p>An entry is a {@link NameAndId} rather than a bare {@code UUID} so that a list stays readable once
 * the player it names has gone offline. The name is whatever the entry was created with and may go stale
 * after a rename, so it is only ever displayed — every match is made on the id.</p>
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
        // claim and the one a later removal has to speak about, so it wins and the session copy is
        // dropped: keeping both would make "remove" report a permanent friend while leaving an entry
        // that still answers yes.
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
     * Adds a friend for this session only.
     *
     * <p>A player who is already a permanent friend is reported as such rather than also being put on the
     * temporary list: the same player on both lists would mean two answers to "will this still be here
     * tomorrow", and the permanent one is the only answer that cannot be wrong.</p>
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
     * command, and silently succeeding here would make a temporary removal look like it took effect.
     */
    public RemoveResult remove(UUID id) {
        if (this.permanent.containsKey(id)) return RemoveResult.PERMANENT;
        return this.temporary.remove(id) == null ? RemoveResult.NOT_A_FRIEND : RemoveResult.REMOVED;
    }

    /**
     * Removes a saved friend. A temporary entry is not one, so it is reported as missing rather than
     * removed — this command only ever speaks about the saved list.
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
