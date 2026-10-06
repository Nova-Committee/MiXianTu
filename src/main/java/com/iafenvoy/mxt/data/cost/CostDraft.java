package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * One channel's half of a payment: the requirements of one entry type, merged as they arrive, checked once and
 * written once. Its constructor copies what the price is checked against, so a draft created on demand is cheap, a
 * draft that is never committed has written nothing, and {@link #reset} has nothing to put back.
 *
 * <p>{@link CostDraftManager} registers which draft serves which type, and {@link CostPayment} drives them. A type it
 * has no draft for answers {@link Cost#test} and {@link Cost#commit} itself, because merging is exactly what such an
 * entry cannot do.
 */
public interface CostDraft<T extends Cost> {
    /**
     * Merges one entry's requirement into this draft, keeping what is already loaded. The answer is why the entry
     * itself cannot be charged here - a formula that produced no usable amount, or a channel this context does not
     * offer - and nothing is written; whether the store can pay for everything loaded is {@link #test}'s answer.
     */
    Optional<CostFailure> load(T cost);

    /**
     * Whether the store can pay for everything loaded so far, asked without writing anything. Read-only on purpose:
     * it is what a payment runs after every entry, and what a caller that only wants a price never has to run.
     * <p>
     * A draft with nothing loaded answers success: the manager gives every registered type a draft, used or not.
     */
    Optional<CostFailure> test();

    /**
     * Writes everything loaded. A store that has changed since {@link #test} can refuse here, which is why the
     * caller puts back the drafts it committed - this one included, because a channel that writes entry by entry
     * can have written some of them.
     * <p>
     * A draft with nothing loaded writes nothing and answers success.
     */
    Optional<CostFailure> commit();

    /**
     * Puts back what {@link #commit} wrote, and only that: a store that moved on in between keeps what it gained.
     * A draft that never committed has written nothing and resets nothing.
     */
    void reset();

    /**
     * The resource that ran out, when {@link #test} or {@link #commit} answered
     * {@link CostFailure#INSUFFICIENT_RESOURCE}. Null for every other reason and for every other channel.
     */
    default @Nullable Identifier failedResource() {
        return null;
    }

    /**
     * The aura amounts this draft charges, or null when it charges no aura store. The table is the draft's own: a
     * payment reads it - for its result, and to answer whether it charges more than the account table spells out -
     * and nothing else writes it.
     */
    default @Nullable Map<Holder<Aura>, Double> auras() {
        return null;
    }

    /**
     * Whether what this draft charges is an amount table. False for a channel whose price only the draft knows - how
     * many items it takes - which is what a caller with room for amounts alone has to refuse.
     */
    default boolean chargesAmounts() {
        return true;
    }
}
