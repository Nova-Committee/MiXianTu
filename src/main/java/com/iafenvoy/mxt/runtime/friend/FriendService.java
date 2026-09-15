package com.iafenvoy.mxt.runtime.friend;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.event.FriendEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one place that answers "is this one of mine?".
 *
 * <p>Every friendly-fire decision in the mod goes through here, so the answer has exactly one home and a
 * consumer never has to know whether a friend came from a list, from another mod, or from that mod's own
 * idea of allegiance. The pipeline is small on purpose: {@link FriendEvent.Relation} gets the first word,
 * and anything it left at {@code DEFAULT} falls through to the holder's own lists.</p>
 *
 * <p>The judge is an id plus an optional entity rather than just an entity, because the two halves reach
 * differently. The id is enough for a source that keeps its own per-player data, and it is the only half
 * that still exists once that player logs out; the entity is what a friend list needs. Asking by id is
 * therefore the general form, and the entity-taking overloads are conveniences for callers that have one.</p>
 */
public final class FriendService {
    private FriendService() {
    }

    /**
     * Whether {@code judge} treats {@code candidate} as its own.
     *
     * <p>Not memoised: each call posts {@link FriendEvent.Relation}, and a listener is free to consult the
     * world. A caller that asks about the same pair many times inside one tick should hold on to the
     * answer itself rather than expect this to be free.</p>
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
     * The full verdict for a judge named by id.
     *
     * <p>The event gets the first word; {@code DEFAULT} from it means "hand this to the built-in friend
     * system", which is the last word and never posts anything back. The built-in system answers from the
     * judge's own list when the judge is loaded and from {@link FriendCache} when they are not, so
     * {@code DEFAULT} escapes this method only for a judge the mirror has never seen.</p>
     *
     * <p>A caller that has to act on that answer then has to decide what "nobody knows" means for it — see
     * {@code FormationRelations#affects}, where it means a hostile formation does not fire.</p>
     *
     * @param judge the judge entity, or null when that player is not loaded
     */
    public static TriState identify(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        FriendEvent.Relation event = new FriendEvent.Relation(judgeId, judge, candidate);
        NeoForge.EVENT_BUS.post(event);
        return event.answered() ? event.result() : builtin(judgeId, judge, candidate);
    }

    /**
     * The built-in answer on its own, without posting anything.
     *
     * <p>Safe to call from inside a {@link FriendEvent.Relation} listener, which is what makes "the lists,
     * plus my own additions" expressible without recursing back into the event — and why a listener must
     * never call {@link #identify} instead, which would post the event again from inside itself.</p>
     */
    public static TriState builtin(Entity judge, Entity candidate) {
        return builtin(judge.getUUID(), judge, candidate);
    }

    /**
     * The fallback answer for a judge named by id.
     *
     * <p>An entity is its own friend, which needs no list, so self is answered first. After that, a judge
     * who is loaded is read straight off their attachment, and a judge who is not is answered from
     * {@link FriendCache} — the mirror filled in at the ends of a session. Only a judge the mirror has
     * never seen leaves the question genuinely unanswered.</p>
     *
     * <p>Only players can have a friend list — the attachment is entity-scoped, but nothing else is ever
     * given one — so a loaded judge that is any other kind of entity is nobody's friend by this route, and
     * has to speak through the event instead. That is {@code FALSE} rather than no answer: the list was
     * asked for, and the answer is that it does not exist.</p>
     */
    public static TriState builtin(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        if (judgeId.equals(candidate.getUUID())) return TriState.TRUE;
        if (judge == null) return FriendCache.lookup(judgeId, candidate.getUUID());
        if (!(judge instanceof Player)) return TriState.FALSE;
        FriendAttachment friends = judge.getExistingData(MxtAttachments.FRIEND).orElse(null);
        return TriState.from(friends != null && friends.isFriend(candidate.getUUID()));
    }
}
