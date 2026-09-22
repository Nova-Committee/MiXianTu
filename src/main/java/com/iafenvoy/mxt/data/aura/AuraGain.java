package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * A positive aura gain evaluated by a data-driven action. It is not a {@code ResourceGain}: that writes an amount
 * into a value (including plain counters that carry no aura), while this names an aura. The amount is credited to
 * the value the aura is counted in ({@code Aura#resource()}).
 */
public record AuraGain(Holder<Aura> aura, NumberProvider amount) {
    public static final Codec<AuraGain> CODEC = RecordCodecBuilder.create(i -> i.group(
            Aura.CODEC.fieldOf("id").forGetter(AuraGain::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AuraGain::amount)
    ).apply(i, AuraGain::new));

    public double evaluate(FormulaContext context) {
        double value = this.amount.evaluate(context);
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalStateException("Aura gain " + HolderHelper.id(this.aura) + " must evaluate to a finite non-negative value");
        return value;
    }
}
