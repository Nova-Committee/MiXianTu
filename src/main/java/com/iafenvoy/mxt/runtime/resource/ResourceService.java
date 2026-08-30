package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
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

    public static Result initialize(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        Resource definition = resource.value();
        if (holder.contains(resource)) return Result.unchanged(holder.get(resource));
        Bounds bounds = bounds(definition, context);
        if (bounds == null) return Result.invalid();
        double value = definition.defaultValue().evaluate(context);
        if (!Double.isFinite(value)) return Result.invalid();
        double clamped = clamp(value, bounds);
        holder.set(resource, clamped, bounds.min(), bounds.max(), -1L, "initialize");
        return Result.changed(clamped);
    }

    public static Result initialize(ResourceHolderAttachment holder, Identifier id, Resource definition, FormulaContext context) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> initialize(holder, resource, context)).orElse(Result.invalid());
    }

    public static Result change(ResourceHolderAttachment holder, Holder<Resource> resource, double amount, FormulaContext context) {
        Resource definition = resource.value();
        if (!Double.isFinite(amount)) return Result.invalid();
        Result initialized = initialize(holder, resource, context);
        if (!initialized.valid()) return initialized;
        Bounds bounds = bounds(definition, context);
        if (bounds == null) return Result.invalid();
        double value = clamp(holder.get(resource) + amount, bounds);
        if (Double.compare(value, holder.get(resource)) == 0) return Result.unchanged(value);
        holder.set(resource, value, bounds.min(), bounds.max(), holder.audit(resource).lastChangedTick(), "resource_change");
        return Result.changed(value);
    }

    public static Result change(ResourceHolderAttachment holder, Identifier id, Resource definition, double amount, FormulaContext context) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> change(holder, resource, amount, context)).orElse(Result.invalid());
    }

    public static Result regenerate(ResourceHolderAttachment holder, Holder<Resource> resource, long elapsedTicks, FormulaContext context) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        double regen = resource.value().regen().evaluate(context);
        if (!Double.isFinite(regen)) return Result.invalid();
        return change(holder, resource, regen * elapsedTicks, context);
    }

    public static Result regenerate(ResourceHolderAttachment holder, Identifier id, Resource definition, long elapsedTicks, FormulaContext context) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> regenerate(holder, resource, elapsedTicks, context)).orElse(Result.invalid());
    }

    /**
     * Adds resource-specific cultivation variables to a caller-provided formula context.
     * {@code absorbed_aura} is this resource's accumulated cultivation progress and is
     * deliberately zero when the player has no realm stage in this resource's chain.
     */
    public static FormulaContext formulaContext(CultivationAttachment spirit, Holder<Resource> resource, FormulaContext base) {
        Resource definition = resource.value();
        Map<String, Double> values = new LinkedHashMap<>(base.variables());
        int realmRank = realmRank(spirit, resource, definition);
        boolean matchesResource = realmRank >= 0;
        double absorbedAura = matchesResource ? spirit.cultivationProgress(resource) : 0.0D;
        int resolvedRank = Math.max(0, realmRank);
        values.put("realm", (double) resolvedRank);
        values.put("realm_rank", (double) resolvedRank);
        values.put("level", (double) resolvedRank);
        values.put("absorbed_aura", absorbedAura);
        values.put("cultivation_progress", absorbedAura);
        return new FormulaContext(values, base.random());
    }

    public static FormulaContext formulaContext(CultivationAttachment spirit, Identifier resource, Resource definition, FormulaContext base) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, resource)
                .map(value -> formulaContext(spirit, value, base)).orElse(base);
    }

    /**
     * Builds the same resource context on either logical side from an entity attachment.
     */
    public static FormulaContext formulaContext(LivingEntity entity, Holder<Resource> resource, FormulaContext base) {
        return formulaContext(entity.getData(MxtAttachments.CULTIVATION), resource,
                FormulaContexts.forEntity(entity, base.variables()));
    }

    public static FormulaContext formulaContext(LivingEntity entity, Identifier resource, Resource definition, FormulaContext base) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, resource)
                .map(value -> formulaContext(entity, value, base)).orElse(base);
    }

    public static Optional<Bounds> resolveBounds(Resource definition, FormulaContext context) {
        double min = definition.min().evaluate(context);
        double max = definition.max().evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max ? Optional.of(new Bounds(min, max)) : Optional.empty();
    }

    private static Bounds bounds(Resource definition, FormulaContext context) {
        return resolveBounds(definition, context).orElse(null);
    }

    private static int realmRank(CultivationAttachment spirit, Holder<Resource> resource, Resource definition) {
        int best = -1;
        for (Holder<RealmStage> current : spirit.realmStages()) {
            if (!current.value().resource().equals(resource)) continue;
            Identifier currentId = HolderHelper.id(current);
            Optional<Integer> cached = ServerCache.get()
                    .filter(cache -> cache.resourceForRealm(currentId).filter(value -> value.equals(HolderHelper.id(resource))).isPresent())
                    .flatMap(cache -> cache.rankForRealm(currentId));
            if (cached.isPresent()) {
                best = Math.max(best, cached.get());
                continue;
            }
            Holder<RealmStage> stage = definition.firstRealm().orElse(null);
            for (int rank = 0; stage != null && rank < 1024; rank++) {
                if (stage.equals(current)) { best = Math.max(best, rank); break; }
                stage = stage.value().nextRealm().orElse(null);
            }
        }
        return best;
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
