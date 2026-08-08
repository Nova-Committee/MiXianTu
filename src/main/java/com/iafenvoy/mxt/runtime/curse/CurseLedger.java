package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.Curse.StackingMode;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;

import java.util.*;

/**
 * Applies stacking and expiry rules without allowing consumers to mutate curse state directly.
 */
public final class CurseLedger {
    private final Map<Holder<Curse>, CurseInstance> instances = new LinkedHashMap<>();

    public CurseLedger() {
    }

    public CurseLedger(Map<Holder<Curse>, CurseInstance> instances) {
        this.instances.putAll(instances);
    }

    public synchronized CurseInstance apply(Holder<Curse> curse, int requestedStacks, long gameTime, FormulaContext context, String source) {
        return this.apply(curse, requestedStacks, gameTime, context, source, Optional.empty());
    }

    public synchronized CurseInstance apply(Holder<Curse> curse, int requestedStacks, long gameTime,
                                            FormulaContext context, String source, Optional<Long> durationOverride) {
        if (requestedStacks <= 0) {
            throw new IllegalArgumentException("requestedStacks must be positive");
        }
        Curse definition = curse.value();
        CurseInstance current = this.instances.get(curse);
        long expiresAt = expiry(definition, gameTime, context, durationOverride);
        if (current == null || definition.stackingMode() == StackingMode.REPLACE) {
            CurseInstance created = new CurseInstance(curse, Math.min(requestedStacks, definition.maxStacks()), gameTime, expiresAt, source);
            this.instances.put(curse, created);
            return created;
        }
        int minStacks = Math.min(definition.maxStacks(), current.stacks() + requestedStacks);
        CurseInstance updated = switch (definition.stackingMode()) {
            case IGNORE -> current;
            case REFRESH_DURATION ->
                    new CurseInstance(curse, current.stacks(), current.appliedAt(), expiresAt, current.source());
            case ADD_STACKS_REFRESH_DURATION ->
                    new CurseInstance(curse, minStacks, current.appliedAt(), expiresAt, current.source());
            case ADD_STACKS_KEEP_DURATION ->
                    new CurseInstance(curse, minStacks, current.appliedAt(), current.expiresAt(), current.source());
            case REPLACE -> throw new IllegalStateException("Handled above");
        };
        this.instances.put(curse, updated);
        return updated;
    }

    public synchronized Optional<CurseInstance> remove(Holder<Curse> curse) {
        return Optional.ofNullable(this.instances.remove(curse));
    }

    public synchronized List<CurseInstance> removeExpired(long gameTime) {
        List<CurseInstance> expired = new ArrayList<>();
        this.instances.entrySet().removeIf(entry -> {
            if (entry.getValue().expiredAt(gameTime)) {
                expired.add(entry.getValue());
                return true;
            }
            return false;
        });
        return expired;
    }

    public synchronized Optional<CurseInstance> get(Holder<Curse> curse) {
        return Optional.ofNullable(this.instances.get(curse));
    }

    public synchronized Map<Holder<Curse>, CurseInstance> snapshot() {
        return this.instances;
    }

    private static long expiry(Curse definition, long gameTime, FormulaContext context, Optional<Long> durationOverride) {
        double duration = durationOverride.map(Long::doubleValue).orElseGet(() -> definition.durationTicks().evaluate(context));
        if (!Double.isFinite(duration) || duration < 0.0D) {
            throw new IllegalStateException("Curse duration must be finite and non-negative");
        }
        return definition.typedType().expiry(Math.round(duration), gameTime);
    }
}
