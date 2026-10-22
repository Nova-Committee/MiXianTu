package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

public record AuraCost(Holder<Aura> aura, NumberProvider amount) implements Cost {
    public static final MapCodec<AuraCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(AuraCost::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AuraCost::amount)
    ).apply(i, AuraCost::new));

    @Override
    public ResultWithData<Transaction> prepare(CostContext context) {
        double value = this.amount.evaluate(context.formula());
        if (!Double.isFinite(value) || value <= 0.0D) return ResultWithData.failure(null, CostFailure.INVALID_AMOUNT);
        return context.auraSource().bind(context, this.aura, value);
    }

    @Override
    public MapCodec<AuraCost> codec() {
        return CODEC;
    }
}
