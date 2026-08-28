package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.AndBiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public interface BiEntityCondition {
    Codec<BiEntityCondition> SINGLE_CODEC = MxtRegistries.BI_ENTITY_CONDITION_TYPE.byNameCodec().dispatch("type", BiEntityCondition::codec, Function.identity());
    Codec<BiEntityCondition> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(AndBiEntityCondition::new, Function.identity()), Either::right);

    static MapCodec<BiEntityCondition> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, AlwaysTrueCondition.INSTANCE);
    }

    @NotNull MapCodec<? extends BiEntityCondition> codec();

    boolean test(@NotNull BiEntityConditionContext context);

    default boolean test(Entity actor, Entity target, Context parent) {
        return this.test(parent.copyTo(new BiEntityConditionContext(actor, target, parent.formula())));
    }

    default boolean test(Entity actor, Entity target, FormulaContext formula) {
        return this.test(new BiEntityConditionContext(actor, target, formula));
    }
}
