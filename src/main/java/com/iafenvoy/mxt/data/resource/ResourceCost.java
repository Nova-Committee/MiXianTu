package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One entry in the common costs array.
 */
public record ResourceCost(Holder<Resource> resource, NumberProvider amount) {
    public static final Codec<ResourceCost> CODEC = RecordCodecBuilder.create(i -> i.group(
            Resource.CODEC.fieldOf("id").forGetter(ResourceCost::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceCost::amount)
    ).apply(i, ResourceCost::new));
    public static final Codec<List<ResourceCost>> LIST_CODEC = AutoIgnoreListCodec.create(CODEC);

    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalStateException("Resource cost " + HolderHelper.id(this.resource) + " must evaluate to a finite positive value");
        }
        return value;
    }

    public Identifier id() {
        return HolderHelper.id(this.resource);
    }
}
