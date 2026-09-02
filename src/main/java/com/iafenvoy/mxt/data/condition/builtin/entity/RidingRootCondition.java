package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Tests the vehicle at the root of this entity's riding chain.
 */
public record RidingRootCondition(BiEntityCondition bientityCondition) implements EntityCondition {
    public static final MapCodec<RidingRootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(RidingRootCondition::bientityCondition)
    ).apply(i, RidingRootCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.bientityCondition.test(entity, entity.getRootVehicle(), ctx);
    }

    @Override
    public @NonNull MapCodec<RidingRootCondition> codec() {
        return CODEC;
    }
}
