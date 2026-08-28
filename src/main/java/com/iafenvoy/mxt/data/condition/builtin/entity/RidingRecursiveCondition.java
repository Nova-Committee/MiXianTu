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

public record RidingRecursiveCondition(Optional<BiEntityCondition> biEntityCondition,
                                       Comparison comparison) implements EntityCondition {
    public static final MapCodec<RidingRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(RidingRecursiveCondition::biEntityCondition),
            Comparison.CODEC.forGetter(RidingRecursiveCondition::comparison)
    ).apply(i, RidingRecursiveCondition::new));

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        int matches = 0;
        for (Entity vehicle = entity.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
            boolean matchesCondition = this.biEntityCondition.isEmpty() || this.biEntityCondition.get().test(entity, vehicle, ctx);
            if (matchesCondition) matches++;
        }
        return this.comparison.compare(matches);
    }

    @Override
    public MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
