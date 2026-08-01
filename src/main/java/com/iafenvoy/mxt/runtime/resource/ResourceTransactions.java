package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData.Audit;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;

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
        return new Evaluation(Map.copyOf(amounts));
    }

    public static Result tryConsume(ResourceHolderData holder, Evaluation evaluation) {
        // The holder is intentionally a value-only attachment.  A cost may never create a
        // negative balance, even when a caller has not resolved the optional datapack bounds.
        for (Entry<Identifier, Double> entry : evaluation.amounts.entrySet()) {
            double amount = entry.getValue();
            double current = holder.get(entry.getKey());
            if (!Double.isFinite(amount) || amount <= 0.0D || !Double.isFinite(current)
                    || current < amount || current - amount < 0.0D) {
                return Result.rejected(entry.getKey(), evaluation.amounts);
            }
        }
        evaluation.amounts.forEach((id, amount) -> {
            Audit previous = holder.audit(id);
            holder.set(id, holder.get(id) - amount, previous.maxSnapshot(), previous.lastChangedTick(), "cost");
        });
        return Result.committed(evaluation.amounts);
    }

    public record Evaluation(Map<Identifier, Double> amounts) {
        public Evaluation {
            amounts = Map.copyOf(amounts);
        }
    }

    public record Result(boolean committed, Identifier failedResource, Map<Identifier, Double> amounts) {
        private static Result committed(Map<Identifier, Double> amounts) {
            return new Result(true, null, amounts);
        }

        private static Result rejected(Identifier resource, Map<Identifier, Double> amounts) {
            return new Result(false, resource, amounts);
        }
    }
}
