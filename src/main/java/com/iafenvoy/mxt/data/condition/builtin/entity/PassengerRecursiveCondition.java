package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.ConstantCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Counts all nested passengers that satisfy a bi-entity condition against this entity.
 */
public record PassengerRecursiveCondition(BiEntityCondition bientityCondition,
                                          Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition", new ConstantCondition(true)).forGetter(PassengerRecursiveCondition::bientityCondition),
            Comparison.CODEC.forGetter(PassengerRecursiveCondition::comparison)
    ).apply(i, PassengerRecursiveCondition::new));

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        long matches = entity.getPassengers().stream()
                .flatMap(Entity::getPassengersAndSelf)
                .filter(passenger -> this.bientityCondition.test(passenger, entity, ctx))
                .count();
        return this.comparison.compare(matches);
    }

    @Override
    public MapCodec<PassengerRecursiveCondition> codec() {
        return CODEC;
    }
}
