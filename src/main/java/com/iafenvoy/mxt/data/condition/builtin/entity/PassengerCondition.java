package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record PassengerCondition(Optional<BiEntityCondition> biEntityCondition,
                                 Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(PassengerCondition::biEntityCondition),
            Comparison.CODEC.forGetter(PassengerCondition::comparison)
    ).apply(i, PassengerCondition::new));

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        long matches = entity.getPassengers().stream().filter(passenger -> this.biEntityCondition.map(condition -> condition.test(passenger, entity, ctx)).orElse(true)).count();
        return this.comparison.compare(matches);
    }

    @Override
    public MapCodec<PassengerCondition> codec() {
        return CODEC;
    }
}
