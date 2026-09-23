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
import java.util.function.Function;

/**
 * One entry of a costs array. An entry only describes what it takes ({@link #charge(CostContext)}); the same
 * evaluated plan is then both checked and spent by {@link CostTransaction}, so there is no second implementation
 * of "can this be paid" to drift away from "take it".
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
     * Evaluates this entry against the channels the context offers. The right side is a failure - a formula that
     * cannot produce a finite positive amount, or a channel this context does not have. Nothing is written here.
     * {@code mxt:js} is the one entry whose availability is answered by the script itself, so planning it calls
     * the script's read-only check.
     */
    Either<Charge, CostFailure> charge(CostContext context);

    MapCodec<? extends Cost> codec();
}
