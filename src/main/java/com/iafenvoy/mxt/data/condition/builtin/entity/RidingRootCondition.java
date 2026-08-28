package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.ConstantCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Tests the vehicle at the root of this entity's riding chain.
 */
public record RidingRootCondition(BiEntityCondition bientityCondition) implements EntityCondition {
    public static final MapCodec<RidingRootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition", new ConstantCondition(true)).forGetter(RidingRootCondition::bientityCondition)
    ).apply(i, RidingRootCondition::new));

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.bientityCondition.test(entity, entity.getRootVehicle(), ctx);
    }

    @Override
    public MapCodec<RidingRootCondition> codec() {
        return CODEC;
    }
}
