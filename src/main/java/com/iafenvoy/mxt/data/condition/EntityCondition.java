package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AndEntityCondition;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

/**
 * Java-owned entity predicate selected by a datapack {@code type} object.
 */
public interface EntityCondition {
    Codec<EntityCondition> SINGLE_CODEC = MxtTypeRegistries.ENTITY_CONDITION_TYPE.byNameCodec().dispatch("type", EntityCondition::codec, Function.identity());
    Codec<EntityCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndEntityCondition::new),
            condition -> condition instanceof AndEntityCondition(
                    List<EntityCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(Entity entity, FormulaContext context);

    MapCodec<? extends EntityCondition> codec();
}
