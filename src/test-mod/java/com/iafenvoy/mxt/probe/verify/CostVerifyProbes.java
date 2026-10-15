package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;

import java.util.List;
import java.util.Map;

/**
 * The one costs shape, driven through the real payment entry point: what a channel the context does not offer
 * refuses with, and that nothing at all is taken when any single entry cannot be paid.
 */
public final class CostVerifyProbes {
    private CostVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/cost_payment", CostVerifyProbes::costPayment));
    }

    private static void costPayment(ProbeContext context) {
        String failure = verifyCostPayment(context);
        if (failure != null) context.fail(failure);
    }

    // One costs shape for every channel: the same array can name a resource and an aura, both are charged in the
    // value they are measured in, a channel the context does not offer is a refusal rather than a half payment,
    // and nothing at all is taken when any single entry cannot be paid. The account is charged without a payer,
    // which is how a formation pays its upkeep while its owner is offline, and then through the player's own
    // attachment, which is the ordinary entity path.
    private static String verifyCostPayment(ProbeContext context) {
        RegistryAccess registries = context.registries();
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(probe, 100.0D, 0.0D, 10_000.0D, -1L, "probe");
        CostContext costs = CostContext.account(account, null, FormulaContext.of(context.player()), CostOrigin.SCRIPT);

        CostPayment.Result plain = CostPayment.pay(decodeCosts(registries,
                "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 3}]"), costs);
        if (!plain.paid()) return "a plain resource cost was refused: " + plain.failure();
        if (!close(account.get(probe), 97.0D))
            return "a 3-point cost left " + account.get(probe) + " instead of 97";

        // Two entries that reach the same value by different routes add up: the aura is charged as the resource
        // it is measured in, which is the only answer that does not depend on the order they were written in.
        account.set(qi, 10.0D, 0.0D, 100.0D, -1L, "probe");
        CostPayment.Result merged = CostPayment.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:qi\", \"amount\": 1}, {\"type\": \"mxt:aura\", \"aura\": \"mxt_test:qi\", \"amount\": 3}]"),
                costs);
        if (!merged.paid()) return "a resource entry plus the aura it names was refused: " + merged.failure();
        if (!close(account.get(qi), 6.0D))
            return "1 + 3 of the same value left " + account.get(qi) + " instead of 6";

        double before = account.get(probe);
        CostPayment.Result refused = CostPayment.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 1}, {\"id\": \"mxt_test:soul_power\", \"amount\": 9999}]"),
                costs);
        if (refused.paid()) return "an array whose second entry is unpayable was paid anyway";
        if (!close(account.get(probe), before))
            return "a refused payment still took " + (before - account.get(probe)) + " off the first entry";

        CostPayment.Result item = CostPayment.pay(decodeCosts(registries,
                "[{\"type\": \"mxt:item\", \"items\": [\"minecraft:emerald\"], \"amount\": 1}]"), costs);
        if (item.paid() || item.failure() != CostFailure.NO_CHANNEL)
            return "an item cost without a player channel read as " + item.failure();

        CostPayment.Result invalid = CostPayment.pay(decodeCosts(registries,
                "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 0}]"), costs);
        if (invalid.paid() || invalid.failure() != CostFailure.INVALID_AMOUNT)
            return "a zero amount read as " + invalid.failure();

        ResourceHolderAttachment personal = context.player().getData(MxtAttachments.RESOURCE_HOLDER);
        double previous = personal.get(probe);
        personal.set(probe, 10.0D, 0.0D, 10_000.0D, -1L, "probe");
        CostPayment.Result onPlayer = CostPayment.pay(decodeCosts(registries,
                        "[{\"id\": \"mxt_test:trigger_probe\", \"amount\": 2}]"),
                CostContext.of(context.player(), FormulaContext.of(context.player()), CostOrigin.SCRIPT));
        double left = personal.get(probe);
        personal.set(probe, previous, 0.0D, 10_000.0D, -1L, "probe");
        if (!onPlayer.paid()) return "a payer-paid resource cost was refused: " + onPlayer.failure();
        if (!close(left, 8.0D)) return "a payer-paid 2-point cost left " + left + " instead of 8";

        // The two aura-only fields read the old map and the new list, and refuse anything that is not an aura:
        // a resource entry there could never be paid by the pool or the store the field charges.
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<Cost> mapped = SpiritRecipe.AURA_CODEC.parse(ops, JsonParser.parseString("{\"mxt_test:qi\": 2}"))
                .getOrThrow(error -> new IllegalArgumentException("The aura map form no longer decodes: " + error));
        if (mapped.size() != 1 || !(mapped.getFirst() instanceof AuraCost mappedCost)
                || !close(mappedCost.amount().evaluate(FormulaContext.EMPTY), 2.0D))
            return "the aura map form decoded as " + mapped;
        if (SpiritRecipe.AURA_CODEC.parse(ops, JsonParser.parseString("[{\"id\": \"mxt_test:qi\", \"amount\": 1}]")).error().isEmpty())
            return "a non-aura entry loaded into an aura-only cost field";

        Holder<Aura> qiAura = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Map<Holder<Aura>, Double> auraAmounts = Costs.auras(List.of(new AuraCost(qiAura, new Constant(2.0D))), costs);
        if (auraAmounts == null || !close(auraAmounts.getOrDefault(qiAura, 0.0D), 2.0D))
            return "an aura entry evaluated to " + auraAmounts;
        return null;
    }

    private static List<Cost> decodeCosts(RegistryAccess registries, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return Cost.LIST_CODEC.parse(ops, JsonParser.parseString(json))
                .getOrThrow(error -> new IllegalArgumentException("Invalid cost fixture: " + error));
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
