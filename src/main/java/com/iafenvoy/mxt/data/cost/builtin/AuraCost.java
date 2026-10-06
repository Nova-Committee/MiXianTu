package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * Takes aura by identity rather than by the value it is counted in: on a payer it becomes that aura's resource,
 * in the world it drains the pool at the context position, and with a bank it takes whole units out of that bank.
 * The target is a property of the call site (see {@link CostContext}), not of this entry; {@code AuraCostDraft}
 * holds what that means for each of the three.
 */
public record AuraCost(Holder<Aura> aura, NumberProvider amount) implements Cost {
    public static final MapCodec<AuraCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(AuraCost::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AuraCost::amount)
    ).apply(i, AuraCost::new));

    @Override
    public Optional<CostFailure> test(CostContext context) {
        return CostPayment.test(this, context);
    }

    @Override
    public Optional<CostFailure> commit(CostContext context) {
        return CostPayment.commit(this, context);
    }

    @Override
    public MapCodec<AuraCost> codec() {
        return CODEC;
    }
}
