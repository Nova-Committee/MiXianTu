package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Charge;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Takes aura by identity rather than by the value it is counted in: on a payer it becomes that aura's resource,
 * in the world it drains the pool at the context position, and with a bank it takes whole units out of that bank.
 * The target is a property of the call site (see {@link CostContext}), not of this entry.
 */
public record AuraCost(Holder<Aura> aura, NumberProvider amount) implements Cost {
    public static final MapCodec<AuraCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(AuraCost::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AuraCost::amount)
    ).apply(i, AuraCost::new));

    @Override
    public Either<Charge, CostFailure> charge(CostContext context) {
        double value = this.amount.evaluate(context.formula());
        if (!Double.isFinite(value) || value <= 0.0D) return Either.right(CostFailure.INVALID_AMOUNT);
        // Which store this comes out of is the call site's decision; whether that store is reachable at all is
        // the transaction's, so evaluation stays usable where the store is paid by hand.
        if (context.auraTarget() == CostContext.AuraTarget.VALUE) {
            // An account holds resources, not auras: what is spent is the value this aura is measured in.
            return Either.left(new Charge.Resources(Map.of(HolderHelper.id(this.aura.value().resource()), value)));
        }
        return Either.left(new Charge.Auras(Map.of(this.aura, value)));
    }

    @Override
    public MapCodec<AuraCost> codec() {
        return CODEC;
    }
}
