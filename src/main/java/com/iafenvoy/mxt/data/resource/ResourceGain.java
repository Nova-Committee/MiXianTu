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
 * A positive resource gain evaluated by a data-driven action.
 */
public record ResourceGain(Holder<ResourceDefinition> resource, NumberProvider amount) {
    public static final Codec<ResourceGain> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceDefinition.HOLDER_CODEC.fieldOf("id").forGetter(ResourceGain::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ResourceGain::amount)
    ).apply(instance, ResourceGain::new));
    public static final Codec<List<ResourceGain>> LIST_CODEC = AutoIgnoreListCodec.create(CODEC);

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
