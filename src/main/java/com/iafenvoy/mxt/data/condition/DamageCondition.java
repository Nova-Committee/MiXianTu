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

import org.jetbrains.annotations.NotNull;

public interface DamageCondition {
    Codec<DamageCondition> SINGLE_CODEC = MxtRegistries.DAMAGE_CONDITION_TYPE.byNameCodec().dispatch("type", DamageCondition::codec, Function.identity());
    Codec<DamageCondition> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(AndDamageCondition::new, Function.identity()), Either::right);

    static MapCodec<DamageCondition> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, AlwaysTrueCondition.INSTANCE);
    }

    @NotNull MapCodec<? extends DamageCondition> codec();

    boolean test(@NotNull DamageConditionContext context);

    default boolean test(DamageSource source, float amount, Context parent) {
        return this.test(parent.copyTo(new DamageConditionContext(source, amount, parent.formula())));
    }

    default boolean test(DamageSource source, float amount, FormulaContext formula) {
        return this.test(new DamageConditionContext(source, amount, formula));
    }
}
