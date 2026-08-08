package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record OrCondition(List<BlockCondition> conditions) implements BlockCondition {
    public static final MapCodec<OrCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrCondition::new, OrCondition::conditions);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return this.conditions.stream().anyMatch(condition -> condition.test(level, pos, context));
    }

    @Override
    public MapCodec<OrCondition> codec() {
        return CODEC;
    }
}
