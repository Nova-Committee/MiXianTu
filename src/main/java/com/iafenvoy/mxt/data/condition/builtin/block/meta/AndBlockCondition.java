package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record AndBlockCondition(List<BlockCondition> conditions) implements BlockCondition {
    public static final MapCodec<AndBlockCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBlockCondition::new, AndBlockCondition::conditions);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return this.conditions.stream().allMatch(condition -> condition.test(level, pos, context));
    }

    @Override
    public MapCodec<AndBlockCondition> codec() {
        return CODEC;
    }
}
