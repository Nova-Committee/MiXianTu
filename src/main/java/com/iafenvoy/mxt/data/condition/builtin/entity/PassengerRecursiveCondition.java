package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Counts all nested passengers that satisfy a bi-entity condition against this entity.
 */
public record PassengerRecursiveCondition(BiEntityCondition bientityCondition,
                                          Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PassengerRecursiveCondition::bientityCondition),
            Comparison.CODEC.forGetter(PassengerRecursiveCondition::comparison)
    ).apply(i, PassengerRecursiveCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        long matches = ctx.entity().getPassengers().stream()
                .flatMap(Entity::getPassengersAndSelf)
                .filter(passenger -> this.bientityCondition.test(passenger, ctx.entity(), ctx))
                .count();
        return this.comparison.compare(matches);
    }

    @Override
    public @NonNull MapCodec<PassengerRecursiveCondition> codec() {
        return CODEC;
    }
}
