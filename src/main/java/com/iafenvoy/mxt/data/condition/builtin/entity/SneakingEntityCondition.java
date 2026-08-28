package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public enum SneakingEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<SneakingEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity.isShiftKeyDown();
    }

    @Override
    public @NonNull MapCodec<SneakingEntityCondition> codec() {
        return CODEC;
    }
}
