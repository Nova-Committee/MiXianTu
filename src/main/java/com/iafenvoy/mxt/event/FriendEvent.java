package com.iafenvoy.mxt.event;

import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Identification hooks for the friend system: who counts as whose.
 *
 * <p>The judge is named by {@link #judgeId()}, with the entity itself along only when it happens to be
 * loaded. That split is deliberate: a friend list lives on the player entity, but a judgement does not have
 * to. A source that keeps its own data per player — a team or faction manager, for instance — can answer for
 * somebody who is offline, and a hostile formation whose owner has logged out needs exactly that. Asking
 * only with an entity would have made the offline case unanswerable by construction.</p>
 */
public abstract class FriendEvent extends Event {
    private final UUID judgeId;
    private final Entity judge;
    private final Entity candidate;

    protected FriendEvent(UUID judgeId, @Nullable Entity judge, Entity candidate) {
        this.judgeId = judgeId;
        this.judge = judge;
        this.candidate = candidate;
    }

    /**
     * Whose verdict is being asked for. Always present, whether or not that player is loaded.
     */
    public UUID judgeId() {
        return this.judgeId;
    }

    /**
     * That entity, when it is loaded. Empty while the judge is offline — the case a manager-level source can
     * still answer and a friend list cannot.
     */
    public Optional<Entity> judge() {
        return Optional.ofNullable(this.judge);
    }

    /**
     * Who that verdict is about. Always a live entity: it is the thing standing in the blast.
     */
    public Entity candidate() {
        return this.candidate;
    }

    /**
     * Asks whether one entity counts another as its own, and is the extension point for every judgement
     * that is not the holder's own friend list.
     *
     * <p>Posted by {@code FriendService} on behalf of whoever is about to make a friendly-fire decision,
     * so that a listener changes the decision everywhere at once rather than at whichever call site its
     * author happened to find. A data pack cannot express this: a friend is a fact about a pair of
     * entities rather than about either one of them, and no condition context carries both.</p>
     *
     * <p>{@link TriState} rather than a boolean, because most listeners have no opinion about most pairs.
     * The answer starts at {@code DEFAULT}, and that is what hands the question to the friend lists; a
     * listener that writes {@code TRUE} or {@code FALSE} takes it over. Writes land directly on the
     * event, so the last listener to write wins — order them with {@code EventPriority}, exactly as the
     * vanilla events that carry a {@code TriState} do.</p>
     *
     * <p>Answer {@code TRUE} whenever the pair is friendly by your own rules and otherwise leave the event
     * at {@code DEFAULT}. Writing {@code FALSE} is a claim that the two are <em>not</em> allies, which
     * overrides every other source including the player's own list; it is the right answer when your source
     * knows the pair is hostile, not merely when it does not recognise them.</p>
     *
     * <p>A listener that needs the judge entity has to tolerate its absence: {@link #judge()} is empty for
     * an offline player, and that is the whole reason the id is separate.</p>
     */
    public static final class Relation extends FriendEvent {
        private TriState result = TriState.DEFAULT;

        public Relation(UUID judgeId, @Nullable Entity judge, Entity candidate) {
            super(judgeId, judge, candidate);
        }

        /**
         * Asks about a judge that is loaded, which is the common case.
         */
        public Relation(Entity judge, Entity candidate) {
            this(judge.getUUID(), judge, candidate);
        }

        /**
         * The verdict so far: {@code DEFAULT} while nobody has claimed the question.
         */
        public TriState result() {
            return this.result;
        }

        /**
         * Claims the question. {@code TRUE} means "treat {@link #candidate()} as mine", {@code FALSE} means
         * "not this one, whatever the lists say", and {@code DEFAULT} gives it back.
         */
        public void setResult(TriState result) {
            this.result = result;
        }

        /**
         * Whether a listener has answered.
         */
        public boolean answered() {
            return this.result != TriState.DEFAULT;
        }
    }
}
