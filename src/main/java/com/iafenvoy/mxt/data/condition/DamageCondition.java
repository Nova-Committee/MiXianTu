package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.data.condition.builtin.damage.meta.AndDamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.damagesource.DamageSource;

import java.util.function.Function;
import java.util.List;

/**
 * Code-owned predicate over an incoming damage event.
 */
public interface DamageCondition {
    Codec<DamageCondition> SINGLE_CODEC = MxtTypeRegistries.DAMAGE_CONDITION_TYPE.byNameCodec().dispatch("type", DamageCondition::codec, Function.identity());
    Codec<DamageCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndDamageCondition::new),
            condition -> condition instanceof AndDamageCondition(
                    List<DamageCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(DamageSource source, float amount, FormulaContext context);

    default boolean test(DamageSource source, float amount) {
        return this.test(source, amount, FormulaContext.EMPTY);
    }

    MapCodec<? extends DamageCondition> codec();
}
