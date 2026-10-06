package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * One payment: the drafts its entries are loaded into and the two tables they charge. Loading merges an entry into
 * the draft of its type and answers at once, testing asks whether the stores can pay for all of it, and committing
 * writes it - so an array that cannot be paid stops at the entry that ran out instead of loading all of it first, and
 * nothing is written before the commit.
 *
 * <p>{@link CostDraftManager} registers which draft serves which type and creates them; a draft is never built here
 * or by a call site. Each table belongs to the draft that charges it, and the payment holds the map too because it
 * answers for it - see {@link #resources()} and {@link #auras()}. {@link #commit} is the one write, and it drops the
 * drafts with it.
 *
 * <p>Write order is chosen so that the only channel that cannot be put back - a script - runs after every channel
 * that can.
 */
public final class CostPayment {
    private final CostContext context;
    private final Map<Identifier, Double> account = new LinkedHashMap<>();
    private final Map<Class<?>, CostDraft<?>> drafts = new LinkedHashMap<>();
    private final List<Cost> direct = new ArrayList<>();
    private @Nullable Map<Holder<Aura>, Double> auras;
    private @Nullable CostFailure failure;
    private @Nullable Identifier failedResource;
    private @Nullable Result written;
    private boolean beyondResources;

    private CostPayment(CostContext context) {
        this.context = context;
    }

    /**
     * Starts a payment over the entries a caller is about to load into it.
     */
    public static CostPayment of(CostContext context) {
        return new CostPayment(context);
    }

    /**
     * Loads, tests and writes one array in one call: nothing is written unless every entry can be charged.
     */
    public static Result pay(List<Cost> costs, CostContext context) {
        CostPayment payment = of(context);
        Optional<CostFailure> refusal = payment.loadAndTest(costs);
        return refusal.map(costFailure -> Result.denied(costFailure, payment.failedResource())).orElseGet(payment::commit);
    }

    /**
     * Writes several payments as one boundary: a later payment that refuses puts back what the earlier ones wrote,
     * and an entry with no draft - a script - runs after every channel that can still be put back. A payment is one
     * pass, so it is written once and keeps no draft.
     */
    public static Result payAll(List<CostPayment> payments) {
        Result result = write(payments);
        for (CostPayment payment : payments) {
            payment.written = result;
            payment.drafts.clear();
        }
        return result;
    }

    /**
     * Checks one entry the way a payment would, for the drafted types' own {@link Cost#test}. Not the path for a
     * type without a draft: such an entry is the one that has to answer for itself.
     */
    public static Optional<CostFailure> test(Cost cost, CostContext context) {
        CostDraft<?> draft = single(cost, context);
        Optional<CostFailure> refusal = load(draft, cost);
        return refusal.isPresent() ? refusal : draft.test();
    }

    /**
     * Takes one entry the way a payment would, for the drafted types' own {@link Cost#commit}.
     */
    public static Optional<CostFailure> commit(Cost cost, CostContext context) {
        CostDraft<?> draft = single(cost, context);
        Optional<CostFailure> refusal = load(draft, cost);
        return refusal.isPresent() ? refusal : draft.commit();
    }

    /**
     * The load step: merges one entry into the draft of its type, or asks the entry itself when its type has no
     * draft. The answer is why the entry cannot be charged at all; the store's ability to pay for what is loaded is
     * {@link #test}.
     */
    public Optional<CostFailure> load(Cost cost) {
        CostDraft<?> draft = this.draft(cost.getClass());
        if (draft == null) {
            Optional<CostFailure> asked = cost.test(this.context);
            if (asked.isEmpty()) {
                this.direct.add(cost);
                this.beyondResources = true;
            }
            return asked.flatMap(costFailure -> this.record(costFailure, null));
        }
        // Remembered here rather than read off the drafts later: every type has a draft once the payment is tested,
        // and a caller asks this before that.
        if (!draft.chargesAmounts()) this.beyondResources = true;
        Optional<CostFailure> refusal = load(draft, cost);
        return refusal.flatMap(costFailure -> this.record(costFailure, draft));
    }

    /**
     * The load step for a whole array, without asking whether it can be paid.
     */
    public Optional<CostFailure> loadAll(List<Cost> costs) {
        for (Cost cost : costs) {
            Optional<CostFailure> refusal = this.load(cost);
            if (refusal.isPresent()) return refusal;
        }
        return Optional.empty();
    }

    /**
     * The load and test steps in one call, answering the earliest refusal: what makes an unaffordable array stop at
     * the entry that ran out, and what a one-shot payment runs.
     */
    public Optional<CostFailure> loadAndTest(List<Cost> costs) {
        Optional<CostFailure> refusal = this.loadAll(costs);
        return refusal.isPresent() ? refusal : this.test();
    }

    /**
     * The test step: whether every draft can pay for what has been loaded into it. Writes nothing.
     */
    public Optional<CostFailure> test() {
        this.createMissingDrafts();
        for (CostDraft<?> draft : this.drafts.values()) {
            Optional<CostFailure> refusal = draft.test();
            if (refusal.isPresent()) return this.record(refusal.get(), draft);
        }
        return Optional.empty();
    }

    /**
     * The resource price loaded so far, mutable: a listener that re-prices a payment writes here, and a caller
     * paying part of the price itself takes out what it paid. This is the account table of the resource draft, and
     * what the payment charges.
     */
    public Map<Identifier, Double> resources() {
        return this.account;
    }

    /**
     * The aura price loaded so far, the table of the aura draft, mutable for the same reason.
     */
    public Map<Holder<Aura>, Double> auras() {
        // Asked before this payment has loaded an aura entry, and answered all the same: every registered type gets
        // its draft, and the one that charges auras owns this table.
        this.createMissingDrafts();
        return Objects.requireNonNull(this.auras, "No registered cost draft charges an aura");
    }

    /**
     * Whether this payment charges anything the resource table does not spell out: an aura, the items a draft counts
     * for itself, a script. What a caller with room for resource amounts only has to leave unsaid.
     */
    public boolean beyondResources() {
        return this.beyondResources || (this.auras != null && !this.auras.isEmpty());
    }

    /**
     * Why this payment was last refused; null while nothing has been refused.
     */
    public @Nullable CostFailure failure() {
        return this.failure;
    }

    /**
     * The resource that ran out, when the last refusal was {@link CostFailure#INSUFFICIENT_RESOURCE}: what a caller
     * names back to the player.
     */
    public @Nullable Identifier failedResource() {
        return this.failedResource;
    }

    /**
     * Writes this payment, and only this payment. A payment is one pass: the first write is the only one, and the
     * drafts it created are dropped with it.
     */
    public Result commit() {
        if (this.written != null) return this.written;
        return payAll(List.of(this));
    }

    // The draft for one entry on its own, for the single-entry step above.
    private static CostDraft<?> single(Cost cost, CostContext context) {
        CostDraft<?> draft = CostDraftManager.create(cost.getClass(), context, new LinkedHashMap<>());
        if (draft == null) throw new IllegalStateException("Cost type " + cost.getClass().getName()
                + " has no cost draft and answers test() and commit() itself");
        return draft;
    }

    @SuppressWarnings("unchecked")
    private static Optional<CostFailure> load(CostDraft<?> draft, Cost cost) {
        return ((CostDraft<Cost>) draft).load(cost);
    }

    private static Result write(List<CostPayment> payments) {
        List<CostDraft<?>> written = new ArrayList<>();
        for (CostPayment payment : payments) {
            payment.createMissingDrafts();
            for (CostDraft<?> draft : payment.drafts.values()) {
                Optional<CostFailure> refusal = draft.commit();
                written.add(draft);
                if (refusal.isPresent()) return Result.denied(refusal.get(), draft.failedResource(), written);
            }
        }
        for (CostPayment payment : payments) {
            for (Cost cost : payment.direct) {
                Optional<CostFailure> refusal = cost.commit(payment.context);
                if (refusal.isPresent()) return Result.denied(refusal.get(), null, written);
            }
        }
        Map<Identifier, Double> resources = new LinkedHashMap<>();
        Map<Holder<Aura>, Double> auras = new LinkedHashMap<>();
        for (CostPayment payment : payments) {
            payment.account.forEach((id, amount) -> resources.merge(id, amount, Double::sum));
            if (payment.auras != null)
                payment.auras.forEach((aura, amount) -> auras.merge(aura, amount, Double::sum));
        }
        return Result.paid(resources, auras);
    }

    // A reset that cannot run is louder than a wrong balance: the payment is abandoned and the reason logged.
    private static void reset(List<CostDraft<?>> written) {
        for (int index = written.size() - 1; index >= 0; index--) {
            try {
                written.get(index).reset();
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Could not reset part of a cost payment; a balance may be wrong", exception);
            }
        }
    }

    // Every registered type gets its draft, used or not: one that charged nothing is a no-op, and this is what gives
    // a table another type wrote into - an mxt:aura entry charged as the resource it is counted in lands in the
    // resource table - a draft to check and write it through.
    private void createMissingDrafts() {
        for (Class<? extends Cost> type : CostDraftManager.types()) this.draft(type);
    }

    // The one draft of its type: an entry is loaded through it, and every later entry of that type is merged into
    // what it already holds. The table a draft owns is picked up here, so the payment can answer for it later.
    private @Nullable CostDraft<?> draft(Class<? extends Cost> type) {
        CostDraft<?> existing = this.drafts.get(type);
        if (existing != null) return existing;
        CostDraft<?> created = CostDraftManager.create(type, this.context, this.account);
        if (created != null) {
            this.drafts.put(type, created);
            Map<Holder<Aura>, Double> auras = created.auras();
            if (auras != null) this.auras = auras;
        }
        return created;
    }

    // The reason a payment was refused, and the resource it names when it names one: a caller reads both off the
    // payment instead of carrying a result through every step.
    private Optional<CostFailure> record(CostFailure failure, @Nullable CostDraft<?> draft) {
        this.failure = failure;
        this.failedResource = draft == null ? null : draft.failedResource();
        return Optional.of(failure);
    }

    /**
     * What a payment did: what it took, or why it took nothing.
     */
    public record Result(boolean paid, CostFailure failure, Identifier failedResource,
                         Map<Identifier, Double> resources, Map<Holder<Aura>, Double> auras) {
        public Result {
            resources = new LinkedHashMap<>(resources);
            auras = new LinkedHashMap<>(auras);
        }

        static Result paid(Map<Identifier, Double> resources, Map<Holder<Aura>, Double> auras) {
            return new Result(true, null, null, resources, auras);
        }

        // Nothing is written before the commit, so a refusal here has nothing to put back.
        static Result denied(CostFailure failure, @Nullable Identifier failedResource) {
            return denied(failure, failedResource, List.of());
        }

        // Everything this payment itself wrote comes back through the drafts it already committed.
        static Result denied(CostFailure failure, @Nullable Identifier failedResource, List<CostDraft<?>> written) {
            reset(written);
            return new Result(false, failure, failedResource, Map.of(), Map.of());
        }
    }
}
