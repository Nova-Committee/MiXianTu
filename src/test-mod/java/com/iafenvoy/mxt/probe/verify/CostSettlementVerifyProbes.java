package com.iafenvoy.mxt.probe.verify;
import com.iafenvoy.mxt.runtime.resulting.failures.AuraFailure;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.CostPayment;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.data.cost.CostTransaction;
import com.iafenvoy.saturn.data.cost.Debit;

import com.google.gson.JsonObject;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.compat.kubejs.callback.SaturnJsCostCallbacks;
import com.iafenvoy.saturn.data.cost.builtin.JsCost;
import com.iafenvoy.saturn.data.cost.builtin.ResourceCost;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.data.cost.context.CostOrigins;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.runtime.resulting.failures.CostFailure;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class CostSettlementVerifyProbes {
    private CostSettlementVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/cost_duplicate_order", CostSettlementVerifyProbes::duplicateOrder),
                Probe.of("verify/cost_entry_order", CostSettlementVerifyProbes::entryOrder),
                Probe.of("verify/cost_real_failure", CostSettlementVerifyProbes::realFailure),
                Probe.of("verify/cost_real_exception", CostSettlementVerifyProbes::realException),
                Probe.of("verify/cost_transaction_guard", CostSettlementVerifyProbes::transactionGuard),
                Probe.of("verify/cost_simulate_after_pay", CostSettlementVerifyProbes::simulateAfterPay)
        );
    }

    // The guard that keeps a store from being written twice at once lives in the transaction, not in the bill, so a
    // script that executes a transaction directly is guarded too. It names the store and, for a script, the payer: the
    // same script charged to two payers is two scopes and must not be refused.
    private static void transactionGuard(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        CostTarget shared = new CostTarget.Account(account, resource);
        CostContext costs = costs(context, account);
        // An inner transaction on the same store, run from inside the outer one's own pass.
        boolean[] innerRefused = {false};
        FixtureCost outer = new FixtureCost(shared, 1.0D, simulate -> {
            innerRefused[0] = child(shared, 1.0D).execute(simulate).getFailure().orElse(null) == CostFailure.REENTRANT;
            return Result.success(List.of());
        });
        Result<List<Debit>> executed = outer.prepare(costs).getData().orElseThrow().execute(true);
        context.check("a nested pass over the store being written is refused", innerRefused[0]);
        context.check("the outer pass itself still runs", (executed instanceof Result.SuccessResult<?>));
        // A different store is independent: that one runs inside the same pass.
        boolean[] otherRan = {false};
        Holder<Resource> second = context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.id("true_essence"));
        FixtureCost independent = new FixtureCost(new CostTarget.Account(new ResourceHolderAttachment(), second), 1.0D, simulate -> {
            otherRan[0] = child(new CostTarget.Account(new ResourceHolderAttachment(), second), 1.0D).execute(simulate).getFailure().isEmpty();
            return Result.success(List.of());
        });
        independent.prepare(costs).getData().orElseThrow().execute(true);
        context.check("an unrelated store is not blocked by the pass in progress", otherRan[0]);
        // Two payers running one script are two scopes: the second is not mistaken for the first re-entering itself.
        CostTarget.Script firstPayer = new CostTarget.Script("probe:shared", 1, context.player().getUUID());
        CostTarget.Script otherPayer = new CostTarget.Script("probe:shared", 2, UUID.randomUUID());
        boolean[] otherPayerRan = {false};
        FixtureCost byFirst = new FixtureCost(firstPayer, 1.0D, simulate -> {
            otherPayerRan[0] = child(otherPayer, 1.0D).execute(simulate).getFailure().isEmpty();
            return Result.success(List.of());
        });
        byFirst.prepare(costs).getData().orElseThrow().execute(true);
        context.check("the same script charged to another payer is a separate scope", otherPayerRan[0]);
        // The same script and the same payer is one scope, however many entries name it.
        CostTarget.Script same = new CostTarget.Script("probe:shared", 3, context.player().getUUID());
        boolean[] samePayerRefused = {false};
        FixtureCost bySame = new FixtureCost(same, 1.0D, simulate -> {
            samePayerRefused[0] = child(new CostTarget.Script("probe:shared", 4, context.player().getUUID()), 1.0D)
                    .execute(simulate).getFailure().orElse(null) == CostFailure.REENTRANT;
            return Result.success(List.of());
        });
        bySame.prepare(costs).getData().orElseThrow().execute(true);
        context.check("the same script for the same payer is one scope", samePayerRefused[0]);
    }

    // A settled transaction is still settled, but a simulation of it never reports real charges: only a real pass
    // produces debits, whichever order the two were asked in.
    private static void simulateAfterPay(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        CostTarget target = new CostTarget.Account(account, resource);
        FixtureTransaction charged = child(target, 1.0D);
        Result<List<Debit>> real = charged.execute(false);
        context.expect("a real pass reports its own charge", real.getData().orElse(List.of()).size(), 1);
        Result<List<Debit>> simulated = charged.execute(true);
        context.check("a simulation of a settled transaction still succeeded", (simulated instanceof Result.SuccessResult<?>));
        context.expect("a simulation of a settled transaction reports no real charge", simulated.getData().orElse(List.of()).size(), 0);
        context.expect("the terminal answer keeps its charge", charged.execute(false).getData().orElse(List.of()).size(), 1);
    }

    private static FixtureTransaction child(CostTarget target, double amount) {
        return new FixtureTransaction(target, amount, simulate -> Result.success(
                simulate ? List.of() : List.of(Debit.of(target, amount))));
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
        Result<List<Debit>> paid = bill.pay();
        context.expect("a later real refusal does not cancel the bill", (paid instanceof Result.SuccessResult<?>), true);
        context.expect("the first entry is charged in list order", account.get(resource), 1.0D);
        context.expect("only the actual first charge is reported", CostPayment.resources(paid.getData().orElse(List.of())).get(resource.unwrapKey().orElseThrow().identifier()), 4.0D);
        context.expect("a repeated pay returns the same terminal result", bill.pay() == paid, true);
        context.expect("a repeated pay does not charge again", account.get(resource), 1.0D);
    }

    private static void entryOrder(ProbeContext context) {
        List<String> calls = new ArrayList<>();
        CostContext costs = CostContext.of(context.player(), CostOrigins.SCRIPT);
        Result<List<Debit>> paid = CostPayment.pay(List.of(
                observed(new CostTarget.Script("probe:first", 1, context.player().getUUID()), "first", calls),
                observed(new CostTarget.Account(new ResourceHolderAttachment(), resource(context)), "account", calls),
                observed(new CostTarget.Script("probe:last", 2, context.player().getUUID()), "last", calls)
        ), costs);
        context.expect("the ordered bill succeeds", (paid instanceof Result.SuccessResult<?>), true);
        context.expect("scripts keep their place in both passes", calls,
                List.of("check:first", "check:account", "check:last", "pay:first", "pay:account", "pay:last"));
    }

    private static void realFailure(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        CostTarget target = new CostTarget.Account(account, resource);
        FixtureCost shortFee = new FixtureCost(target, 5.0D, simulate -> {
            if (simulate) return Result.success(List.of());
            account.set(resource, account.get(resource) - 3.0D);
            return Result.failure(AuraFailure.INSUFFICIENT_AURA);
        });
        CostPayment bill = CostPayment.of(costs(context, account));
        bill.price(List.of(shortFee, new ResourceCost(resource, new Constant(1.0D))));
        Result<List<Debit>> paid = bill.pay();
        context.expect("a real refusal allows the caller to proceed", (paid instanceof Result.SuccessResult<?>), true);
        context.expect("later fees still execute after a partial refusal", account.get(resource), 6.0D);
        // A failure no longer carries the debit its store already wrote, so only the later, successful fee is reported.
        context.expect("only later receipts are retained", paid.getData().orElse(List.of()).size(), 1);
        context.expect("only the successful amount is reported", paid.getData().orElse(List.of()).stream().mapToDouble(Debit::amount).sum(), 1.0D);
        context.expect("a refused fee is not retried", bill.pay() == paid, true);
        context.expect("the refused bill cannot charge twice", account.get(resource), 6.0D);
    }

    private static void realException(ProbeContext context) {
        Holder<Resource> resource = resource(context);
        ResourceHolderAttachment account = new ResourceHolderAttachment();
        account.set(resource, 10.0D);
        int[] attempts = {0};
        String script = "mxt_test:cost_exception_probe";
        SaturnJsCostCallbacks.register(script, (player, params, formula) -> true, (player, params, formula) -> {
            attempts[0]++;
            throw new IllegalStateException("intentional cost probe exception");
        });
        CostPayment bill = CostPayment.of(costs(context, account));
        bill.price(List.of(new ResourceCost(resource, new Constant(2.0D)), new JsCost(script, new JsonObject()),
                new ResourceCost(resource, new Constant(1.0D))));
        Result<List<Debit>> paid = bill.pay();
        context.expect("a real exception does not cancel the bill", (paid instanceof Result.SuccessResult<?>), true);
        context.expect("fees before and after the exception both execute", account.get(resource), 7.0D);
        context.expect("known receipts survive an exception", paid.getData().orElse(List.of()).stream().mapToDouble(Debit::amount).sum(), 3.0D);
        context.expect("the exceptional bill has a terminal result", bill.pay() == paid, true);
        context.expect("the throwing fee is attempted once", attempts[0], 1);
        context.expect("repeating pay cannot write again", account.get(resource), 7.0D);
    }

    private static FixtureCost observed(CostTarget target, String name, List<String> calls) {
        return new FixtureCost(target, 1.0D, simulate -> {
            calls.add((simulate ? "check:" : "pay:") + name);
            return Result.success(List.of());
        });
    }

    private static Holder<Resource> resource(ProbeContext context) {
        return context.require(SaturnResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
    }

    private static CostContext costs(ProbeContext context, ResourceHolderAttachment account) {
        return CostContext.account(account, context.player(), FormulaContext.of(context.player()), CostOrigins.SCRIPT);
    }

    private record FixtureCost(CostTarget target, double amount,
                               Function<Boolean, Result<List<Debit>>> action) implements Cost {
        @Override
        public Result<Transaction> prepare(CostContext context) {
            return Result.success(new FixtureTransaction(this.target, this.amount, this.action));
        }

        @Override
        public MapCodec<? extends Cost> codec() {
            throw new UnsupportedOperationException("probe-only cost");
        }
    }

    private static final class FixtureTransaction extends CostTransaction {
        private final Function<Boolean, Result<List<Debit>>> action;

        private FixtureTransaction(CostTarget target, double amount, Function<Boolean, Result<List<Debit>>> action) {
            super(target, amount);
            this.action = action;
        }

        @Override
        protected Result<List<Debit>> perform(boolean simulate) {
            return this.action.apply(simulate);
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new FixtureTransaction(this.target(), amount, this.action);
        }
    }
}
