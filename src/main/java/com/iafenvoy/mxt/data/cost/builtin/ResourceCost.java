package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Charge;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostChannel;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Takes an amount of one datapack resource from the payer's own attachment.
 */
public record ResourceCost(Holder<Resource> resource, NumberProvider amount) implements Cost {
    public static final MapCodec<ResourceCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new));

    public Identifier id() {
        return HolderHelper.id(this.resource);
    }

    /**
     * The evaluated amount, or {@link Double#NaN} when the formula cannot produce a finite positive one.
     */
    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        return Double.isFinite(value) && value > 0.0D ? value : Double.NaN;
    }

    @Override
    public Either<Charge, CostFailure> charge(CostContext context) {
        // Each cost is evaluated with the formula context of the resource it spends, so a cost may refer to that
        // resource's realm rank and absorbed aura.
        FormulaContext formula = context.payer() == null ? context.formula()
                : ResourceService.formulaContext(context.payer(), this.id(), context.formula());
        double value = this.evaluate(formula);
        if (Double.isNaN(value)) return Either.right(CostFailure.INVALID_AMOUNT);
        if (!context.hasChannel(CostChannel.RESOURCE_ACCOUNT)) return Either.right(CostFailure.NO_CHANNEL);
        return Either.left(new Charge.Resources(Map.of(this.id(), value)));
    }

    @Override
    public MapCodec<ResourceCost> codec() {
        return CODEC;
    }
}
