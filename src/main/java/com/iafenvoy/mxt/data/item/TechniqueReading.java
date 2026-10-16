package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * How one stack is read, resolved: the technique's own three fields, already overlaid by the stack's
 * {@code mxt:technique_reading} component where it wrote one. The gesture belongs to the technique, so every manual
 * of it reads the same way until a stack says otherwise. The length stays a provider because it may depend on the
 * reader, so only {@link #holdTicks(FormulaContext)} turns it into a number of ticks.
 */
public record TechniqueReading(NumberProvider learnTime, ItemUseAnimation holdAnimation, Holder<SoundEvent> holdSound) {
    // What a manual reads as while its technique is gone from the pack: the same values the codec falls back to.
    public static final TechniqueReading DEFAULTS = new TechniqueReading(new Constant(HoldBinding.NO_HOLD),
            HoldBinding.DEFAULT_HOLD_ANIMATION, HoldBinding.DEFAULT_HOLD_SOUND);

    public static TechniqueReading of(Holder<Technique> technique) {
        if (!technique.isBound()) return DEFAULTS;
        Technique value = technique.value();
        return new TechniqueReading(value.learnTime(), value.holdAnimation(), value.holdSound());
    }

    // The length in ticks: a non-finite result is reported by the provider and reads as no hold, a fractional one is
    // floored, and nothing is capped - a read may last as long as it likes.
    public int holdTicks(FormulaContext context) {
        double value = this.learnTime.evaluate(context);
        if (!this.learnTime.assertFinite(value) || value < 1.0D) return HoldBinding.NO_HOLD;
        return (int) Math.min(Math.floor(value), Integer.MAX_VALUE);
    }

    public boolean requiresHold(FormulaContext context) {
        return this.holdTicks(context) > HoldBinding.NO_HOLD;
    }
}
