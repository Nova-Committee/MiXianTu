package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Negates a nested damage condition.
 */
public record NotDamageCondition(DamageCondition condition) implements DamageCondition {
    public static final MapCodec<NotDamageCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DamageCondition.CODEC.fieldOf("condition").forGetter(NotDamageCondition::condition)
    ).apply(instance, NotDamageCondition::new));

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        return !this.condition.test(source, amount, context);
    }

    @Override
    public MapCodec<NotDamageCondition> codec() {
        return CODEC;
    }
}
