package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.Curse.StackingMode;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Applies stacking and expiry rules without letting consumers mutate curse state directly.
 * <p>
 * Nothing here throws on a definition it cannot honour: an application it cannot resolve an expiry for comes back
 * empty, so a malformed definition cannot take a tick or an event handler down with it.
 */
public final class CurseLedger {
    private final Map<Holder<Curse>, CurseInstance> instances = new LinkedHashMap<>();

    public CurseLedger() {
    }

    public CurseLedger(Map<Holder<Curse>, CurseInstance> instances) {
        this.instances.putAll(instances);
    }

    public synchronized Optional<CurseInstance> apply(Holder<Curse> curse, int requestedStacks, long gameTime,
                                                      FormulaContext context, Optional<Long> durationOverride) {
        if (requestedStacks <= 0) return Optional.empty();
        Curse definition = curse.value();
        OptionalLong resolved = expiry(definition, gameTime, context, durationOverride);
        if (resolved.isEmpty()) return Optional.empty();
        long expiresAt = resolved.getAsLong();
        CurseInstance current = this.instances.get(curse);
        if (current == null || definition.stackingMode() == StackingMode.REPLACE) {
            CurseInstance created = new CurseInstance(curse, Math.min(requestedStacks, definition.maxStacks()), gameTime, expiresAt);
            this.instances.put(curse, created);
            return Optional.of(created);
        }
        int minStacks = Math.min(definition.maxStacks(), current.stacks() + requestedStacks);
        CurseInstance updated = switch (definition.stackingMode()) {
            case IGNORE -> current;
            case REFRESH_DURATION ->
                    new CurseInstance(curse, current.stacks(), current.appliedAt(), expiresAt);
            case ADD_STACKS_REFRESH_DURATION ->
                    new CurseInstance(curse, minStacks, current.appliedAt(), expiresAt);
            case ADD_STACKS_KEEP_DURATION ->
                    new CurseInstance(curse, minStacks, current.appliedAt(), current.expiresAt());
            case REPLACE -> throw new IllegalStateException("Handled above");
        };
        this.instances.put(curse, updated);
        return Optional.of(updated);
    }

    public synchronized Optional<CurseInstance> remove(Holder<Curse> curse) {
        return Optional.ofNullable(this.instances.remove(curse));
    }

    public synchronized Map<Holder<Curse>, CurseInstance> snapshot() {
        return this.instances;
    }

    // A caller-supplied duration may shorten a curse but never outlast the definition: content wanting a longer
    // curse writes a longer duration_ticks, so nobody can hand out an effectively permanent instance.
    private static OptionalLong expiry(Curse definition, long gameTime, FormulaContext context, Optional<Long> durationOverride) {
        double duration = definition.durationTicks().evaluate(context);
        if (!Double.isFinite(duration) || duration < 0.0D) return OptionalLong.empty();
        OptionalLong declared = definition.typedType().expiry(Math.round(duration), gameTime);
        if (declared.isEmpty() || durationOverride.isEmpty()) return declared;
        OptionalLong overridden = definition.typedType().expiry(durationOverride.get(), gameTime);
        if (overridden.isEmpty()) return OptionalLong.empty();
        long declaredValue = declared.getAsLong();
        long overriddenValue = overridden.getAsLong();
        // A definition that never expires is only ever shortened by an override; one that expires is never
        // extended, and never turned permanent.
        if (declaredValue < 0L) return overridden;
        if (overriddenValue < 0L) return declared;
        return OptionalLong.of(Math.min(declaredValue, overriddenValue));
    }
}
