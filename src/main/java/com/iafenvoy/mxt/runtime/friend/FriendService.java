package com.iafenvoy.mxt.runtime.friend;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.event.FriendEvent.Relation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one place that answers "is this one of mine?", so every friendly-fire decision in the mod goes through here
 * and a consumer never has to know whether a friend came from a list or from another mod. {@link Relation} gets
 * the first word, and anything it leaves at {@code DEFAULT} falls through to the holder's own lists.
 */
public final class FriendService {
    private FriendService() {
    }

    // Not memoised: each call posts a Relation event and a listener is free to consult the world, so a caller
    // asking repeatedly inside one tick should hold on to the answer itself.
    public static boolean isFriend(Entity judge, Entity candidate) {
        return identify(judge, candidate) == TriState.TRUE;
    }

    public static TriState identify(Entity judge, Entity candidate) {
        return identify(judge.getUUID(), judge, candidate);
    }

    // The event gets the first word; DEFAULT from it defers to the built-in system, which answers from the judge's
    // own list when loaded and from FriendCache when not. The judge entity is null when that player is not loaded.
    public static TriState identify(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        Relation event = new Relation(judgeId, judge, candidate);
        NeoForge.EVENT_BUS.post(event);
        return event.answered() ? event.result() : builtin(judgeId, judge, candidate);
    }

    // Safe to call from inside a Relation listener, which is what makes "the lists, plus my own additions"
    // expressible without recursing back into the event.
    public static TriState builtin(Entity judge, Entity candidate) {
        return builtin(judge.getUUID(), judge, candidate);
    }

    // An entity is its own friend, so self is answered first; a loaded judge is then read off their attachment and
    // an unloaded one from FriendCache. Only players can have a list, so another kind of judge answers FALSE.
    public static TriState builtin(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        if (judgeId.equals(candidate.getUUID())) return TriState.TRUE;
        if (judge == null) return FriendCache.lookup(judgeId, candidate.getUUID());
        if (!(judge instanceof Player)) return TriState.FALSE;
        FriendAttachment friends = judge.getExistingData(MxtAttachments.FRIEND).orElse(null);
        return TriState.from(friends != null && friends.isFriend(candidate.getUUID()));
    }
}
