package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record ScheduleTickAction(int delay) implements BlockAction {
    public static final MapCodec<ScheduleTickAction> CODEC = Codec.INT.fieldOf("delay").xmap(ScheduleTickAction::new, ScheduleTickAction::delay);

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (!level.isClientSide() && level.hasChunkAt(pos))
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), this.delay);
    }

    @Override
    public @NonNull MapCodec<ScheduleTickAction> codec() {
        return CODEC;
    }
}
