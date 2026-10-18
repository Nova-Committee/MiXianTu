package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The objects a formula is evaluated against — caster, target, resource subject, random source, registry access —
 * plus the explicit values {@link #variables()} carries. Variables read their number out of the objects on demand,
 * and a derived context shares the explicit value map instead of copying it.
 */
public final class FormulaContext {
    public static final FormulaContext EMPTY = new FormulaContext(Map.of(), RandomSource.create(), null, null, null, null, null, false);
    private final Map<String, Double> variables;
    private final RandomSource random;
    @Nullable
    private final Provider registries;
    @Nullable
    private final Player player;
    @Nullable
    private final Entity caster, target;
    @Nullable
    private final ResourceSubject resource;

    // copy=false is only safe for a map the caller owns and will not mutate afterwards.
    public FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random, @Nullable Provider registries, @Nullable Player player, @Nullable Entity caster, @Nullable Entity target, @Nullable ResourceSubject resource, boolean copy) {
        Objects.requireNonNull(variables, "variables");
        this.variables = copy ? new LinkedHashMap<>(variables) : variables;
        this.random = Objects.requireNonNull(random, "random");
        this.registries = registries;
        this.player = player;
        this.caster = caster;
        this.target = target;
        this.resource = resource;
    }

    // Uses the entity's authoritative random source.
    public static FormulaContext of(Entity entity) {
        return FormulaContexts.forEntity(entity, Map.of());
    }

    public static FormulaContext of(Entity entity, Map<String, Double> extra) {
        return FormulaContexts.forEntity(entity, extra);
    }

    public static FormulaContext of(Level level) {
        return of(level, Map.of());
    }

    /**
     * A context over a registry access alone, for a caller that holds one but has no entity to build from - a
     * tooltip on a client, or a reader looking a definition up outside any world.
     */
    public static FormulaContext of(Provider registries) {
        return EMPTY.withRegistries(registries);
    }

    /**
     * A level context carries no entity, so the entity variables are not available in it.
     */
    public static FormulaContext of(Level level, Map<String, Double> extra) {
        return new FormulaContext(FormulaContexts.finite(extra), level.getRandom(), level.registryAccess(), null, null, null, null, false);
    }

    // The returned map must not be modified.
    public Map<String, Double> variables() {
        return this.variables;
    }

    public RandomSource random() {
        return this.random;
    }

    // The one place a variable or a value provider reads datapack content from. Null only when the context was
    // built without an entity, a level or an explicit access - the shared EMPTY and contexts derived from it -
    // so a reader answers "nothing" instead of reaching for a server that may not be running.
    @Nullable
    public Provider registries() {
        return this.registries;
    }

    @Nullable
    public Player player() {
        return this.player;
    }

    @Nullable
    public Entity caster() {
        return this.caster;
    }

    @Nullable
    public Entity target() {
        return this.target;
    }

    @Nullable
    public ResourceSubject resource() {
        return this.resource;
    }

    public double value(String name) {
        double explicit = this.explicit(name);
        return Double.isNaN(explicit) ? FormulaVariables.resolve(name, this) : explicit;
    }

    // Explicit values are validated to be finite, so NaN reliably means "absent" and callers can skip
    // the boxed map lookup.
    public double explicit(String name) {
        Double value = this.variables.get(name);
        return value == null ? Double.NaN : value;
    }

    public boolean contains(String name) {
        return this.variables.containsKey(name) || !Double.isNaN(FormulaVariables.peek(name, this));
    }

    public FormulaContext with(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Formula context values must be finite");
        Map<String, Double> result = new LinkedHashMap<>(this.variables);
        result.put(name, value);
        return new FormulaContext(result, this.random, this.registries, this.player, this.caster, this.target, this.resource, false);
    }

    // For a caller holding an access its entities cannot supply, such as a block entity reading a registry on a
    // client that has no entity in scope.
    public FormulaContext withRegistries(@Nullable Provider registries) {
        return new FormulaContext(this.variables, this.random, registries, this.player, this.caster, this.target, this.resource, false);
    }

    // Adopts the new caster's random source and registry access, the caster being what the formula is evaluated for.
    public FormulaContext withCaster(@Nullable Entity caster) {
        if (caster == null) return this;
        return new FormulaContext(this.variables, caster.getRandom(), caster.level().registryAccess(), playerOf(caster, this.target), caster, this.target, this.resource, false);
    }

    // The target only supplies an access when the context has none: a caster, once there is one, stays authoritative.
    public FormulaContext withTarget(@Nullable Entity target) {
        if (target == null) return this;
        Provider registries = this.registries != null ? this.registries : target.level().registryAccess();
        return new FormulaContext(this.variables, this.random, registries, playerOf(this.caster, target), this.caster, target, this.resource, false);
    }

    public FormulaContext withResource(CultivationAttachment cultivation, Holder<Resource> resource) {
        return new FormulaContext(this.variables, this.random, this.registries, this.player, this.caster, this.target,
                new ResourceSubject(cultivation, resource), false);
    }

    private static Player playerOf(@Nullable Entity caster, @Nullable Entity target) {
        if (caster instanceof Player player) return player;
        return target instanceof Player player ? player : null;
    }

    //FIXME:?
    public record ResourceSubject(CultivationAttachment cultivation, Holder<Resource> resource) {
        public ResourceSubject {
            Objects.requireNonNull(cultivation, "cultivation");
            Objects.requireNonNull(resource, "resource");
        }
    }
}
