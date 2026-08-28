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

import java.util.List;
import java.util.function.Function;

/**
 * Predicate with an actor and a candidate target.
 */
public interface BiEntityCondition {
    Codec<BiEntityCondition> SINGLE_CODEC = MxtRegistries.BI_ENTITY_CONDITION_TYPE.byNameCodec().dispatch("type", BiEntityCondition::codec, Function.identity());
    Codec<BiEntityCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndBiEntityCondition::new),
            condition -> condition instanceof AndBiEntityCondition(
                    List<BiEntityCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(BiEntityConditionContext context);

    default boolean test(Entity actor, Entity target, Context parent) {
        return this.test(parent.copyTo(new BiEntityConditionContext(actor, target, parent.formula())));
    }

    default boolean test(Entity actor, Entity target, FormulaContext formula) {
        return this.test(new BiEntityConditionContext(actor, target, formula));
    }

    MapCodec<? extends BiEntityCondition> codec();
}
