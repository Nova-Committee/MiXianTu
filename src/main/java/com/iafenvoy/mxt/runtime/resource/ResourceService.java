package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

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

    /**
     * Applies a cultivation profile's passive regeneration over the elapsed ticks.
     */
    public static Result regenerate(ResourceHolderAttachment holder, Holder<Resource> resource, NumberProvider regen,
                                    long elapsedTicks, FormulaContext context) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        double amount = regen.evaluate(context);
        if (!Double.isFinite(amount)) return Result.invalid();
        return change(holder, resource, amount * elapsedTicks, context);
    }

    /**
     * Adds resource-specific cultivation variables to a caller-provided formula context.
     * {@code absorbed_aura} is this resource's accumulated cultivation progress and is
     * deliberately zero when the player has no realm stage in this resource's chain.
     */
    public static FormulaContext formulaContext(CultivationAttachment spirit, Holder<Resource> resource, FormulaContext base) {
        return base.withResource(spirit, resource);
    }

    public static FormulaContext formulaContext(CultivationAttachment spirit, Identifier resource, Resource definition, FormulaContext base) {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, resource)
                .map(value -> formulaContext(spirit, value, base)).orElse(base);
    }

    /**
     * Builds the same resource context on either logical side from an entity attachment.
     */
    public static FormulaContext formulaContext(LivingEntity entity, Holder<Resource> resource, FormulaContext base) {
        return FormulaContexts.forEntity(entity, base)
                .withResource(entity.getData(MxtAttachments.CULTIVATION), resource);
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

    /**
     * Rank of this entity's stage in the chain, or {@code -1} when it has no stage in it. The chain
     * holder is the state key, so no registry lookup is needed. Read by the resource formula variables.
     */
    public static int realmRank(CultivationAttachment spirit, Holder<CultivationProfile> cultivation) {
        Holder<RealmStage> current = spirit.realmStage(cultivation);
        if (current != null) {
            Identifier currentId = HolderHelper.id(current);
            Optional<Integer> cached = ServerCache.get().flatMap(cache -> cache.rankForRealm(currentId));
            if (cached.isPresent()) return cached.get();
            Holder<RealmStage> stage = cultivation.value().firstRealm().orElse(null);
            for (int rank = 0; stage != null && rank < 1024; rank++) {
                if (stage.equals(current)) return rank;
                stage = stage.value().nextRealm().orElse(null);
            }
            return -1;
        }
        // A null realm stage represents a mortal, whose formulas still use the
        // chain's first realm as the pending cultivation stage.
        return cultivation.value().firstRealm()
                .map(first -> ServerCache.get().flatMap(cache -> cache.rankForRealm(HolderHelper.id(first))).orElse(0))
                .orElse(-1);
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
