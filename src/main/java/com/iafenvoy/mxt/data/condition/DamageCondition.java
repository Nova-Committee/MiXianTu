package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.data.condition.builtin.damage.meta.AndDamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;

import java.util.function.Function;
import java.util.List;

/**
 * Code-owned predicate over an incoming damage event.
 */
public interface DamageCondition {
    Codec<DamageCondition> SINGLE_CODEC = MxtRegistries.DAMAGE_CONDITION_TYPE.byNameCodec().dispatch("type", DamageCondition::codec, Function.identity());
    Codec<DamageCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndDamageCondition::new),
            condition -> condition instanceof AndDamageCondition(
                    List<DamageCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(DamageConditionContext context);

    default boolean test(DamageSource source, float amount, Context parent) {
        return this.test(parent.copyTo(new DamageConditionContext(source, amount, parent.formula())));
    }

    default boolean test(DamageSource source, float amount, FormulaContext formula) {
        return this.test(new DamageConditionContext(source, amount, formula));
    }

    MapCodec<? extends DamageCondition> codec();
}
