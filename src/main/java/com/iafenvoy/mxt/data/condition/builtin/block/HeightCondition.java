package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record HeightCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<HeightCondition> CODEC = Comparison.CODEC.xmap(HeightCondition::new, HeightCondition::comparison);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        return this.comparison.compare(pos.getY());
    }

    @Override
    public @NonNull MapCodec<HeightCondition> codec() {
        return CODEC;
    }
}
