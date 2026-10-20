package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * Takes an amount of one datapack resource from the payer's own account; the write itself is {@link ResourceChannel}.
 */
public record ResourceCost(Holder<Resource> resource, NumberProvider amount) implements Cost {
    public static final MapCodec<ResourceCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new));

    @Override
    public ResultWithData<Transaction> prepare(CostContext context) {
        double value = ResourceChannel.evaluate(context, this.resource(), this.amount());
        if (value <= 0.0D) return ResultWithData.failure(null, CostFailure.INVALID_AMOUNT);
        CostTransaction transaction = ResourceChannel.transaction(context, this.resource(), value);
        return ResultWithData.success(transaction);
    }

    @Override
    public MapCodec<ResourceCost> codec() {
        return CODEC;
    }
}
