package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Applies resource definition bounds consistently for initialization, changes and passive regeneration.
 */
public final class ResourceService {
    private ResourceService() {
    }

    public static Result initialize(ResourceHolderData holder, Identifier id, Resource definition, FormulaContext context) {
        if (holder.contains(id)) return Result.unchanged(holder.get(id));
        Bounds bounds = bounds(definition, context);
        if (bounds == null) return Result.invalid();
        double value = definition.defaultValue().evaluate(context);
        if (!Double.isFinite(value)) return Result.invalid();
        double clamped = clamp(value, bounds);
        holder.set(id, clamped, bounds.min(), bounds.max(), -1L, "initialize");
        return Result.changed(clamped);
    }

    public static Result change(ResourceHolderData holder, Identifier id, Resource definition, double amount, FormulaContext context) {
        if (!Double.isFinite(amount)) return Result.invalid();
        Result initialized = initialize(holder, id, definition, context);
        if (!initialized.valid()) return initialized;
        Bounds bounds = bounds(definition, context);
        if (bounds == null) return Result.invalid();
        double value = clamp(holder.get(id) + amount, bounds);
        if (Double.compare(value, holder.get(id)) == 0) return Result.unchanged(value);
        holder.set(id, value, bounds.min(), bounds.max(), holder.audit(id).lastChangedTick(), "resource_change");
        return Result.changed(value);
    }

    public static Result regenerate(ResourceHolderData holder, Identifier id, Resource definition, long elapsedTicks, FormulaContext context) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        double regen = definition.regen().evaluate(context);
        if (!Double.isFinite(regen)) return Result.invalid();
        return change(holder, id, definition, regen * elapsedTicks, context);
    }

    /**
     * Adds resource-specific cultivation variables to a caller-provided formula context.
     * {@code absorbed_aura} is the active realm's accumulated cultivation progress and is
     * deliberately zero for every resource outside that realm chain.
     */
    public static FormulaContext formulaContext(SpiritData spirit, Identifier resource, Resource definition, FormulaContext base) {
        Map<String, Double> values = new LinkedHashMap<>(base.variables());
        int realmRank = realmRank(spirit, resource, definition);
        boolean matchesResource = spirit.realmStage().isPresent() && realmRank >= 0;
        double absorbedAura = matchesResource ? spirit.cultivationProgress() : 0.0D;
        int resolvedRank = Math.max(0, realmRank);
        values.put("realm", (double) resolvedRank);
        values.put("realm_rank", (double) resolvedRank);
        values.put("level", (double) resolvedRank);
        values.put("absorbed_aura", absorbedAura);
        values.put("cultivation_progress", absorbedAura);
        return new FormulaContext(values, base.random());
    }

    /** Builds the same resource context on either logical side from an entity attachment. */
    public static FormulaContext formulaContext(LivingEntity entity, Identifier resource, Resource definition, FormulaContext base) {
        return formulaContext(entity.getData(MxtAttachments.SPIRIT_DATA), resource, definition,
                FormulaContexts.forEntity(entity, base.variables()));
    }

    public static Optional<Bounds> resolveBounds(Resource definition, FormulaContext context) {
        double min = definition.min().evaluate(context);
        double max = definition.max().evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max ? Optional.of(new Bounds(min, max)) : Optional.empty();
    }

    private static Bounds bounds(Resource definition, FormulaContext context) {
        return resolveBounds(definition, context).orElse(null);
    }

    private static int realmRank(SpiritData spirit, Identifier resource, Resource definition) {
        Identifier current = spirit.realmStage().orElse(null);
        if (current == null) return -1;
        Optional<Integer> cached = ServerCache.get()
                .filter(cache -> cache.resourceForRealm(current).filter(resource::equals).isPresent())
                .flatMap(cache -> cache.rankForRealm(current));
        if (cached.isPresent()) return cached.get();

        Holder<RealmStage> stage = definition.firstRealm().orElse(null);
        for (int rank = 0; stage != null && rank < 1024; rank++) {
            if (HolderHelper.id(stage).equals(current)) return rank;
            stage = stage.value().nextRealm().orElse(null);
        }
        return -1;
    }

    private static double clamp(double value, Bounds bounds) {
        return Math.max(bounds.min(), Math.min(bounds.max(), value));
    }

    public record Bounds(double min, double max) {
    }

    public record Result(boolean valid, boolean changed, double value) {
        private static Result invalid() {
            return new Result(false, false, Double.NaN);
        }

        private static Result changed(double value) {
            return new Result(true, true, value);
        }

        private static Result unchanged(double value) {
            return new Result(true, false, value);
        }
    }
}
