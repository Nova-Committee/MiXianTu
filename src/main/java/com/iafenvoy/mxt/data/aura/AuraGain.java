package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * A positive aura gain evaluated by a data-driven action: how much of one named aura an action hands over.
 * <p>
 * It is the aura counterpart of {@code ResourceGain} rather than the same record, because the two answer
 * different questions: a {@code ResourceGain} writes an amount into a value and is what {@code add_resource}
 * uses - including for plain counters that carry no aura at all - while this names an aura, which is what
 * makes a gain a gain of aura. The amount is credited to the value the aura is counted in
 * ({@code Aura#resource()}) at the boundary where the holder's pool is written.
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
