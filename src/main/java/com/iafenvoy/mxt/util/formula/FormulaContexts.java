package com.iafenvoy.mxt.util.formula;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Factories for formula contexts.
 *
 * <p>A context only records which objects the formula is evaluated against. The entity and
 * resource variables are read out of those objects on demand, so creating a context costs
 * nothing beyond the event values the caller passes in.</p>
 */
public final class FormulaContexts {
    private FormulaContexts() {
    }

    public static FormulaContext forEntity(@NotNull Entity entity) {
        return forEntity(entity, Map.of());
    }

    public static FormulaContext forEntity(@NotNull Entity entity, @NotNull Map<String, Double> extra) {
        return new FormulaContext(finite(extra), entity.getRandom(), playerOf(entity, null), entity, null, null, false);
    }

    public static FormulaContext forEntities(@NotNull Entity caster, @NotNull Entity target, @NotNull Map<String, Double> extra) {
        return new FormulaContext(finite(extra), caster.getRandom(), playerOf(caster, target), caster, target, null, false);
    }

    /**
     * Adds the acting entity to an existing context while keeping its explicit values,
     * its target and its resource subject.
     */
    public static FormulaContext forEntity(@NotNull Entity entity, @NotNull FormulaContext base) {
        return base.withCaster(entity);
    }

    /**
     * Adds both entities of a bi-entity formula to an existing context.
     */
    public static FormulaContext forEntities(@NotNull Entity caster, @NotNull Entity target, @NotNull FormulaContext base) {
        return base.withCaster(caster).withTarget(target);
    }

    /**
     * Keeps the entries a formula can actually read: a null or non-finite extra value is dropped
     * instead of failing the evaluation. A map that is already immutable and clean is reused as
     * it is, so the common {@code Map.of(...)} payload costs nothing.
     */
    static Map<String, Double> finite(Map<String, Double> extra) {
        if (extra.isEmpty()) return Map.of();
        for (Entry<String, Double> entry : extra.entrySet()) {
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value)) return sanitise(extra);
        }
        return Map.copyOf(extra);
    }

    private static Map<String, Double> sanitise(Map<String, Double> extra) {
        Map<String, Double> values = new LinkedHashMap<>();
        extra.forEach((key, value) -> {
            if (value != null && Double.isFinite(value)) values.put(key, value);
        });
        return Map.copyOf(values);
    }

    private static Player playerOf(Entity caster, Entity target) {
        if (caster instanceof Player player) return player;
        return target instanceof Player player ? player : null;
    }
}
