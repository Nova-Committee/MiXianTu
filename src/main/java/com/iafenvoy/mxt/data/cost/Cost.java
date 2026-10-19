package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.function.Function;

/**
 * One entry of a costs array. An entry is data and does one thing: given the context of a payment it prices itself and
 * returns the fee it would charge - a {@link Transaction}, bound to one store at one fixed amount. It does not ask any
 * store whether it can pay, does not write, and remembers nothing between calls, so "what this costs" is decided in
 * exactly one place and the payment is what decides "can this be paid" and "take it".
 *
 * <p>The fixed amount is why a dynamic formula is evaluated once per payment: everything after {@link #prepare} works
 * on the bound fee, and a payment that re-prices or scales one creates a replacement rather than re-running the
 * formula. {@link Transaction#execute} is the only channel write there is.
 *
 * <p>Two entries reaching one store is not a load error: the payment sums them, charges once and warns, and a pack
 * keeps one entry per target.
 */
public interface Cost {
    Codec<Cost> TYPED_CODEC = MxtRegistries.COST_TYPE.byNameCodec().dispatch("type", Cost::codec, Function.identity());
    /**
     * The pre-{@code Cost} resource shorthand ({@code {"id": ..., "amount": ...}}) is still read as an
     * {@code mxt:resource} entry.
     */
    Codec<Cost> CODEC = Codec.either(TYPED_CODEC, RecordCodecBuilder.<ResourceCost>create(i -> i.group(
            Resource.CODEC.fieldOf("id").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new))).xmap(
            value -> value.map(Function.identity(), Function.identity()),
            value -> value instanceof ResourceCost resource ? Either.right(resource) : Either.left(value)
    );
    Codec<List<Cost>> LIST_CODEC = CODEC.listOf();

    /**
     * Prices this entry for one payment and binds it: the amount is evaluated now and only now, the store is resolved
     * from {@code context}, and the parameters are frozen. Nothing is checked or written, and no attachment, chunk or
     * store is created - a fee that cannot even be priced (a broken formula, a channel this context does not offer)
     * refuses here, which is why a payment that prepared nothing takes nothing.
     */
    ResultWithData<Transaction> prepare(CostContext context);

    MapCodec<? extends Cost> codec();

    /**
     * One bound fee: a fixed store at a fixed amount, and the two passes over it. It belongs to one payment - it is
     * not a definition, not a cache and not a singleton, and it is never serialized.
     */
    interface Transaction {
        /**
         * The store this fee comes out of.
         */
        CostTarget target();

        /**
         * The fixed amount, in the unit of that store.
         */
        double amount();

        /**
         * The check when {@code simulate} is set, the real write when it is not. A simulated call writes nothing and
         * answers with no debit; a refused call answers with the reason and takes nothing of its own. The first real
         * answer is terminal, so the same fee cannot be taken twice.
         */
        ResultWithData<List<Debit>> execute(boolean simulate);

        /**
         * The same binding at another fixed amount, which is how a payment edits what it is about to charge. Zero is
         * dropped by the payment instead of being executed.
         */
        Transaction withAmount(double amount);
    }
}
