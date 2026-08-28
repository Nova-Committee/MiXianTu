package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AndEntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public interface EntityCondition {
    Codec<EntityCondition> SINGLE_CODEC = MxtRegistries.ENTITY_CONDITION_TYPE.byNameCodec().dispatch("type", EntityCondition::codec, Function.identity());
    Codec<EntityCondition> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(AndEntityCondition::new, Function.identity()), Either::right);

    static MapCodec<EntityCondition> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, AlwaysTrueCondition.INSTANCE);
    }

    @NotNull MapCodec<? extends EntityCondition> codec();

    boolean test(@NotNull EntityConditionContext context);

    default boolean test(Entity entity, Context parent) {
        return this.test(parent.copyTo(new EntityConditionContext(entity, parent.formula())));
    }

    default boolean test(Entity entity, FormulaContext formula) {
        return this.test(new EntityConditionContext(entity, formula));
    }
}
