package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

import java.util.function.Function;

/**
 * Code-owned predicate over an incoming damage event.
 */
public interface DamageCondition {
    Codec<DamageCondition> CODEC = MxtTypeRegistries.DAMAGE_CONDITION_TYPE.byNameCodec().dispatch("type", DamageCondition::codec, Function.identity());

    boolean test(DamageSource source, float amount, FormulaContext context);

    default boolean test(DamageSource source, float amount) {
        return this.test(source, amount, FormulaContext.EMPTY);
    }

    MapCodec<? extends DamageCondition> codec();
}
