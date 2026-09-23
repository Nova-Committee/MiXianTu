package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Performs all-or-nothing resource accounting for amounts a caller has already planned. Evaluating a costs array
 * is {@code CostTransaction}'s job; this is only the write, and it validates every entry before touching any.
 */
public final class ResourceTransactions {
    private ResourceTransactions() {
    }

    public static Result tryConsume(ResourceHolderAttachment holder, Evaluation evaluation) {
        return tryConsume(null, holder, evaluation);
    }

    // The optional entity enables a resource's use gate; server systems without an entity retain value-only
    // accounting.
    public static Result tryConsume(LivingEntity entity, ResourceHolderAttachment holder, Evaluation evaluation) {
        // The holder is intentionally a value-only attachment, so a cost may never create a negative balance even
        // when a caller has not resolved the optional datapack bounds.
        for (Entry<Identifier, Double> entry : evaluation.amounts.entrySet()) {
            Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, entry.getKey()).orElse(null);
            if (resource == null) return Result.rejected(entry.getKey(), evaluation.amounts);
            // The gate belongs to the aura a value carries; a value without one has no gate to ask, and a
            // server system without an entity has nobody to ask.
            if (entity != null) {
                Holder<Aura> aura = AuraLookup.holder(entity, resource).orElse(null);
                if (aura != null && !ResourceUseService.canUse(entity, aura))
                    return Result.rejected(entry.getKey(), evaluation.amounts);
            }
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

        // Wraps amounts already evaluated by the caller, so a system adjusting a cost does not re-evaluate the
        // providers against a context other than the one they were written for.
        public static Evaluation of(Map<Identifier, Double> amounts) {
            return new Evaluation(amounts);
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
