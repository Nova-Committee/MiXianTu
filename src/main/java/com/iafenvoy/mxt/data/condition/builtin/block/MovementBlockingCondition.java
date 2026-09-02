package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

public enum MovementBlockingCondition implements BlockCondition {
    INSTANCE;
    public static final MapCodec<MovementBlockingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        BlockState state = level.getBlockState(pos);
        return state.blocksMotion() && !state.getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public @NonNull MapCodec<MovementBlockingCondition> codec() {
        return CODEC;
    }
}
