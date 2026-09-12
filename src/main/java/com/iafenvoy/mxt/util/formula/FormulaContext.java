package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import net.minecraft.core.Holder;
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
 * The objects a formula is evaluated against, plus the values that belong to no object.
 *
 * <p>Formula variables are not stored here. A variable reads its number out of {@link #caster()},
 * {@link #target()}, {@link #resource()} or {@link #random()} when a formula asks for the name.
 * {@link #variables()} therefore only carries explicit values: event payloads such as
 * {@code damage} or {@code block_x}, and anything a caller adds with {@link #with(String, double)}.</p>
 *
 * <p>Deriving a context that only changes an object — a new caster, a target, a resource subject —
 * shares the explicit value map instead of copying it, because those maps are never modified in
 * place. Only {@link #with(String, double)} has to build a new map.</p>
 */
public final class FormulaContext {
    public static final FormulaContext EMPTY = new FormulaContext(Map.of(), RandomSource.create(), null, null, null, null, false);

    private final Map<String, Double> variables;
    private final RandomSource random;
    private final Player player;
    private final Entity caster;
    private final Entity target;
    private final ResourceSubject resource;

    public FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random) {
        this(variables, random, null);
    }

    public FormulaContext(@NotNull Map<String, Double> variables) {
        this(variables, RandomSource.create());
    }

    public FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random, @Nullable Player player) {
        this(variables, random, player, null, null, null);
    }

    /**
     * Creates a context from its objects. Prefer the factories in {@link FormulaContexts}.
     */
    public FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random, @Nullable Player player,
                          @Nullable Entity caster, @Nullable Entity target, @Nullable ResourceSubject resource) {
        this(variables, random, player, caster, target, resource, true);
    }

    /**
     * @param copy whether the explicit values must be copied; callers that own an immutable or
     *             freshly built map pass {@code false}. Kept package-private so that only
     *             {@link FormulaContexts} and this class decide ownership.
     */
    FormulaContext(@NotNull Map<String, Double> variables, @NotNull RandomSource random, @Nullable Player player,
                   @Nullable Entity caster, @Nullable Entity target, @Nullable ResourceSubject resource, boolean copy) {
        Objects.requireNonNull(variables, "variables");
        this.variables = copy ? new LinkedHashMap<>(variables) : variables;
        this.random = Objects.requireNonNull(random, "random");
        this.player = player;
        this.caster = caster;
        this.target = target;
        this.resource = resource;
    }

    /**
     * Creates an entity context using its authoritative random source.
     */
    public static FormulaContext of(Entity entity) {
        return FormulaContexts.forEntity(entity, Map.of());
    }

    /**
     * Creates an entity context and adds finite event-specific values.
     */
    public static FormulaContext of(Entity entity, Map<String, Double> extra) {
        return FormulaContexts.forEntity(entity, extra);
    }

    /**
     * Creates a level context using the level's authoritative random source.
     */
    public static FormulaContext of(Level level) {
        return of(level, Map.of());
    }

    /**
     * Creates a level context and adds finite event-specific values. A level context carries no
     * entity, so the entity variables are not available in it.
     */
    public static FormulaContext of(Level level, Map<String, Double> extra) {
        return new FormulaContext(FormulaContexts.finite(extra), level.getRandom(), null, null, null, null, false);
    }

    /**
     * Explicit values only. Entity and resource variables are resolved on demand and are not
     * part of this map. The returned map must not be modified.
     */
    public Map<String, Double> variables() {
        return this.variables;
    }

    public RandomSource random() {
        return this.random;
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

    /**
     * The explicit value of a name, or {@link Double#NaN} when this context carries none. Explicit
     * values are validated to be finite, so NaN reliably means "absent" and callers can avoid the
     * boxed map lookup.
     */
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
        return new FormulaContext(result, this.random, this.player, this.caster, this.target, this.resource, false);
    }

    /**
     * Replaces the acting entity and adopts its random source.
     */
    public FormulaContext withCaster(@Nullable Entity caster) {
        if (caster == null) return this;
        return new FormulaContext(this.variables, caster.getRandom(), playerOf(caster, this.target), caster, this.target, this.resource, false);
    }

    /**
     * Adds or replaces the second entity of a bi-entity formula.
     */
    public FormulaContext withTarget(@Nullable Entity target) {
        if (target == null) return this;
        return new FormulaContext(this.variables, this.random, playerOf(this.caster, target), this.caster, target, this.resource, false);
    }

    /**
     * Binds the cultivation state of one resource, which is what the resource variables read.
     */
    public FormulaContext withResource(CultivationAttachment cultivation, Holder<Resource> resource) {
        return new FormulaContext(this.variables, this.random, this.player, this.caster, this.target,
                new ResourceSubject(cultivation, resource), false);
    }

    private static Player playerOf(@Nullable Entity caster, @Nullable Entity target) {
        if (caster instanceof Player player) return player;
        return target instanceof Player player ? player : null;
    }

    /**
     * The cultivation state of a single resource, as read by {@code realm}, {@code realm_rank},
     * {@code level}, {@code absorbed_aura} and {@code cultivation_progress}.
     */
    public record ResourceSubject(CultivationAttachment cultivation, Holder<Resource> resource) {
        public ResourceSubject {
            Objects.requireNonNull(cultivation, "cultivation");
            Objects.requireNonNull(resource, "resource");
        }
    }
}
