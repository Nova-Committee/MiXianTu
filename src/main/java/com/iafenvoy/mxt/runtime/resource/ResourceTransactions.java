package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Performs all-or-nothing resource accounting for amounts a caller has already loaded. Reading a costs array is
 * {@code CostPayment}'s job; this is only the write, and it validates every entry before touching any. Amounts are
 * keyed by the resource itself, so nothing here has to resolve an id: the one place a data pack's id form is read is
 * {@link Evaluation#of(Provider, Map)}.
 */
public final class ResourceTransactions {
    private ResourceTransactions() {
    }

    public static Result tryConsume(ResourceHolderAttachment holder, Evaluation evaluation) {
        return tryConsume(null, holder, evaluation);
    }

    // The optional entity enables a resource's use gate; server systems without an entity retain value-only
    // accounting.
    public static Result tryConsume(@Nullable LivingEntity entity, ResourceHolderAttachment holder,
                                    Evaluation evaluation) {
        // The holder is intentionally a value-only attachment, so a cost may never create a negative balance even
        // when a caller has not resolved the optional datapack bounds.
        for (Entry<Holder<Resource>, Double> entry : evaluation.amounts.entrySet()) {
            Holder<Resource> resource = entry.getKey();
            // The gate belongs to the aura a value carries; a value without one has no gate to ask, and a
            // server system without an entity has nobody to ask.
            if (entity != null) {
                Holder<Aura> aura = AuraLookup.holder(entity, resource).orElse(null);
                if (aura != null && !ResourceUseService.canUse(entity, aura))
                    return Result.rejected(resource, evaluation.amounts);
            }
            double amount = entry.getValue();
            double current = holder.get(resource);
            if (!Double.isFinite(amount) || amount <= 0.0D || !Double.isFinite(current)
                    || current < amount || current - amount < 0.0D) {
                return Result.rejected(resource, evaluation.amounts);
            }
        }
        evaluation.amounts.forEach((resource, amount) -> {
            Audit previous = holder.audit(resource);
            holder.set(resource, holder.get(resource) - amount, previous.minSnapshot(), previous.maxSnapshot(), previous.lastChangedTick(), "cost");
        });
        return Result.committed(evaluation.amounts);
    }

    public record Evaluation(Map<Holder<Resource>, Double> amounts) {
        public Evaluation {
            amounts = new LinkedHashMap<>(amounts);
        }

        // Wraps amounts already resolved by the caller, so a system adjusting a cost does not re-evaluate the
        // providers against a context other than the one they were written for.
        public static Evaluation of(Map<Holder<Resource>, Double> amounts) {
            return new Evaluation(amounts);
        }

        // The id-keyed shape a definition writes, resolved once here: an id the access does not provide is dropped,
        // which is the same answer an unresolved id gets everywhere else.
        public static Evaluation of(Provider access, Map<Identifier, Double> amounts) {
            Map<Holder<Resource>, Double> resolved = new LinkedHashMap<>();
            amounts.forEach((id, amount) -> MxtDatapackRegistries.holder(access, MxtResourceKeys.RESOURCE, id)
                    .ifPresent(resource -> resolved.merge(resource, amount, Double::sum)));
            return new Evaluation(resolved);
        }
    }

    // What left a store, reported by id: this is the table a caller names back to a player.
    public record Result(boolean committed, Identifier failedResource, Map<Identifier, Double> amounts) {
        public Result {
            amounts = new LinkedHashMap<>(amounts);
        }

        private static Result committed(Map<Holder<Resource>, Double> amounts) {
            Map<Identifier, Double> reported = new LinkedHashMap<>();
            amounts.forEach((resource, amount) -> reported.merge(HolderHelper.id(resource), amount, Double::sum));
            return new Result(true, null, reported);
        }

        private static Result rejected(Holder<Resource> resource, Map<Holder<Resource>, Double> amounts) {
            Map<Identifier, Double> reported = new LinkedHashMap<>();
            amounts.forEach((entry, amount) -> reported.merge(HolderHelper.id(entry), amount, Double::sum));
            return new Result(false, HolderHelper.id(resource), reported);
        }
    }
}
