package com.iafenvoy.mxt.util.formula;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, server-authoritative variables supplied to a number formula.
 */
public record FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random,
                             @Nullable Player player) {
    public static final FormulaContext EMPTY = new FormulaContext(Map.of());

    public FormulaContext {
        variables = new LinkedHashMap<>(variables);
    }

    public FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random) {
        this(variables, random, null);
    }

    public FormulaContext(@NotNull Map<String, Double> variables) {
        this(variables, RandomSource.create());
    }

    /**
     * Creates an entity context using its authoritative random source.
     */
    public static FormulaContext of(Entity entity) {
        return of(entity, Map.of());
    }

    /**
     * Creates an entity context and adds finite event-specific values.
     */
    public static FormulaContext of(Entity entity, Map<String, Double> extra) {
        return entity instanceof LivingEntity living ? FormulaContexts.forEntity(living, extra)
                : new FormulaContext(extra, entity.getRandom(), entity instanceof Player player ? player : null);
    }

    /**
     * Creates a level context using the level's authoritative random source.
     */
    public static FormulaContext of(Level level) {
        return of(level, Map.of());
    }

    /**
     * Creates a level context and adds finite event-specific values.
     */
    public static FormulaContext of(Level level, Map<String, Double> extra) {
        return new FormulaContext(extra, level.getRandom());
    }

    public double value(String name) {
        Double explicit = this.variables.get(name);
        if (explicit != null) return explicit;
        return FormulaVariables.resolve(name, this);
    }

    public boolean contains(String name) {
        return this.variables.containsKey(name) || FormulaVariables.contains(name);
    }

    public FormulaContext with(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Formula context values must be finite");
        Map<String, Double> result = new LinkedHashMap<>(this.variables);
        result.put(name, value);
        return new FormulaContext(result, this.random, this.player);
    }
}
