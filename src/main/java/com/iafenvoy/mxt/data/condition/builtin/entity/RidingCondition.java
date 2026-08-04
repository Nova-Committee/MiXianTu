package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record RidingCondition(Optional<BiEntityCondition> biEntityCondition) implements EntityCondition {
    public static final MapCodec<RidingCondition> CODEC = BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").xmap(RidingCondition::new, RidingCondition::biEntityCondition);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        Entity vehicle = entity.getVehicle();
        return vehicle != null && this.biEntityCondition.map(condition -> condition.test(entity, vehicle, context)).orElse(true);
    }

    @Override
    public boolean test(Entity entity) {
        return this.test(entity, FormulaContext.EMPTY);
    }

    @Override
    public MapCodec<RidingCondition> codec() {
        return CODEC;
    }
}
