package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record RidingRecursiveCondition(BiEntityCondition biEntityCondition,
                                       Comparison comparison) implements EntityCondition {
    public static final MapCodec<RidingRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(RidingRecursiveCondition::biEntityCondition),
            Comparison.CODEC.forGetter(RidingRecursiveCondition::comparison)
    ).apply(i, RidingRecursiveCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        int matches = 0;
        for (Entity vehicle = entity.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
            boolean matchesCondition = this.biEntityCondition.test(entity, vehicle, ctx);
            if (matchesCondition) matches++;
        }
        return this.comparison.compare(matches);
    }

    @Override
    public @NonNull MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
