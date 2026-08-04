package com.iafenvoy.mxt.data.condition.builtin.entity;

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
    public static final MapCodec<PassengerRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition", new ConstantCondition(true)).forGetter(PassengerRecursiveCondition::bientityCondition),
            Comparison.CODEC.forGetter(PassengerRecursiveCondition::comparison)
    ).apply(instance, PassengerRecursiveCondition::new));

    @Override
    public boolean test(Entity entity) {
        long matches = entity.getPassengers().stream()
                .flatMap(Entity::getPassengersAndSelf)
                .filter(passenger -> this.bientityCondition.test(passenger, entity, FormulaContext.EMPTY))
                .count();
        return this.comparison.compare(matches);
    }

    @Override
    public MapCodec<PassengerRecursiveCondition> codec() {
        return CODEC;
    }
}
