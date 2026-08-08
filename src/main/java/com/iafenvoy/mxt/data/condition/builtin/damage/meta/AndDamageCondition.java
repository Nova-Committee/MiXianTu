package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;

/**
 * Requires every nested damage condition to pass.
 */
public record AndDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<AndDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndDamageCondition::new, AndDamageCondition::conditions);

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        return this.conditions.stream().allMatch(condition -> condition.test(source, amount, context));
    }

    @Override
    public MapCodec<AndDamageCondition> codec() {
        return CODEC;
    }
}
