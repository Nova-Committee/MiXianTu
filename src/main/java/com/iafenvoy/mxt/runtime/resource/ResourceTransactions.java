package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Evaluates a costs array once, then performs all-or-nothing resource accounting.
 */
public final class ResourceTransactions {
    private ResourceTransactions() {
    }

    public static Evaluation evaluate(List<ResourceCost> costs, FormulaContext context) {
        LinkedHashMap<Identifier, Double> amounts = new LinkedHashMap<>();
        for (ResourceCost cost : costs) {
            double amount = cost.evaluate(context);
            if (!Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("Resource cost must be finite and positive for " + cost.id());
            }
            if (amounts.put(cost.id(), amount) != null) {
                throw new IllegalArgumentException("Duplicate resource cost " + cost.id());
            }
        }
        return new Evaluation(amounts);
    }

    /**
     * Evaluates every cost with the formula context of the resource it spends.
     * This lets a skill cost refer to that resource's realm rank and absorbed aura.
     */
    public static Evaluation evaluate(LivingEntity payer, List<ResourceCost> costs, FormulaContext context) {
        LinkedHashMap<Identifier, Double> amounts = new LinkedHashMap<>();
        for (ResourceCost cost : costs) {
            Identifier id = cost.id();
            double amount = cost.evaluate(ResourceService.formulaContext(payer, id, cost.resource().value(), context));
            if (!Double.isFinite(amount) || amount <= 0.0D) {
                throw new IllegalArgumentException("Resource cost must be finite and positive for " + id);
            }
            if (amounts.put(id, amount) != null) {
                throw new IllegalArgumentException("Duplicate resource cost " + id);
            }
        }
        return new Evaluation(amounts);
    }

    public static Result tryConsume(ResourceHolderAttachment holder, Evaluation evaluation) {
        return tryConsume(null, holder, evaluation);
    }

    /**
     * Performs an entity-driven transaction. The optional entity enables a resource's
     * use gate; server systems without an entity retain value-only accounting.
     */
    public static Result tryConsume(LivingEntity entity, ResourceHolderAttachment holder, Evaluation evaluation) {
        // The holder is intentionally a value-only attachment.  A cost may never create a
        // negative balance, even when a caller has not resolved the optional datapack bounds.
        for (Entry<Identifier, Double> entry : evaluation.amounts.entrySet()) {
            Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, entry.getKey()).orElse(null);
            if (resource == null) return Result.rejected(entry.getKey(), evaluation.amounts);
            if (entity != null && !ResourceUseService.canUse(entity, resource))
                return Result.rejected(entry.getKey(), evaluation.amounts);
            double amount = entry.getValue();
            double current = holder.get(resource);
            if (!Double.isFinite(amount) || amount <= 0.0D || !Double.isFinite(current)
                    || current < amount || current - amount < 0.0D) {
                return Result.rejected(entry.getKey(), evaluation.amounts);
            }
        }
        evaluation.amounts.forEach((id, amount) -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).ifPresent(resource -> {
            Audit previous = holder.audit(resource);
            holder.set(resource, holder.get(resource) - amount, previous.minSnapshot(), previous.maxSnapshot(), previous.lastChangedTick(), "cost");
        }));
        return Result.committed(evaluation.amounts);
    }

    public record Evaluation(Map<Identifier, Double> amounts) {
        public Evaluation {
            amounts = new LinkedHashMap<>(amounts);
        }
    }

    public record Result(boolean committed, Identifier failedResource, Map<Identifier, Double> amounts) {
        public Result {
            amounts = new LinkedHashMap<>(amounts);
        }

        private static Result committed(Map<Identifier, Double> amounts) {
            return new Result(true, null, amounts);
        }

        private static Result rejected(Identifier resource, Map<Identifier, Double> amounts) {
            return new Result(false, resource, amounts);
        }
    }
}
