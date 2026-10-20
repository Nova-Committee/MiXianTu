package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
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
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Checks every bound fee, then attempts every real charge in entry order. Duplicate targets are diagnostic only;
 * real failures warn and continue, with known debits retained and the final answer cached against repeat charges.
 */
public final class CostPayment {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LoggerDispatcher DUPLICATE_TARGET = LoggerDispatcher.of(LOGGER);
    private final CostContext context;
    private final List<Entry> entries = new ArrayList<>();
    private @Nullable Failure failure;
    private @Nullable Identifier failedResource;
    private boolean closed;
    private boolean paying;
    private boolean uncertain;
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
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.COST)) {
            CostPayment payment = of(context);
            Optional<Failure> refusal = payment.price(costs);
            return refusal.map(failure -> PaymentResult.denied(failure, payment.failedResource()))
                    .orElseGet(payment::pay);
        }
    }

    /**
     * Prices and binds every entry, in this bill's own context. Nothing is checked or written: this is what a caller
     * that only wants the price of an array, or that is about to edit it, asks for. A fee that cannot be priced
     * refuses here, and a payment that was priced in part pays nothing at all.
     */
    public Optional<Failure> price(List<Cost> costs) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.COST)) {
            return this.price(costs, this.context);
        }
    }

    /**
     * Prices entries that belong to another context into this same bill: one payment can charge a body out of an
     * account and a ground out of a world pool without becoming two payments.
     */
    public Optional<Failure> price(List<Cost> costs, CostContext context) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.COST)) {
            if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
            for (Cost cost : costs) {
                ResultWithData<Cost.Transaction> prepared = cost.prepare(context);
                if (prepared.isFailure()) return this.broken(prepared.failure(), prepared.failedResource());
                this.entries.add(new Entry(context, prepared.getData()));
            }
            return Optional.empty();
        }
    }

    /**
     * Asks every store of the frozen bill whether it can pay, writing nothing. A bill that was refused while being
     * priced, or paid already, answers with that instead. A refusal here is only this moment's answer: the bill stays
     * usable, so a caller can ask again after the inventory moved and then pay.
     */
    public Optional<Failure> test() {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.COST)) {
            if (this.taken != null) return Optional.ofNullable(this.taken.failure());
            if (this.failure != null) return Optional.of(this.failure);
            List<Entry> plan = this.plan();
            if (plan == null) return Optional.of(CostFailure.INVALID_AMOUNT);
            for (Entry entry : plan) {
                ResultWithData<List<Debit>> simulated = entry.transaction().execute(true);
                if (simulated.isFailure()) return Optional.of(simulated.failure());
            }
            return Optional.empty();
        }
    }

    /**
     * Simulation failures refuse the bill; real failures only warn and do not cancel it or later fees.
     * Repeating this call returns the terminal answer without charging again.
     */
    public PaymentResult pay() {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.COST)) {
            if (this.taken != null) return this.taken;
            if (this.paying) return PaymentResult.denied(CostFailure.REENTRANT, null);
            // Asked once the real pass had begun and never answered: whatever it wrote before the throw cannot be taken
            // back, so a second attempt could charge a fee that is already paid. The bill stays unanswered rather than
            // guessing, and nothing is written again.
            if (this.uncertain) return this.taken = PaymentResult.denied(CostFailure.UNCERTAIN, null);
            if (this.failure != null) return this.taken = PaymentResult.denied(this.failure, this.failedResource);
            List<Entry> plan = this.plan();
            if (plan == null) return this.taken = PaymentResult.denied(CostFailure.INVALID_AMOUNT, null);
            this.closed = true;
            this.paying = true;
            try {
                return this.taken = this.settle(plan);
            } finally {
                this.paying = false;
            }
        }
    }

    private PaymentResult settle(List<Entry> plan) {
        for (Entry entry : plan) {
            ResultWithData<List<Debit>> simulated = entry.transaction().execute(true);
            if (simulated.isFailure()) return PaymentResult.denied(simulated.failure(), simulated.failedResource());
        }
        List<Debit> debits = new ArrayList<>(plan.size());
        // From here on a store may really be written, so an answer that never comes back leaves the bill unanswered.
        this.uncertain = true;
        for (Entry entry : plan) {
            try {
                ResultWithData<List<Debit>> result = entry.transaction().execute(false);
                debits.addAll(result.getData());
                if (result.isFailure())
                    LOGGER.warn("Cost deduction failed for {} (origin {}, reason {}, resource {}); continuing payment",
                            entry.transaction().target().label(), entry.context().origin(), result.failure(), result.failedResource());
            } catch (RuntimeException exception) {
                LOGGER.warn("Cost deduction threw for {} (origin {}); continuing payment with known debits only",
                        entry.transaction().target().label(), entry.context().origin(), exception);
            }
        }
        this.uncertain = false;
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
     * The resource price keyed by the resource itself; projection totals do not merge executable fees.
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
     * <p>The table is one owner's amounts, so it is only unambiguous for a bill that charges one account: a bill naming
     * two accounts keys its resources by id alone and cannot say whose balance an id means. Such a bill refuses the
     * edit instead of picking one payer for all of it - see {@link #samePayer()}.
     *
     * <p>An unknown resource, a negative or non-finite amount or a missing account refuses the whole edit and leaves the
     * bill exactly as it was.
     */
    public Optional<Failure> replaceResourceAmounts(Map<Identifier, Double> amounts) {
        if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
        if (!this.samePayer()) return Optional.of(CostFailure.AMBIGUOUS_PAYER);
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
     * Whether every resource fee of this bill charges the same account. A table keyed by resource id only is one
     * owner's amounts, so an edit of a bill that mixes accounts has no single owner to apply to.
     */
    public boolean samePayer() {
        ResourceHolderAttachment account = null;
        for (Entry entry : this.entries) {
            if (!(entry.transaction().target() instanceof CostTarget.Account target)) continue;
            if (account == null) account = target.account();
            else if (account != target.account()) return false;
        }
        return true;
    }

    /**
     * Scales the selected fees, which is how a caller that already decided how much of a bill it owes replaces the
     * amounts instead of writing to a side table: each selected fee is replaced at its own amount times
     * {@code ratio}, and one that comes out at zero leaves the bill. A non-finite or negative ratio refuses the whole
     * edit and leaves the bill exactly as it was.
     */
    public Optional<Failure> scale(Predicate<Cost.Transaction> selection, double ratio) {
        return this.scale(selection, transaction -> ratio);
    }

    /**
     * Scales each fee by its own ratio, which is how a caller that was granted a different share of each source replaces
     * the amounts: {@code ratio} is asked once per fee, so one source's grant cannot stand in for another's. A
     * non-finite or negative ratio refuses the whole edit and leaves the bill exactly as it was.
     */
    public Optional<Failure> scale(Predicate<Cost.Transaction> selection, ToDoubleFunction<Cost.Transaction> ratio) {
        if (this.closed) return Optional.of(CostFailure.BILL_CLOSED);
        List<Entry> scaled = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            Cost.Transaction transaction = entry.transaction();
            if (!selection.test(transaction)) {
                scaled.add(entry);
                continue;
            }
            double factor = ratio.applyAsDouble(transaction);
            if (!Double.isFinite(factor) || factor < 0.0D) return Optional.of(CostFailure.INVALID_AMOUNT);
            double amount = transaction.amount() * factor;
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

    private @Nullable List<Entry> plan() {
        List<Entry> plan = new ArrayList<>(this.entries.size());
        Set<Object> seen = new HashSet<>();
        Set<Object> reported = new HashSet<>();
        for (Entry entry : this.entries) {
            Cost.Transaction transaction = entry.transaction();
            if (!Double.isFinite(transaction.amount()) || transaction.amount() < 0.0D) return null;
            if (transaction.amount() == 0.0D) continue;
            CostTarget target = transaction.target();
            Object key = target instanceof CostTarget.Script script
                    ? new ScriptTarget(entry.context().payer(), script.id()) : target;
            if (!seen.add(key) && reported.add(key))
                DUPLICATE_TARGET.log("A costs array charges {} more than once (origin {}); use one entry per target; entries remain separate and run in order",
                        target.label(), entry.context().origin());
            plan.add(entry);
        }
        return List.copyOf(plan);
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

    private record ScriptTarget(@Nullable Object payer, String id) {
    }

    /**
     * A preflight refusal, or a completed real pass with its known debits; success does not promise full collection.
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

        /**
         * What was reported as really taken, including partial debits from a failed fee.
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
