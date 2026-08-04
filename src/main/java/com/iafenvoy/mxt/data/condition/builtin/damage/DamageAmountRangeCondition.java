package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;

public record DamageAmountRangeCondition(NumberProvider min, NumberProvider max) implements DamageCondition {
    public static final MapCodec<DamageAmountRangeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("min").forGetter(DamageAmountRangeCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(DamageAmountRangeCondition::max)
    ).apply(instance, DamageAmountRangeCondition::new));

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && amount >= min && amount <= max;
    }

    @Override
    public MapCodec<DamageAmountRangeCondition> codec() {
        return CODEC;
    }
}
