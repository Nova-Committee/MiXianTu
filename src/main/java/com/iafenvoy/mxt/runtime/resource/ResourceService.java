package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;

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
        holder.set(id, clamped, bounds.max(), -1L, "initialize");
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
        holder.set(id, value, bounds.max(), holder.audit(id).lastChangedTick(), "resource_change");
        return Result.changed(value);
    }

    public static Result regenerate(ResourceHolderData holder, Identifier id, Resource definition, long elapsedTicks, FormulaContext context) {
        if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
        double regen = definition.regen().evaluate(context);
        if (!Double.isFinite(regen)) return Result.invalid();
        return change(holder, id, definition, regen * elapsedTicks, context);
    }

    private static Bounds bounds(Resource definition, FormulaContext context) {
        double min = definition.min().evaluate(context);
        double max = definition.max().evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max ? new Bounds(min, max) : null;
    }

    private static double clamp(double value, Bounds bounds) {
        return Math.max(bounds.min(), Math.min(bounds.max(), value));
    }

    private record Bounds(double min, double max) {
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
