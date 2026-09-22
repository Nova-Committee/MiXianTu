package com.iafenvoy.mxt.event;

import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Identification hooks for the friend system: who counts as whose. The judge is named by {@link #judgeId()} with the
 * entity along only when it is loaded, so a source that keeps its own data per player can answer for somebody who is
 * offline; asking only with an entity would make that case unanswerable.
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

    public UUID judgeId() {
        return this.judgeId;
    }

    // Empty while the judge is offline, which a manager-level source can still answer and a friend list cannot.
    public Optional<Entity> judge() {
        return Optional.ofNullable(this.judge);
    }

    public Entity candidate() {
        return this.candidate;
    }

    /**
     * An entity asking whether another counts as its own: the extension point for every judgement that is not the
     * holder's own friend list. A listener answers {@code TRUE} when the pair is friendly by its own rules, leaves
     * {@code DEFAULT} to hand the question to the friend lists, and writes {@code FALSE} only when it knows the pair
     * is hostile: {@code FALSE} overrides every other source, including the player's own list.
     */
    public static final class Relation extends FriendEvent {
        private TriState result = TriState.DEFAULT;

        public Relation(UUID judgeId, @Nullable Entity judge, Entity candidate) {
            super(judgeId, judge, candidate);
        }

        public Relation(Entity judge, Entity candidate) {
            this(judge.getUUID(), judge, candidate);
        }

        // DEFAULT while nobody has claimed the question.
        public TriState result() {
            return this.result;
        }

        // TRUE means "treat the candidate as mine", FALSE "not this one, whatever the lists say", DEFAULT gives it back.
        public void setResult(TriState result) {
            this.result = result;
        }

        public boolean answered() {
            return this.result != TriState.DEFAULT;
        }
    }
}
