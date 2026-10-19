package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.ResourceChannel;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * One payment: the fees it was given and the one place they are paid from. Pricing binds every entry to a fixed
 * transaction, editing replaces fees the caller changed, and paying is one simulation pass over all of them followed by
 * one real pass - a fee is never taken before every fee has been asked, and a refusal on the way takes nothing.
 *
 * <p>Fees that name the same store are summed and charged once, so an array can reach one balance through two routes
 * (an {@code mxt:aura} charged as the value it is measured in and the {@code mxt:resource} naming that value) without
 * being checked twice against one balance. That is why a repeated target is only reported, not refused.
 *
 * <p>There is no rollback: a second pass that refuses leaves what the earlier fees already took, and the result reports
 * it. Payment is started once - after it starts the bill is closed, and a fee can no longer be added, edited or paid a
 * second time.
 */
public final class CostPayment {
    private static final Logger LOGGER = LogUtils.getLogger();
    // One warn per origin per target for the life of the process: a shape hint, not a per-payment event.
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Set<CostTarget>> PAYING = ThreadLocal.withInitial(HashSet::new);
    private final CostContext context;
    private final List<Entry> entries = new ArrayList<>();
    private @Nullable Failure failure;
    private @Nullable Identifier failedResource;
    private boolean closed;
    private boolean paying;
    private @Nullable PaymentResult taken;

    private CostPayment(CostContext context) {
        this.context = context;
    }

    /**
     * An empty bill for this context. Pricing is what fills it; nothing is bound before that.
     */
    public static CostPayment of(CostContext context) {
        return new CostPayment(context);
    }

    /**
     * One array, priced, checked and paid in one call: nothing is taken unless every entry could be priced and every
     * store could pay.
     */
    public static PaymentResult pay(List<Cost> costs, CostContext context) {
        CostPayment payment = of(context);
        Optional<Failure> refusal = payment.price(costs);
        return refusal.map(failure -> PaymentResult.denied(failure, payment.failedResource()))
                .orElseGet(payment::pay);
    }

    /**
     * Prices and binds every entry, in this bill's own context. Nothing is checked or written: this is what a caller
     * that only wants the price of an array, or that is about to edit it, asks for. A fee that cannot be priced
     * refuses here, and a payment that was priced in part pays nothing at all.
     */
    public Optional<Failure> price(List<Cost> costs) {
        return this.price(costs, this.context);
    }

    /**
     * Prices entries that belong to another context into this same bill: one payment can charge a body out of an
     * account and a ground out of a world pool without becoming two payments.
     */
    public Optional<Failure> price(List<Cost> costs, CostContext context) {
        if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
        for (Cost cost : costs) {
            ResultWithData<Cost.Transaction> prepared = cost.prepare(context);
            if (prepared.isFailure()) return this.broken(prepared.failure(), prepared.failedResource());
            this.accept(new Entry(context, prepared.getData()));
        }
        return Optional.empty();
    }

    /**
     * Asks every store of the frozen bill whether it can pay, writing nothing. A bill that was refused while being
     * priced, or paid already, answers with that instead. A refusal here is only this moment's answer: the bill stays
     * usable, so a caller can ask again after the inventory moved and then pay.
     */
    public Optional<Failure> test() {
        if (this.taken != null) return Optional.ofNullable(this.taken.failure());
        if (this.failure != null) return Optional.of(this.failure);
        for (Group group : this.plan()) {
            ResultWithData<List<Debit>> simulated = this.run(group, true);
            if (simulated.isFailure()) return Optional.of(simulated.failure());
        }
        return Optional.empty();
    }

    /**
     * The whole payment: one simulation pass over every fee, then one real pass. A simulation that refuses takes
     * nothing; a real pass that refuses keeps what it already took and says so. The answer is terminal - paying the
     * same bill again repeats it without touching a store.
     */
    public PaymentResult pay() {
        if (this.taken != null) return this.taken;
        if (this.paying) return PaymentResult.denied(CostFailure.REENTRANT, null);
        if (this.failure != null) return this.taken = PaymentResult.denied(this.failure, this.failedResource);
        List<Group> plan = this.plan();
        this.closed = true;
        this.paying = true;
        try {
            return this.taken = this.settle(plan);
        } finally {
            this.paying = false;
        }
    }

    private PaymentResult settle(List<Group> plan) {
        for (Group group : plan) {
            ResultWithData<List<Debit>> simulated = this.run(group, true);
            if (simulated.isFailure()) return PaymentResult.denied(simulated.failure(), simulated.failedResource());
        }
        List<Debit> debits = new ArrayList<>(plan.size());
        for (Group group : plan) {
            ResultWithData<List<Debit>> result = this.run(group, false);
            if (result.isFailure()) return PaymentResult.partly(debits, result.failure(), result.failedResource());
            debits.addAll(result.getData());
        }
        return PaymentResult.paid(debits);
    }

    /**
     * The resource price of this bill as the id-keyed table a listener, a script or a result names resources in. A copy:
     * what is charged is decided by the fees themselves.
     */
    public Map<Identifier, Double> resources() {
        Map<Identifier, Double> reported = new LinkedHashMap<>();
        this.resourceAmounts().forEach((resource, amount) -> reported.merge(HolderHelper.id(resource), amount, Double::sum));
        return reported;
    }

    /**
     * The resource price keyed by the resource itself, read-only. Two fees reaching one balance are already summed.
     */
    public Map<Holder<Resource>, Double> resourceAmounts() {
        Map<Holder<Resource>, Double> amounts = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            if (!(entry.transaction().target() instanceof CostTarget.Account target)) continue;
            amounts.merge(target.resource(), entry.transaction().amount(), Double::sum);
        }
        return amounts;
    }

    /**
     * The aura price, read-only: the entries charged out of a pool or a bank, which an account cannot spell out.
     */
    public Map<Holder<Aura>, Double> auras() {
        Map<Holder<Aura>, Double> amounts = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            if (!(entry.transaction().target() instanceof CostTarget.AuraStore target)) continue;
            amounts.merge(target.aura(), entry.transaction().amount(), Double::sum);
        }
        return amounts;
    }


    /**
     * Whether this bill charges anything a resource table cannot spell out: an aura out of a pool or a bank, the items
     * an entry counts for itself, a script.
     */
    public boolean beyondResources() {
        if (!this.auras().isEmpty()) return true;
        for (Entry entry : this.entries)
            if (entry.transaction().target() instanceof CostTarget.Items || entry.transaction().target() instanceof CostTarget.Script)
                return true;
        return false;
    }

    /**
     * Replaces the resource fees of this bill with the id-keyed table a listener hands back, which is how a
     * {@code ResourceConsumeEvent} changes what is paid: an id the new table does not name is not charged at all, and a
     * zero removes it. Items, pool and bank auras and scripts are untouched.
     *
     * <p>An unknown resource, a negative or non-finite amount or a missing account refuses the whole edit and leaves the
     * bill exactly as it was.
     */
    public Optional<Failure> replaceResourceAmounts(Map<Identifier, Double> amounts) {
        if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
        Provider access = this.context.formula().registries();
        if (access == null) return Optional.of(Failures.INVALID_VALUE);
        Map<Holder<Resource>, Double> resolved = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Double> entry : amounts.entrySet()) {
            Identifier id = entry.getKey();
            double amount = entry.getValue();
            if (id == null || !Double.isFinite(amount) || amount < 0.0D) return Optional.of(CostFailure.INVALID_AMOUNT);
            if (amount <= 0.0D) continue;
            Holder<Resource> resource = MxtDatapackRegistries.holder(access, MxtResourceKeys.RESOURCE, id).orElse(null);
            if (resource == null) return Optional.of(Failures.INVALID_VALUE);
            resolved.merge(resource, amount, Double::sum);
        }
        CostContext context = this.resourceContext();
        List<Entry> replaced = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries)
            if (!(entry.transaction().target() instanceof CostTarget.Account)) replaced.add(entry);
        for (Map.Entry<Holder<Resource>, Double> entry : resolved.entrySet()) {
            replaced.add(new Entry(context, ResourceChannel.transaction(context, entry.getKey(), entry.getValue())));
        }
        this.entries.clear();
        this.entries.addAll(replaced);
        return Optional.empty();
    }

    /**
     * Scales the selected fees, which is how a caller that already decided how much of a bill it owes replaces the
     * amounts instead of writing to a side table: each selected fee is replaced at its own amount times
     * {@code ratio}, and one that comes out at zero leaves the bill. A non-finite or negative ratio refuses the whole
     * edit and leaves the bill exactly as it was.
     */
    public Optional<Failure> scale(Predicate<Cost.Transaction> selection, double ratio) {
        if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
        if (!Double.isFinite(ratio) || ratio < 0.0D) return Optional.of(CostFailure.INVALID_AMOUNT);
        List<Entry> scaled = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            Cost.Transaction transaction = entry.transaction();
            if (!selection.test(transaction)) {
                scaled.add(entry);
                continue;
            }
            double amount = transaction.amount() * ratio;
            if (!Double.isFinite(amount)) return Optional.of(CostFailure.INVALID_AMOUNT);
            if (amount <= 0.0D) continue;
            scaled.add(new Entry(entry.context(), transaction.withAmount(amount)));
        }
        this.entries.clear();
        this.entries.addAll(scaled);
        return Optional.empty();
    }

    /**
     * Why this bill was last refused while being priced; null while it was priced in full.
     */
    public @Nullable Failure failure() {
        return this.failure;
    }

    /**
     * The resource that ran out, when the last refusal named one.
     */
    public @Nullable Identifier failedResource() {
        return this.failedResource;
    }

    private void accept(Entry entry) {
        this.warnSameTarget(entry.transaction().target());
        this.entries.add(entry);
    }

    // One entry per target is the shape a pack is meant to write; two that reach one store are summed and charged once,
    // which is correct but not what was written, so it is reported. A bill is also built from several definitions (a
    // talisman's inscriptions), so the hint is once per origin and target rather than once per payment.
    private void warnSameTarget(CostTarget target) {
        for (Entry known : this.entries) {
            if (!known.transaction().target().equals(target)) continue;
            if (WARNED.add(this.context.origin() + "|" + target.label()))
                LOGGER.warn("A costs array charges {} twice (origin {}); the amounts were summed, and one entry per target is the shape to write",
                        target.label(), this.context.origin());
            return;
        }
    }

    // Fees naming one store are one charge: the amounts add up in that store's own unit and one transaction takes it,
    // which is why a bill needs no side table of per-entry shares. The order is the pipeline's, not the pack's: every
    // built-in store first, then the opaque scripts, so a script never runs before the price it was written next to.
    private List<Group> plan() {
        Map<CostTarget, Cost.Transaction> merged = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            Cost.Transaction transaction = entry.transaction();
            if (!(transaction.amount() > 0.0D)) continue;
            Cost.Transaction present = merged.get(transaction.target());
            merged.put(transaction.target(), present == null ? transaction
                    : present.withAmount(present.amount() + transaction.amount()));
        }
        List<Group> stores = new ArrayList<>(merged.size());
        List<Group> scripts = new ArrayList<>();
        merged.forEach((target, transaction) -> (target instanceof CostTarget.Script ? scripts : stores)
                .add(new Group(target, transaction)));
        stores.addAll(scripts);
        return stores;
    }

    // A nested payment touching a store this one is writing is refused rather than interleaved, while an independent
    // one on another store goes through.
    private ResultWithData<List<Debit>> run(Group group, boolean simulate) {
        Set<CostTarget> paying = PAYING.get();
        if (!paying.add(group.target())) return ResultWithData.failure(List.of(), CostFailure.REENTRANT);
        try {
            return group.transaction().execute(simulate);
        } finally {
            paying.remove(group.target());
        }
    }

    private CostContext resourceContext() {
        for (Entry entry : this.entries)
            if (entry.transaction().target() instanceof CostTarget.Account) return entry.context();
        return this.context;
    }

    private Optional<Failure> broken(Failure failure, @Nullable Identifier resource) {
        this.failure = failure;
        if (resource != null) this.failedResource = resource;
        return Optional.of(failure);
    }

    private record Entry(CostContext context, Cost.Transaction transaction) {
    }

    private record Group(CostTarget target, Cost.Transaction transaction) {
    }

    /**
     * What a payment did: every fee it really took, or why it took nothing, or why it stopped halfway.
     */
    public static final class PaymentResult extends Result {
        private final List<Debit> debits;

        private PaymentResult(@Nullable Failure failure, @Nullable Identifier failedResource, List<Debit> debits) {
            super(failure, failedResource);
            this.debits = List.copyOf(debits);
        }

        static PaymentResult paid(List<Debit> debits) {
            return new PaymentResult(null, null, debits);
        }

        static PaymentResult denied(Failure failure, @Nullable Identifier failedResource) {
            return new PaymentResult(failure, failedResource, List.of());
        }

        // A channel that refused mid-way: what the earlier fees took is still what happened, and is reported as such.
        static PaymentResult partly(List<Debit> debits, Failure failure, @Nullable Identifier failedResource) {
            return new PaymentResult(failure, failedResource, debits);
        }

        /**
         * What was really taken, one per store.
         */
        public List<Debit> debits() {
            return this.debits;
        }

        public Map<Identifier, Double> resources() {
            Map<Identifier, Double> reported = new LinkedHashMap<>();
            for (Debit debit : this.debits) {
                if (!(debit.target() instanceof CostTarget.Account target)) continue;
                reported.merge(HolderHelper.id(target.resource()), debit.amount(), Double::sum);
            }
            return reported;
        }

        public Map<Holder<Aura>, Double> auras() {
            Map<Holder<Aura>, Double> reported = new LinkedHashMap<>();
            for (Debit debit : this.debits) {
                if (!(debit.target() instanceof CostTarget.AuraStore target)) continue;
                reported.merge(target.aura(), debit.amount(), Double::sum);
            }
            return reported;
        }
    }
}
