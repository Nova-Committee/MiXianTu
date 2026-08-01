package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.AndBiEntityCondition;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

/**
 * Predicate with an actor and a candidate target.
 */
public interface BiEntityCondition {
    Codec<BiEntityCondition> SINGLE_CODEC = MxtTypeRegistries.BI_ENTITY_CONDITION_TYPE.byNameCodec().dispatch("type", BiEntityCondition::codec, Function.identity());
    Codec<BiEntityCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndBiEntityCondition::new),
            condition -> condition instanceof AndBiEntityCondition(
                    List<BiEntityCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(Entity actor, Entity target, FormulaContext context);

    default boolean test(Entity actor, Entity target) {
        return this.test(actor, target, FormulaContext.EMPTY);
    }

    MapCodec<? extends BiEntityCondition> codec();
}
