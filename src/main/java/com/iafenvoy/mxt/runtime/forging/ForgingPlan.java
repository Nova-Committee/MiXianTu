package com.iafenvoy.mxt.runtime.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;

/**
 * Already-resolved forging plan used by sessions and optimal-step search.
 */
public record ForgingPlan(int meterMin, int meterMax, int targetMin, int targetMax, List<Identifier> finishPattern,
                          int requiredSuffixSteps, Map<Identifier, Integer> deltas, int maxSteps, int optimalSteps) {
    public static final Codec<ForgingPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("meter_min").forGetter(ForgingPlan::meterMin),
            Codec.INT.fieldOf("meter_max").forGetter(ForgingPlan::meterMax),
            Codec.INT.fieldOf("target_min").forGetter(ForgingPlan::targetMin),
            Codec.INT.fieldOf("target_max").forGetter(ForgingPlan::targetMax),
            Identifier.CODEC.listOf().fieldOf("finish_pattern").forGetter(ForgingPlan::finishPattern),
            Codec.intRange(0, 6).fieldOf("required_suffix_steps").forGetter(ForgingPlan::requiredSuffixSteps),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("deltas").forGetter(ForgingPlan::deltas),
            Codec.INT.fieldOf("max_steps").forGetter(ForgingPlan::maxSteps)
    ).apply(i, ForgingPlan::new));

    public ForgingPlan(int meterMin, int meterMax, int targetMin, int targetMax, List<Identifier> finishPattern,
                       int requiredSuffixSteps, Map<Identifier, Integer> deltas, int maxSteps) {
        this(meterMin, meterMax, targetMin, targetMax, finishPattern, requiredSuffixSteps, deltas, maxSteps,
                findOptimalSteps(meterMin, meterMax, targetMin, targetMax, finishPattern, requiredSuffixSteps, deltas));
    }

    public ForgingPlan(int meterMin, int meterMax, int targetMin, int targetMax, List<Identifier> finishPattern,
                       int requiredSuffixSteps, Map<Identifier, Integer> deltas) {
        this(meterMin, meterMax, targetMin, targetMax, finishPattern, requiredSuffixSteps, deltas, Integer.MAX_VALUE);
    }

    public ForgingPlan {
        finishPattern = new LinkedList<>(finishPattern);
        deltas = new LinkedHashMap<>(deltas);
        if (!(meterMin < 0 && meterMax > 0 && meterMin <= targetMin && targetMin <= targetMax && targetMax <= meterMax)) {
            throw new IllegalArgumentException("Invalid forge meter range");
        }
        if (!((finishPattern.isEmpty() && requiredSuffixSteps == 0) || finishPattern.size() == 6) || requiredSuffixSteps < 0 || requiredSuffixSteps > 6) {
            throw new IllegalArgumentException("finishPattern must be empty when unused or contain six entries");
        }
        if (deltas.isEmpty() || deltas.values().stream().anyMatch(delta -> delta == 0)) {
            throw new IllegalArgumentException("Forging plan requires non-zero method deltas");
        }
        if (!deltas.keySet().containsAll(finishPattern)) {
            throw new IllegalArgumentException("Finish pattern references an unavailable method");
        }
        if (maxSteps <= 0 || optimalSteps < 0 || optimalSteps > maxSteps) {
            throw new IllegalArgumentException("maxSteps must cover a reachable optimal forging plan");
        }
    }

    public boolean inTarget(int value) {
        return value >= this.targetMin && value <= this.targetMax;
    }

    public boolean inBounds(int value) {
        return value >= this.meterMin && value <= this.meterMax;
    }

    /**
     * The method's delta, or {@code null} when this plan does not allow it.
     *
     * <p>An unlisted method is an answer, not an accident. The client offers whatever the placed tools
     * currently resolve to, and the session is struck through several layers that can each see a slightly
     * older plan, so "is this method allowed" is a question a caller is entitled to ask. That is what this
     * method is for; {@link #delta} is for callers that have already asked and are entitled to treat the
     * missing entry as the contract violation it now is.</p>
     */
    public @Nullable Integer deltaIfAllowed(@NotNull Identifier method) {
        return this.deltas.get(method);
    }

    /**
     * The method's delta, throwing when the plan does not allow the method.
     *
     * @see #deltaIfAllowed the non-throwing question
     */
    public int delta(@NotNull Identifier method) {
        Integer value = this.deltaIfAllowed(method);
        if (value == null) {
            throw new IllegalArgumentException("Method is not allowed: " + method);
        }
        return value;
    }

    private static int findOptimalSteps(int meterMin, int meterMax, int targetMin, int targetMax,
                                        List<Identifier> pattern, int requiredSteps, Map<Identifier, Integer> deltas) {
        ArrayDeque<SearchState> queue = new ArrayDeque<>();
        Set<SearchState> visited = new HashSet<>();
        Map<SearchState, Integer> distances = new HashMap<>();
        SearchState start = new SearchState(0, List.of());
        queue.add(start);
        visited.add(start);
        distances.put(start, 0);
        while (!queue.isEmpty()) {
            SearchState state = queue.removeFirst();
            int steps = distances.get(state);
            if (state.value >= targetMin && state.value <= targetMax && suffixMatches(state.history, pattern, requiredSteps))
                return steps;
            for (Entry<Identifier, Integer> entry : deltas.entrySet()) {
                int nextValue = state.value + entry.getValue();
                if (nextValue < meterMin || nextValue > meterMax) continue;
                SearchState next = new SearchState(nextValue, append(state.history, entry.getKey()));
                if (visited.add(next)) {
                    distances.put(next, steps + 1);
                    queue.addLast(next);
                }
            }
        }
        throw new IllegalArgumentException("Forging plan cannot reach its target while satisfying the suffix rule");
    }

    /**
     * Whether the last {@code requiredSteps} entries of {@code history} equal the last {@code requiredSteps}
     * entries of the six-long {@code pattern}.
     *
     * <p>Only those are compared. The positions in front of them - which the screen draws as barriers when
     * {@code requiredSteps} is less than six - take no part in the rule, so a pattern's earlier steps are
     * never asked for and a longer history is only ever judged by its tail.</p>
     *
     * <p>One definition, because it has to be one: the optimal-step search below uses this to decide when a
     * session <em>would be</em> finished, and {@link ForgingSession} uses it to decide that it <em>is</em>.
     * Two copies could let the search return a path the session then refuses to accept.</p>
     */
    public static boolean suffixMatches(List<Identifier> history, List<Identifier> pattern, int requiredSteps) {
        if (requiredSteps == 0) return true;
        if (history.size() < requiredSteps) return false;
        for (int index = 0; index < requiredSteps; index++) {
            if (!history.get(history.size() - requiredSteps + index).equals(pattern.get(6 - requiredSteps + index)))
                return false;
        }
        return true;
    }

    private static List<Identifier> append(List<Identifier> history, Identifier method) {
        ArrayList<Identifier> result = new ArrayList<>(Math.min(6, history.size() + 1));
        result.addAll(history.subList(Math.max(0, history.size() - 5), history.size()));
        result.add(method);
        return result;
    }

    private record SearchState(int value, List<Identifier> history) {
        SearchState {
            history = new LinkedList<>(history);
        }
    }
}
