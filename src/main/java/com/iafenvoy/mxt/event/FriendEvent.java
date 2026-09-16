package com.iafenvoy.mxt.event;

import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Identification hooks for the friend system: who counts as whose. The judge is named by
 * {@link #judgeId()} with the entity along only when it is loaded, so a source that keeps its own data per
 * player can answer for somebody who is offline; asking only with an entity would make that case
 * unanswerable.
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
     * That entity, when it is loaded. Empty while the judge is offline, which a manager-level source can
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
     * An entity asking whether another counts as its own, and the extension point for every judgement that is
     * not the holder's own friend list. A listener answers {@code TRUE} when the pair is friendly by its own
     * rules, leaves {@code DEFAULT} to hand the question to the friend lists, and writes {@code FALSE} only
     * when it knows the pair is hostile: {@code FALSE} overrides every other source, including the player's
     * own list. {@link #judge()} is empty for an offline player, which a listener has to tolerate.
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
