package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Applies a nested action at a relative block offset.
 */
public record OffsetAction(BlockAction action, int x, int y, int z) implements BlockAction {
    public static final MapCodec<OffsetAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.CODEC.fieldOf("action").forGetter(OffsetAction::action),
            Codec.INT.optionalFieldOf("x", 0).forGetter(OffsetAction::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(OffsetAction::y),
            Codec.INT.optionalFieldOf("z", 0).forGetter(OffsetAction::z)
    ).apply(i, OffsetAction::new));

    @Override
    public void execute(BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        this.action.execute(level, pos.offset(this.x, this.y, this.z), ctx);
    }

    @Override
    public MapCodec<OffsetAction> codec() {
        return CODEC;
    }
}
