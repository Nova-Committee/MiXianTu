package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

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
    public void execute(@NonNull BlockActionContext ctx) {
        this.action.execute(ctx.level(), ctx.pos().offset(this.x, this.y, this.z), ctx);
    }

    @Override
    public @NonNull MapCodec<OffsetAction> codec() {
        return CODEC;
    }
}
