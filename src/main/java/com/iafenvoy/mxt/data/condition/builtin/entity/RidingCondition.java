package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record RidingCondition(Optional<BiEntityCondition> biEntityCondition) implements EntityCondition {
    public static final MapCodec<RidingCondition> CODEC = BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").xmap(RidingCondition::new, RidingCondition::biEntityCondition);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        Entity vehicle = entity.getVehicle();
        return vehicle != null && this.biEntityCondition.map(condition -> condition.test(entity, vehicle, ctx)).orElse(true);
    }

    @Override
    public MapCodec<RidingCondition> codec() {
        return CODEC;
    }
}
