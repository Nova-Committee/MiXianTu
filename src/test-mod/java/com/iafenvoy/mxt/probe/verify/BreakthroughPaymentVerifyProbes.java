package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.runtime.resource.ResourceSeeding;
import com.iafenvoy.saturn.runtime.resource.ResourceService;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.MessageFailure;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The entry breakthrough's price, paid the way a player pays it: a body that never touched the resource, the pack's
 * default, and the account the cost names. A body whose default never reached its account cannot climb the first
 * rung at all, so the seeded balance is part of what this checks.
 */
public final class BreakthroughPaymentVerifyProbes {
    private BreakthroughPaymentVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/breakthrough_payment", BreakthroughPaymentVerifyProbes::breakthroughPayment));
    }

    private static void breakthroughPayment(ProbeContext context) {
        LivingEntity body = context.player();
        CultivationAttachment spirit = body.getData(MxtAttachments.CULTIVATION);
        ResourceHolderAttachment resources = body.getData(SaturnAttachments.RESOURCE_HOLDER);
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Resource> payment = context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER);
        Holder<RealmStage> entry = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING);
        FormulaContext formula = FormulaContext.of(body);
        double threshold = entry.value().breakthroughExp().evaluate(formula);

        // Logging in is what seeds it, so this is read before anything in the probe touches the account. The cap has
        // to be positive for a body that stands in no chain at all, or the payment can never be held.
        context.expect("a fresh body starts on the pack default", resources.get(payment), 100.0D);
        context.expect("a mortal's cap leaves room for the payment", ResourceService.resolveBounds(payment.value(),
                        ResourceService.formulaContext(body, payment, formula))
                .map(value -> value.max() >= 100.0D).orElse(false), true);

        resources.set(payment, 0.0D);
        spirit.setCultivationProgress(aura, threshold);
        Result<Void> refused = CultivationService.attempt(body, spirit, resources, aura, formula, () -> true);
        Failure refusal = refused.getFailure().orElseThrow();
        context.expect("an empty account is refused",
                MessageFailure.reason(refusal), Failures.INSUFFICIENT_RESOURCE);
        context.expect("a refused breakthrough stays outside the realm", spirit.realmStage(aura) == null, true);

        ProbeKit.ensureResource(body, resources, payment, 100.0D);
        spirit.setCultivationProgress(aura, threshold);
        Result<Void> paid = CultivationService.attempt(body, spirit, resources, aura, formula, () -> true);
        context.check("a body holding the payment breaks through", paid instanceof Result.SuccessResult<?>);
        context.expect("the entry cost comes out of the account", resources.get(payment), 75.0D);
        context.expect("the body lands in the entry realm",
                spirit.realmStage(aura) == null ? "none" : HolderHelper.id(spirit.realmStage(aura)).toString(),
                HolderHelper.id(entry).toString());

        // A save written while the cap was zero could never hold anything: the login pass seeds it again.
        resources.set(payment, 0.0D, 0.0D, 0.0D, -1L, "initialize");
        ResourceSeeding.seed(body);
        context.expect("a value written under a zero cap is seeded again", resources.get(payment), 100.0D);
        context.expect("and its stored cap follows the definition", resources.audit(payment).maxSnapshot(), 100.0D);

        // A balance that had room is never refilled; it is only clamped into the cap the pack defines now.
        resources.set(payment, 10.0D, 0.0D, 100.0D, -1L, "cost");
        ResourceSeeding.seed(body);
        context.expect("a spent value stays where it was", resources.get(payment), 10.0D);

        // The next stage's own gate has to be satisfiable from the stage before it: a stage that asks for a realm it
        // is itself locked behind can never be entered.
        ProbeKit.ensureResource(body, resources, payment, 100.0D);
        Holder<RealmStage> foundation = context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.FOUNDATION);
        spirit.setCultivationProgress(aura, foundation.value().breakthroughExp().evaluate(formula));
        CultivationService.BreakthroughStatus next = CultivationService.breakthroughStatusForChain(body, aura, formula);
        context.check("the next stage reads as reached", next.reached());
        context.check("and its conditions are met from the stage before it", next.conditionsMet());
    }
}
