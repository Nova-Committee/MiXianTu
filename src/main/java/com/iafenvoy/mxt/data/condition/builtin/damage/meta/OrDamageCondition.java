package com.iafenvoy.mxt.data.condition.builtin.damage.meta;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Passes when at least one nested damage condition passes.
 */
public record OrDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<OrDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrDamageCondition::new, OrDamageCondition::conditions);

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        return this.conditions.stream().anyMatch(condition -> condition.test(ctx.source(), ctx.amount(), ctx));
    }

    @Override
    public @NonNull MapCodec<OrDamageCondition> codec() {
        return CODEC;
    }
}
