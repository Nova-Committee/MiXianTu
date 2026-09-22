package com.iafenvoy.mxt.runtime.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedList;
import java.util.List;

/**
 * Server-side forging progress - the current value, the step count and the last six methods struck - judged
 * against the immutable {@link ForgingPlan} it was started from. Everything a blueprint decides belongs to the
 * plan, so this keeps no second copy of a plan property that a save file could disagree with.
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

    // What leaves this class to be persisted is {@link #snapshot()}.
    List<Identifier> history() {
        return List.copyOf(this.history);
    }

    // Taken from the plan rather than stored: a copy here would only be a second number a save file could
    // contradict.
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

    // Needs both a step left in the budget and a value that stays inside the meter; a method the plan does not
    // list is refused rather than raised.
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

    // The persistable half of a session: its progress, and nothing that belongs to the plan.
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
