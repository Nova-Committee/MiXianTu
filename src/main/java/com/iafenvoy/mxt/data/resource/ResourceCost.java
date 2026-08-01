package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * One entry in the common costs array.
 */
public record ResourceCost(Identifier id, NumberProvider amount) {
    public static final Codec<ResourceCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(ResourceCost::id),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(instance, ResourceCost::new));

    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalStateException("Resource cost " + this.id + " must evaluate to a finite positive value");
        }
        return value;
    }
}
