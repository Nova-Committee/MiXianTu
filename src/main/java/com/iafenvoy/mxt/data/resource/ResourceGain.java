package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

/**
 * A positive resource gain evaluated by a data-driven action.
 */
public record ResourceGain(Holder<Resource> resource, NumberProvider amount) {
    public static final Codec<ResourceGain> CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("id").forGetter(ResourceGain::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceGain::amount)
    ).apply(i, ResourceGain::new));

    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalStateException("Resource gain " + HolderHelper.id(this.resource) + " must evaluate to a finite non-negative value");
        return value;
    }

    public Identifier id() {
        return HolderHelper.id(this.resource);
    }
}
