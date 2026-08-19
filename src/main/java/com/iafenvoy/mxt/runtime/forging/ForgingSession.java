package com.iafenvoy.mxt.runtime.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Server-side forging meter state, including the six-step suffix rule and BFS optimal-step calculation.
 */
public final class ForgingSession {
    private final ForgingPlan plan;
    private final int optimalSteps;
    private final List<Identifier> history = new LinkedList<>();
    private int value;
    private int steps;

    public ForgingSession(ForgingPlan plan) {
        this.plan = plan;
        this.optimalSteps = plan.optimalSteps();
    }

    private ForgingSession(ForgingPlan plan, int value, int steps, int optimalSteps, List<Identifier> history) {
        this.plan = plan;
        if (!plan.inBounds(value) || steps < 0 || optimalSteps < 0 || history.size() > 6) {
            throw new IllegalArgumentException("Invalid forging session snapshot");
        }
        this.value = value;
        this.steps = steps;
        this.optimalSteps = optimalSteps;
        this.history.addAll(history);
    }

    public Snapshot snapshot() {
        return new Snapshot(this.value, this.steps, this.optimalSteps, this.history.stream().toList());
    }

    public static ForgingSession restore(ForgingPlan plan, Snapshot snapshot) {
        return new ForgingSession(plan, snapshot.value(), snapshot.steps(), snapshot.optimalSteps(), snapshot.history());
    }

    public int value() {
        return this.value;
    }

    public int steps() {
        return this.steps;
    }

    public int optimalSteps() {
        return this.optimalSteps;
    }

    public List<Identifier> history() {
        return this.history;
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

    public boolean canStrike(Identifier method) {
        if (this.steps >= this.plan.maxSteps()) return false;
        try {
            return this.plan.inBounds(this.value + this.plan.delta(method));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean canComplete() {
        return this.plan.inTarget(this.value) && suffixMatches(this.history, this.plan.finishPattern(), this.plan.requiredSuffixSteps());
    }

    public int extraSteps() {
        if (!this.canComplete()) {
            throw new IllegalStateException("Forging session does not meet completion requirements");
        }
        return this.steps - this.optimalSteps;
    }

    public static int findOptimalSteps(ForgingPlan plan) {
        return plan.optimalSteps();
    }

    private static boolean suffixMatches(Iterable<Identifier> history, List<Identifier> pattern, int requiredSteps) {
        if (requiredSteps == 0) return true;
        List<Identifier> values = new ArrayList<>();
        history.forEach(values::add);
        if (values.size() < requiredSteps) return false;
        for (int index = 0; index < requiredSteps; index++) {
            if (!values.get(values.size() - requiredSteps + index).equals(pattern.get(6 - requiredSteps + index)))
                return false;
        }
        return true;
    }

    public record Snapshot(int value, int steps, int optimalSteps, List<Identifier> history) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("value").forGetter(Snapshot::value), Codec.INT.fieldOf("steps").forGetter(Snapshot::steps),
                Codec.INT.fieldOf("optimal_steps").forGetter(Snapshot::optimalSteps), Identifier.CODEC.listOf().fieldOf("history").forGetter(Snapshot::history)
        ).apply(i, Snapshot::new));

        public Snapshot {
            history = new LinkedList<>(history);
        }
    }
}
