package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record AndBlockCondition(List<BlockCondition> conditions) implements BlockCondition {
    public static final MapCodec<AndBlockCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBlockCondition::new, AndBlockCondition::conditions);

    @Override
    public boolean test(BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().allMatch(condition -> condition.test(level, pos, ctx));
    }

    @Override
    public MapCodec<AndBlockCondition> codec() {
        return CODEC;
    }
}
