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
 * The one place that answers "is this one of mine?", so every friendly-fire decision in the mod goes through
 * here and a consumer never has to know whether a friend came from a list or from another mod.
 * {@link Relation} gets the first word, and anything it leaves at {@code DEFAULT} falls through
 * to the holder's own lists. The judge is an id plus an optional entity, because the id is the only half
 * that still exists once that player logs out.
 */
public final class FriendService {
    private FriendService() {
    }

    /**
     * Not memoised: each call posts a {@link Relation}, and a listener is free to consult the
     * world, so a caller asking repeatedly inside one tick should hold on to the answer itself.
     */
    public static boolean isFriend(Entity judge, Entity candidate) {
        return identify(judge, candidate) == TriState.TRUE;
    }

    /**
     * The full verdict for a judge that is loaded.
     */
    public static TriState identify(Entity judge, Entity candidate) {
        return identify(judge.getUUID(), judge, candidate);
    }

    /**
     * The event gets the first word; {@code DEFAULT} from it defers to the built-in friend system, which
     * answers from the judge's own list when that judge is loaded and from {@link FriendCache} when they are
     * not. {@code DEFAULT} therefore escapes only for a judge the mirror has never seen, and what a caller
     * makes of "nobody knows" is its own decision — see {@code FormationRelations#affects}.
     *
     * @param judge the judge entity, or null when that player is not loaded
     */
    public static TriState identify(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        Relation event = new Relation(judgeId, judge, candidate);
        NeoForge.EVENT_BUS.post(event);
        return event.answered() ? event.result() : builtin(judgeId, judge, candidate);
    }

    /**
     * Safe to call from inside a {@link Relation} listener, which is what makes "the lists, plus
     * my own additions" expressible without recursing back into the event.
     */
    public static TriState builtin(Entity judge, Entity candidate) {
        return builtin(judge.getUUID(), judge, candidate);
    }

    /**
     * An entity is its own friend, so self is answered first; a loaded judge is then read straight off their
     * attachment and an unloaded one from {@link FriendCache}. Only players can have a friend list, so a
     * loaded judge of any other kind is {@code FALSE} rather than no answer.
     */
    public static TriState builtin(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        if (judgeId.equals(candidate.getUUID())) return TriState.TRUE;
        if (judge == null) return FriendCache.lookup(judgeId, candidate.getUUID());
        if (!(judge instanceof Player)) return TriState.FALSE;
        FriendAttachment friends = judge.getExistingData(MxtAttachments.FRIEND).orElse(null);
        return TriState.from(friends != null && friends.isFriend(candidate.getUUID()));
    }
}
