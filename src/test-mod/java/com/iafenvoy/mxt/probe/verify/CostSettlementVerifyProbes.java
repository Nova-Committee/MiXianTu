package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.data.cost.*;
import com.iafenvoy.mxt.data.cost.builtin.JsCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CostSettlementVerifyProbes {
    private CostSettlementVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/cost_duplicate_order", CostSettlementVerifyProbes::duplicateOrder),
                Probe.of("verify/cost_entry_order", CostSettlementVerifyProbes::entryOrder),
                Probe.of("verify/cost_real_failure", CostSettlementVerifyProbes::realFailure),
                Probe.of("verify/cost_real_exception", CostSettlementVerifyProbes::realException)
        );
    }

    private static void duplicateOrder(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 5.0D);
        CostPayment bill = CostPayment.of(costs(context, account));
        bill.price(List.of(new ResourceCost(resource, new Constant(4.0D)),
                new ResourceCost(resource, new Constant(3.0D))));
        context.expect("each duplicate can simulate against the current balance", bill.test().isEmpty(), true);
        context.expect("simulation leaves the balance unchanged", account.get(resource), 5.0D);
        CostPayment.PaymentResult paid = bill.pay();
        context.expect("a later real refusal does not cancel the bill", paid.isSuccess(), true);
        context.expect("the first entry is charged in list order", account.get(resource), 1.0D);
        context.expect("only the actual first charge is reported", paid.resources().get(resource.unwrapKey().orElseThrow().identifier()), 4.0D);
        context.expect("a repeated pay returns the same terminal result", bill.pay() == paid, true);
        context.expect("a repeated pay does not charge again", account.get(resource), 1.0D);
    }

    private static void entryOrder(ProbeContext context) {
        List<String> calls = new ArrayList<>();
        CostContext costs = CostContext.of(context.player(), CostOrigin.SCRIPT);
        CostPayment.PaymentResult paid = CostPayment.pay(List.of(
                observed(new CostTarget.Script("probe:first", 1), "first", calls),
                observed(new CostTarget.Account(new ResourceHolderAttachment(), resource(context)), "account", calls),
                observed(new CostTarget.Script("probe:last", 2), "last", calls)
        ), costs);
        context.expect("the ordered bill succeeds", paid.isSuccess(), true);
        context.expect("scripts keep their place in both passes", calls,
                List.of("check:first", "check:account", "check:last", "pay:first", "pay:account", "pay:last"));
    }

    private static void realFailure(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        CostTarget target = new CostTarget.Account(account, resource);
        FixtureCost shortFee = new FixtureCost(target, 5.0D, simulate -> {
            if (simulate) return ResultWithData.success(List.of());
            account.set(resource, account.get(resource) - 3.0D);
            return ResultWithData.failure(List.of(Debit.of(target, 3.0D)), CostFailure.INSUFFICIENT_AURA);
        });
        CostPayment bill = CostPayment.of(costs(context, account));
        bill.price(List.of(shortFee, new ResourceCost(resource, new Constant(1.0D))));
        CostPayment.PaymentResult paid = bill.pay();
        context.expect("a real refusal allows the caller to proceed", paid.isSuccess(), true);
        context.expect("later fees still execute after a partial refusal", account.get(resource), 6.0D);
        context.expect("partial and later receipts are both retained", paid.debits().size(), 2);
        context.expect("only the known amount is reported", paid.debits().stream().mapToDouble(Debit::amount).sum(), 4.0D);
        context.expect("a refused fee is not retried", bill.pay() == paid, true);
        context.expect("the refused bill cannot charge twice", account.get(resource), 6.0D);
    }

    private static void realException(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        int[] attempts = {0};
        String script = "mxt_test:cost_exception_probe";
        MxtJsCostCallbacks.register(script, (player, params, formula) -> true, (player, params, formula) -> {
            attempts[0]++;
            throw new IllegalStateException("intentional cost probe exception");
        });
        CostPayment bill = CostPayment.of(costs(context, account));
        bill.price(List.of(new ResourceCost(resource, new Constant(2.0D)), new JsCost(script, new JsonObject()),
                new ResourceCost(resource, new Constant(1.0D))));
        CostPayment.PaymentResult paid = bill.pay();
        context.expect("a real exception does not cancel the bill", paid.isSuccess(), true);
        context.expect("fees before and after the exception both execute", account.get(resource), 7.0D);
        context.expect("known receipts survive an exception", paid.debits().stream().mapToDouble(Debit::amount).sum(), 3.0D);
        context.expect("the exceptional bill has a terminal result", bill.pay() == paid, true);
        context.expect("the throwing fee is attempted once", attempts[0], 1);
        context.expect("repeating pay cannot write again", account.get(resource), 7.0D);
    }

    private static FixtureCost observed(CostTarget target, String name, List<String> calls) {
        return new FixtureCost(target, 1.0D, simulate -> {
            calls.add((simulate ? "check:" : "pay:") + name);
            return ResultWithData.success(List.of());
        });
    }

    private static Holder<Resource> resource(ProbeContext context) {
        return context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
    }

    private static CostContext costs(ProbeContext context, ResourceHolderAttachment account) {
        return CostContext.account(account, context.player(), FormulaContext.of(context.player()), CostOrigin.SCRIPT);
    }

    private record FixtureCost(CostTarget target, double amount,
                               Function<Boolean, ResultWithData<List<Debit>>> action) implements Cost {
        @Override
        public ResultWithData<Transaction> prepare(CostContext context) {
            return ResultWithData.success(new FixtureTransaction(this.target, this.amount, this.action));
        }

        @Override
        public MapCodec<? extends Cost> codec() {
            throw new UnsupportedOperationException("probe-only cost");
        }
    }

    private static final class FixtureTransaction extends CostTransaction {
        private final Function<Boolean, ResultWithData<List<Debit>>> action;

        private FixtureTransaction(CostTarget target, double amount, Function<Boolean, ResultWithData<List<Debit>>> action) {
            super(target, amount);
            this.action = action;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            return this.action.apply(simulate);
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new FixtureTransaction(this.target(), amount, this.action);
        }
    }
}
