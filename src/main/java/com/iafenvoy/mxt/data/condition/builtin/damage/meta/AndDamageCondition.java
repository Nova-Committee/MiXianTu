package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Requires every nested damage condition to pass.
 */
public record AndDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<AndDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndDamageCondition::new, AndDamageCondition::conditions);

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        float amount = ctx.amount();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().allMatch(condition -> condition.test(source, amount, ctx));
    }

    @Override
    public @NonNull MapCodec<AndDamageCondition> codec() {
        return CODEC;
    }
}
