package com.iafenvoy.mxt.data.condition.builtin.entity;

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
    public static final MapCodec<PassengerCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(PassengerCondition::biEntityCondition),
            Comparison.CODEC.forGetter(PassengerCondition::comparison)
    ).apply(instance, PassengerCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        long matches = entity.getPassengers().stream().filter(passenger -> this.biEntityCondition.map(condition -> condition.test(passenger, entity, context)).orElse(true)).count();
        return this.comparison.compare(matches);
    }

    @Override
    public boolean test(Entity entity) {
        return this.test(entity, FormulaContext.EMPTY);
    }

    @Override
    public MapCodec<PassengerCondition> codec() {
        return CODEC;
    }
}
