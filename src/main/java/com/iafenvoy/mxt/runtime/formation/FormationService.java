package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.FormationEvent.UpkeepFailed;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
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
     * Charges one period of upkeep.
     *
     * <p>Only ever pays: a failure leaves the instance untouched rather than marking it inactive, because
     * the decision to tear a formation down belongs to {@link FormationWorldService#deactivate} — and
     * because a listener may cancel {@link UpkeepFailed} to let the
     * formation stand through a period it could not pay for.</p>
     */
    public static MaintainResult maintain(FormationInstance instance, Formation definition, ResourceHolderAttachment resources, FormulaContext context) {
        return maintain(instance, definition, resources, context, Map.of());
    }

    /**
     * Charges one period of upkeep, with the aura the formation's own blocks supply paying first.
     *
     * <p>An emitter inside a formation does not feed the environment; it feeds the formation, which spends
     * it on its own upkeep. {@code supplied} is that contribution, already totalled per resource, and it
     * reduces the charge — a block supplying ten units against a cost of twelve leaves two to pay. A
     * contribution past the cost is not banked: the block emitted it this period and the surplus is simply
     * spent, which is what keeps this from being a second, hidden resource store.</p>
     */
    public static MaintainResult maintain(FormationInstance instance, Formation definition, ResourceHolderAttachment resources,
                                          FormulaContext context, Map<Holder<Resource>, Double> supplied) {
        Result payment = ResourceTransactions.tryConsume(resources, ResourceTransactions.Evaluation.of(
                MaintainRule.remaining(definition, context, supplied)));
        if (!payment.committed()) return MaintainResult.unpaid(payment.failedResource());
        instance.maintained();
        return MaintainResult.paid();
    }

    /**
     * How much of a period's upkeep is left for the payer once the formation's own blocks have supplied
     * what they supply.
     *
     * <p>Split out from the payment because it is the whole of the rule and the payment is one call: the
     * rule is assertable on its own, without standing up a formation in a level to observe a balance.</p>
     */
    public static final class MaintainRule {
        private MaintainRule() {
        }

        /**
         * @return cost per resource id, with the supplied aura subtracted, and nothing left for a cost that
         * is fully covered
         */
        public static Map<Identifier, Double> remaining(Formation definition, FormulaContext context,
                                                        Map<Holder<Resource>, Double> supplied) {
            Map<Identifier, Double> charged = new LinkedHashMap<>();
            ResourceTransactions.evaluate(definition.maintenanceCosts(), context).amounts().forEach((id, amount) -> {
                Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).orElse(null);
                // Absorbed aura only offsets the resource it is: a formation full of fire aura does not pay a
                // spirit-power bill. An unresolvable resource absorbs nothing and keeps the full charge.
                double offset = resource == null ? 0.0D : supplied.getOrDefault(resource, 0.0D);
                charged.put(id, Math.max(0.0D, amount - offset));
            });
            // A cost fully covered by the formation's own aura never touches the payer, so it cannot fail
            // for being short of a resource the payer does not hold at all.
            charged.entrySet().removeIf(entry -> entry.getValue() <= 0.0D);
            return charged;
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
