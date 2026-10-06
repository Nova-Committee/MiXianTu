package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One entry of a costs array. An entry is data: what it costs is read, merged and written by the {@link CostDraft}
 * of its type, so there is no second implementation of "can this be paid" to drift away from "take it", and two
 * entries that reach the same store add up instead of depending on the order they were written in.
 *
 * <p>{@link #test} and {@link #commit} are what an entry does on its own, and they are what {@link CostPayment}
 * calls for a type it has no draft for - a script, whose state belongs to the script.
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
    Codec<List<Cost>> LIST_CODEC = CODEC.listOf().validate(Costs::validate);

    /**
     * Whether this one entry can be charged here, asked without writing anything. The right side is a failure - a
     * formula that cannot produce a finite positive amount, or a channel this context does not offer.
     */
    Optional<CostFailure> test(CostContext context);

    /**
     * Takes what this one entry asks for. Not reversible: an entry whose type has a draft is taken through it.
     */
    Optional<CostFailure> commit(CostContext context);

    MapCodec<? extends Cost> codec();
}
