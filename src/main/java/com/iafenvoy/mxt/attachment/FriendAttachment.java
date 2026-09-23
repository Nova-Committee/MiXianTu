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
 * The players the holder accepts as its own. {@code temporary} is saved but emptied at login, so saving it is what
 * keeps a session friend through death. An entry is a {@link NameAndId} so a list stays readable offline, and every
 * match is made on the id. Not synced: the client's copy would be stale the moment the next login clears it.
 */
public final class FriendAttachment {
    public static final MapCodec<FriendAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(NameAndId.CODEC).lenientOptionalFieldOf("permanent", List.of()).forGetter(FriendAttachment::permanent),
            CollectionCodecs.list(NameAndId.CODEC).lenientOptionalFieldOf("temporary", List.of()).forGetter(FriendAttachment::temporary)
    ).apply(i, FriendAttachment::new));

    private final Map<UUID, NameAndId> permanent = new LinkedHashMap<>();
    private final Map<UUID, NameAndId> temporary = new LinkedHashMap<>();

    public FriendAttachment() {
    }

    private FriendAttachment(List<NameAndId> permanent, List<NameAndId> temporary) {
        // A hand-edited file can name the same player on both lists; the saved entry is the stronger claim.
        for (NameAndId friend : permanent) this.permanent.put(friend.id(), friend);
        for (NameAndId friend : temporary)
            if (!this.permanent.containsKey(friend.id())) this.temporary.put(friend.id(), friend);
    }

    public List<NameAndId> permanent() {
        return List.copyOf(this.permanent.values());
    }

    public List<NameAndId> temporary() {
        return List.copyOf(this.temporary.values());
    }

    public boolean isFriend(UUID id) {
        return this.permanent.containsKey(id) || this.temporary.containsKey(id);
    }

    // A permanent friend is reported as such rather than put on both lists, where "will this still be here
    // tomorrow" would have two answers.
    public AddResult add(NameAndId friend) {
        if (this.permanent.containsKey(friend.id())) return AddResult.ALREADY_PERMANENT;
        return this.temporary.put(friend.id(), friend) == null ? AddResult.ADDED : AddResult.ALREADY_TEMPORARY;
    }

    public AddResult addPermanent(NameAndId friend) {
        if (this.permanent.put(friend.id(), friend) != null) return AddResult.ALREADY_PERMANENT;
        this.temporary.remove(friend.id());
        return AddResult.ADDED;
    }

    // A permanent friend is left alone and reported: forgetting it is a different command.
    public RemoveResult remove(UUID id) {
        if (this.permanent.containsKey(id)) return RemoveResult.PERMANENT;
        return this.temporary.remove(id) == null ? RemoveResult.NOT_A_FRIEND : RemoveResult.REMOVED;
    }

    public RemoveResult removePermanent(UUID id) {
        return this.permanent.remove(id) == null ? RemoveResult.NOT_A_FRIEND : RemoveResult.REMOVED;
    }

    // What a login does: the session list ends here.
    public boolean clearTemporary() {
        if (this.temporary.isEmpty()) return false;
        this.temporary.clear();
        return true;
    }

    public enum AddResult {
        ADDED,
        ALREADY_TEMPORARY,
        ALREADY_PERMANENT
    }

    public enum RemoveResult {
        REMOVED,
        NOT_A_FRIEND,
        PERMANENT
    }
}
