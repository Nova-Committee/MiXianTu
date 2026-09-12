package com.iafenvoy.mxt.runtime.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedList;
import java.util.List;

/**
 * Server-side forging progress: the current value, the step count and the last six methods struck, judged
 * against the {@link ForgingPlan} the session was started from.
 *
 * <p>The division is the point of the pair. Everything a blueprint decides - the meter range, the target,
 * the finish pattern, the per-method deltas, the step limit and the shortest possible run - belongs to the
 * plan, which is immutable and snapshotted when the session starts. What is left here is only what changes
 * while the player strikes. So this class owns no rule of its own: {@link #canStrike} and
 * {@link #canComplete} are questions asked of the plan, and {@link #optimalSteps} reads the plan rather
 * than keeping a second copy that a save file could disagree with.</p>
 */
public final class ForgingSession {
    private final ForgingPlan plan;
    private final List<Identifier> history = new LinkedList<>();
    private int value;
    private int steps;

    public ForgingSession(ForgingPlan plan) {
        this.plan = plan;
    }

    private ForgingSession(ForgingPlan plan, int value, int steps, List<Identifier> history) {
        this.plan = plan;
        if (!plan.inBounds(value) || steps < 0 || history.size() > 6) {
            throw new IllegalArgumentException("Invalid forging session snapshot");
        }
        this.value = value;
        this.steps = steps;
        this.history.addAll(history);
    }

    public Snapshot snapshot() {
        return new Snapshot(this.value, this.steps, this.history.stream().toList());
    }

    public static ForgingSession restore(ForgingPlan plan, Snapshot snapshot) {
        return new ForgingSession(plan, snapshot.value(), snapshot.steps(), snapshot.history());
    }

    public int value() {
        return this.value;
    }

    public int steps() {
        return this.steps;
    }

    /**
     * The methods struck, oldest first, as an immutable copy.
     *
     * <p>Package private, and a copy even so. The live list is this class's own state, and the one reader
     * outside it is {@link ForgingSessionView}; what leaves this class to be persisted is
     * {@link #snapshot()}.</p>
     */
    List<Identifier> history() {
        return List.copyOf(this.history);
    }

    /**
     * The shortest run that satisfies the plan, taken from the plan itself.
     *
     * <p>Not stored: it is a property of the plan, and the plan travels with the session anyway - both are
     * fields of the same {@link ForgingTableState}, written and read together. A copy here would only be a
     * second number that a save file, or a plan decoded from a different datapack revision, could set to
     * something the plan does not say.</p>
     */
    public int optimalSteps() {
        return this.plan.optimalSteps();
    }

    public boolean strike(Identifier method) {
        if (!this.canStrike(method)) return false;
        this.value += this.plan.delta(method);
        this.steps++;
        if (this.history.size() == 6) {
            this.history.removeFirst();
        }
        this.history.addLast(method);
        return true;
    }

    /**
     * Whether the method may be struck now, which needs both a step left in the budget and a value that
     * stays inside the meter.
     *
     * <p>A method the plan does not list is refused rather than raised: see
     * {@link ForgingPlan#deltaIfAllowed}.</p>
     */
    public boolean canStrike(Identifier method) {
        if (this.steps >= this.plan.maxSteps()) return false;
        Integer delta = this.plan.deltaIfAllowed(method);
        return delta != null && this.plan.inBounds(this.value + delta);
    }

    public boolean canComplete() {
        return this.plan.inTarget(this.value)
                && ForgingPlan.suffixMatches(this.history, this.plan.finishPattern(), this.plan.requiredSuffixSteps());
    }

    public int extraSteps() {
        if (!this.canComplete()) {
            throw new IllegalStateException("Forging session does not meet completion requirements");
        }
        return this.steps - this.optimalSteps();
    }

    /**
     * The persistable half of a session: its progress, and nothing that belongs to the plan.
     *
     * <p>The plan is written beside it - see {@code ForgingTableState} - so {@code optimal_steps} used to be
     * stored here as well and is not any more. Reading an older save that still carries it is fine: the
     * decoder takes the fields it knows and ignores the rest, and this side now recovers the number from the
     * plan that was saved next to it.</p>
     */
    public record Snapshot(int value, int steps, List<Identifier> history) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("value").forGetter(Snapshot::value), Codec.INT.fieldOf("steps").forGetter(Snapshot::steps),
                Identifier.CODEC.listOf().fieldOf("history").forGetter(Snapshot::history)
        ).apply(i, Snapshot::new));

        public Snapshot {
            history = new LinkedList<>(history);
        }
    }
}
