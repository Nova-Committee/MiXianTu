package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record RidingCondition(BiEntityCondition biEntityCondition) implements EntityCondition {
    public static final MapCodec<RidingCondition> CODEC = BiEntityCondition.optionalCodec("bientity_condition").xmap(RidingCondition::new, RidingCondition::biEntityCondition);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity vehicle = ctx.entity().getVehicle();
        return vehicle != null && this.biEntityCondition.test(ctx.entity(), vehicle, ctx);
    }

    @Override
    public @NonNull MapCodec<RidingCondition> codec() {
        return CODEC;
    }
}
