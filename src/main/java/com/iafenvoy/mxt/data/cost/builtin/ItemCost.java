package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Takes matching items out of the payer's inventory, or out of a container the call site names; see
 * {@code ItemCostDraft} for how the slots are counted and when the counts are written back.
 */
public record ItemCost(List<Entry> entries, NumberProvider amount) implements ItemMatcher, Cost {
    public static final MapCodec<ItemCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemMatcher::entries),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ItemCost::amount)
    ).apply(i, ItemCost::new));

    @Override
    public Optional<CostFailure> test(CostContext context) {
        return CostPayment.test(this, context);
    }

    @Override
    public Optional<CostFailure> commit(CostContext context) {
        return CostPayment.commit(this, context);
    }

    @Override
    public MapCodec<ItemCost> codec() {
        return CODEC;
    }

    // Costs are matched, never ranked: the entries come from the record component, and the priority is the default.
    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }
}
