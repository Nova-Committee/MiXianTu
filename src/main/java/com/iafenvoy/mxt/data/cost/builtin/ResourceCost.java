package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Takes an amount of one datapack resource from the payer's own attachment; see {@code ResourceCostDraft} for how
 * the amount is evaluated and when it is written.
 */
public record ResourceCost(Holder<Resource> resource, NumberProvider amount) implements Cost {
    public static final MapCodec<ResourceCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new));

    public Identifier id() {
        return HolderHelper.id(this.resource);
    }

    @Override
    public Optional<CostFailure> test(CostContext context) {
        return CostPayment.test(this, context);
    }

    @Override
    public Optional<CostFailure> commit(CostContext context) {
        return CostPayment.commit(this, context);
    }

    @Override
    public MapCodec<ResourceCost> codec() {
        return CODEC;
    }
}
