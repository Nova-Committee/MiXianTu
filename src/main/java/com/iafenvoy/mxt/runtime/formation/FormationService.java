package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.Formation.Storage;
import com.iafenvoy.mxt.runtime.formation.FormationService.MaintainRule.PaymentPlan;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Activates formations and charges their maintenance only through common resource transactions.
 */
public final class FormationService {
    private FormationService() {
    }

    public static ActivateResult activate(Identifier id, Formation definition, ResourceHolderAttachment resources, FormulaContext context) {
        return activate(id, definition, resources, context, null);
    }

    public static ActivateResult activate(Identifier id, Formation definition, ResourceHolderAttachment resources, FormulaContext context, UUID owner) {
        double radius = definition.radius().evaluate(context);
        if (!Double.isFinite(radius) || radius <= 0.0D) return ActivateResult.rejected(Failure.INVALID_FORMULA, null);
        Result payment = ResourceTransactions.tryConsume(resources, ResourceTransactions.evaluate(definition.activationCosts(), context));
        if (!payment.committed())
            return ActivateResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        return ActivateResult.activated(owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner));
    }

    /**
     * Charges one period of upkeep. Only ever pays: a failure leaves the instance untouched rather than marking
     * it inactive, because tearing a formation down belongs to {@link FormationWorldService#deactivate}.
     */
    public static MaintainResult maintain(FormationInstance instance, Formation definition, ResourceHolderAttachment resources, FormulaContext context) {
        return maintain(instance, definition, resources, context, Map.of());
    }

    /**
     * Charges one period of upkeep: the formation's own blocks pay first, then its stock. A failure leaves the
     * instance untouched rather than marking it inactive, because tearing a formation down belongs to
     * {@link FormationWorldService#deactivate}.
     */
    public static MaintainResult maintain(FormationInstance instance, Formation definition, ResourceHolderAttachment resources,
                                          FormulaContext context, Map<Holder<Aura>, Double> supplied) {
        Map<Holder<Aura>, Double> capacity = definition.storage()
                .map(storage -> MaintainRule.capacities(storage, context))
                .orElse(Map.of());
        PaymentPlan plan = MaintainRule.plan(definition, context, supplied, instance.stored(), capacity);
        Result payment = ResourceTransactions.tryConsume(resources, Evaluation.of(plan.fromOwner()));
        if (!payment.committed()) return MaintainResult.unpaid(payment.failedResource());
        plan.applyTo(instance);
        instance.maintained();
        return MaintainResult.paid();
    }

    /**
     * How much of a period's upkeep is left for the payer once the formation's own blocks have supplied what
     * they supply, and once its stock has covered what that left.
     */
    public static final class MaintainRule {
        private MaintainRule() {
        }

        /**
         * The capacity of each aura a formation's storage declaration names, evaluated once per period.
         * A capacity that comes out non-finite, zero or negative is dropped rather than clamped: it means
         * this aura is not stored.
         */
        public static Map<Holder<Aura>, Double> capacities(Storage storage, FormulaContext context) {
            Map<Holder<Aura>, Double> capacities = new LinkedHashMap<>();
            storage.capacity().forEach((aura, provider) -> {
                if (aura == null) return;
                double capacity = provider.evaluate(context);
                if (Double.isFinite(capacity) && capacity > 0.0D) capacities.put(aura, capacity);
            });
            return capacities;
        }

        /**
         * @return cost per resource id, with the supplied aura subtracted, and nothing left for a cost that
         * is fully covered
         */
        public static Map<Identifier, Double> remaining(Formation definition, FormulaContext context,
                                                        Map<Holder<Aura>, Double> supplied) {
            // The plain reading of "what does the payer owe": the cost minus the ground's contribution, run
            // through the same code path upkeep uses, with an empty bank.
            return plan(definition, context, supplied, Map.of(), Map.of()).fromOwner();
        }

        /**
         * The three-way split of one period's bill: what the stock pays, what the stock gains, and what is left
         * to the payer. A pure function of its arguments, because it is the only part of upkeep where a wrong
         * answer is invisible in play.
         * <p>
         * The bill is written per value - what a pool is charged in - while the supply, the stock and its
         * capacity are per aura. The two are reconciled by the only thing that connects them, the value an aura
         * names ({@code Aura#resource()}), so nothing here reads a registry.
         *
         * @param supplied what the formation's own blocks and (optionally) its ground supply this period
         * @param stored   what the stock holds right now
         * @param capacity what the stock may hold, per aura; an aura it does not name is not stored
         */
        public static PaymentPlan plan(Formation definition, FormulaContext context,
                                       Map<Holder<Aura>, Double> supplied,
                                       Map<Holder<Aura>, Double> stored, Map<Holder<Aura>, Double> capacity) {
            Map<Identifier, Double> cost = ResourceTransactions.evaluate(definition.maintenanceCosts(), context).amounts();
            Map<Identifier, Holder<Aura>> byValue = new LinkedHashMap<>();
            for (Holder<Aura> aura : supplied.keySet())
                byValue.putIfAbsent(HolderHelper.id(aura.value().resource()), aura);
            for (Holder<Aura> aura : stored.keySet())
                byValue.putIfAbsent(HolderHelper.id(aura.value().resource()), aura);
            for (Holder<Aura> aura : capacity.keySet())
                byValue.putIfAbsent(HolderHelper.id(aura.value().resource()), aura);
            Map<Holder<Aura>, Double> fromStock = new LinkedHashMap<>();
            Map<Identifier, Double> fromPayer = new LinkedHashMap<>();
            // What this period's own supply was actually spent on, so the rest of it can be banked. An aura no
            // cost names has spent nothing, so the wrong kind of aura still fills the bank.
            Map<Holder<Aura>, Double> spentSupply = new LinkedHashMap<>();
            cost.forEach((id, amount) -> {
                Holder<Aura> aura = byValue.get(id);
                // An aura the bill names but nothing supplies absorbs nothing and banks nothing: it keeps the
                // full charge.
                double here = aura == null ? 0.0D : Math.max(0.0D, supplied.getOrDefault(aura, 0.0D));
                double used = Math.min(amount, here);
                if (used > 0.0D) spentSupply.merge(aura, used, Double::sum);
                double left = amount - used;
                double banked = aura == null ? 0.0D : Math.min(left, Math.max(0.0D, stored.getOrDefault(aura, 0.0D)));
                if (banked > 0.0D) fromStock.merge(aura, banked, Double::sum);
                double owed = left - banked;
                if (owed > 0.0D) fromPayer.merge(id, owed, Double::sum);
            });
            Map<Holder<Aura>, Double> deposit = new LinkedHashMap<>();
            supplied.forEach((aura, amount) -> {
                if (aura == null || amount == null || !Double.isFinite(amount)) return;
                double surplus = amount - spentSupply.getOrDefault(aura, 0.0D);
                if (surplus <= 0.0D) return;
                double room = capacity.getOrDefault(aura, 0.0D) - stored.getOrDefault(aura, 0.0D);
                double banked = Math.min(surplus, Math.max(0.0D, room));
                if (banked > 0.0D) deposit.merge(aura, banked, Double::sum);
            });
            return new PaymentPlan(fromStock, fromPayer, deposit);
        }

        /**
         * One period's decided split, before anything is written: a plan that fails to be paid for must
         * leave everything as it was, including the stock.
         */
        public record PaymentPlan(Map<Holder<Aura>, Double> fromStock, Map<Identifier, Double> fromOwner,
                                  Map<Holder<Aura>, Double> deposit) {
            public PaymentPlan {
                fromStock = new LinkedHashMap<>(fromStock);
                fromOwner = new LinkedHashMap<>(fromOwner);
                deposit = new LinkedHashMap<>(deposit);
            }

            /**
             * Applies the split to a live instance: spend the old stock, then bank this period's surplus.
             */
            public void applyTo(FormationInstance instance) {
                instance.withdraw(this.fromStock);
                instance.deposit(this.deposit);
            }
        }
    }

    public enum Failure {DISABLED, INVALID_FORMULA, INSUFFICIENT_RESOURCE}

    public record ActivateResult(FormationInstance instance, Failure failure, Identifier failedResource) {
        static ActivateResult activated(FormationInstance instance) {
            return new ActivateResult(instance, null, null);
        }

        static ActivateResult rejected(Failure failure, Identifier resource) {
            return new ActivateResult(null, failure, resource);
        }

        public boolean active() {
            return this.instance != null;
        }
    }

    public record MaintainResult(boolean maintained, Identifier failedResource) {
        static MaintainResult paid() {
            return new MaintainResult(true, null);
        }

        static MaintainResult unpaid(Identifier resource) {
            return new MaintainResult(false, resource);
        }
    }
}
