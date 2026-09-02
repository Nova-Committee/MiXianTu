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

public record PassengerCondition(BiEntityCondition biEntityCondition,
                                 Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PassengerCondition::biEntityCondition),
            Comparison.CODEC.forGetter(PassengerCondition::comparison)
    ).apply(i, PassengerCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        long matches = entity.getPassengers().stream().filter(passenger -> this.biEntityCondition.test(passenger, entity, ctx)).count();
        return this.comparison.compare(matches);
    }

    @Override
    public @NonNull MapCodec<PassengerCondition> codec() {
        return CODEC;
    }
}
