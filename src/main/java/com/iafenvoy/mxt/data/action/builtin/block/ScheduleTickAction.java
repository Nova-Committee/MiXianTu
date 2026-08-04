package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record ScheduleTickAction(int delay) implements BlockAction {
    public static final MapCodec<ScheduleTickAction> CODEC = Codec.INT.fieldOf("delay").xmap(ScheduleTickAction::new, ScheduleTickAction::delay);

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (!level.isClientSide() && level.hasChunkAt(pos))
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), this.delay);
    }

    @Override
    public MapCodec<ScheduleTickAction> codec() {
        return CODEC;
    }
}
