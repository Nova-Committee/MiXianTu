package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Activates formations and charges their maintenance only through common resource transactions.
 */
public final class FormationService {
    private FormationService() {
    }

    public static ActivateResult activate(Identifier id, Formation definition, ResourceHolderComponent resources, FormulaContext context) {
        return activate(id, definition, resources, context, null);
    }

    public static ActivateResult activate(Identifier id, Formation definition, ResourceHolderComponent resources, FormulaContext context, UUID owner) {
        double radius = definition.radius().evaluate(context);
        if (!Double.isFinite(radius) || radius <= 0.0D) return ActivateResult.rejected(Failure.INVALID_FORMULA, null);
        Result payment = ResourceTransactions.tryConsume(resources, ResourceTransactions.evaluate(definition.activationCosts(), context));
        if (!payment.committed())
            return ActivateResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        return ActivateResult.activated(owner == null ? new FormationInstance(id, radius) : new FormationInstance(id, radius, owner));
    }

    public static MaintainResult maintain(FormationInstance instance, Formation definition, ResourceHolderComponent resources, FormulaContext context) {
        if (!instance.active()) return MaintainResult.inactive();
        Result payment = ResourceTransactions.tryConsume(resources, ResourceTransactions.evaluate(definition.maintenanceCosts(), context));
        if (!payment.committed()) {
            instance.deactivate();
            return MaintainResult.deactivated(payment.failedResource());
        }
        instance.maintained();
        return MaintainResult.success();
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

    public record MaintainResult(boolean maintained, boolean deactivated, Identifier failedResource) {
        static MaintainResult inactive() {
            return new MaintainResult(false, false, null);
        }

        static MaintainResult success() {
            return new MaintainResult(true, false, null);
        }

        static MaintainResult deactivated(Identifier resource) {
            return new MaintainResult(false, true, resource);
        }
    }
}
