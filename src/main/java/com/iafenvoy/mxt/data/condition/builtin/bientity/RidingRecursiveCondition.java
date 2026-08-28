package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum RidingRecursiveCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<RidingRecursiveCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        for (Entity vehicle = actor.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle())
            if (vehicle == target) return true;
        return false;
    }

    @Override
    public MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
